package app.peertube.tv

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.KeyStore

internal class AccountAuthFailure(val reason: String, val status: Int = 0) : java.io.IOException(reason)
internal data class AccountOAuthClient(val id: String, val secret: String) { override fun toString() = "AccountOAuthClient(redacted)" }
internal data class AccountOAuthTokens(val access: String, val refresh: String, val expiresIn: Long) { override fun toString() = "AccountOAuthTokens(redacted)" }
internal data class AccountProfile(val userId: Long, val username: String, val displayName: String, val avatar: String?)
internal data class AccountSession(val server: String, val client: AccountOAuthClient, val access: String, val refresh: String,
    val expiresAt: Long, val profile: AccountProfile, val needsLogin: Boolean = false) {
    val id get() = "$server|${profile.userId}"
    override fun toString() = "AccountSession(server=$server, credentials=redacted)"
}
internal data class StoredPeerTubeAccounts(val sessions: List<AccountSession> = emptyList(), val activeId: String? = null)
internal data class PeerTubeAccountSummary(val id: String, val server: String, val username: String, val displayName: String, val avatar: String?, val needsLogin: Boolean)
internal data class PeerTubeAccountsState(val ready: Boolean = false, val accounts: List<PeerTubeAccountSummary> = emptyList(), val activeId: String? = null, val storageError: Boolean = false) {
    val active get() = accounts.firstOrNull { it.id == activeId }
}

internal fun accountServerOrigin(input: String): String {
    val raw = input.trim().let { if (it.contains("://")) it else "https://$it" }
    val uri = runCatching { URI(raw) }.getOrNull() ?: throw AccountAuthFailure("invalid_server")
    if (!uri.scheme.equals("https", true) || uri.host.isNullOrBlank() || uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
        uri.path.orEmpty() !in listOf("", "/") || uri.port !in -1..65535 || uri.port == 0) throw AccountAuthFailure("invalid_server")
    return "https://${uri.host.lowercase(Locale.ROOT)}${if (uri.port > 0 && uri.port != 443) ":${uri.port}" else ""}"
}

private class PeerTubeAccountTransport(private val client: () -> OkHttpClient) {
    private val gson = Gson()
    private fun request(server: String, path: String, token: String? = null) = Request.Builder()
        .url(accountServerOrigin(server) + path).header("Accept", "application/json").apply { if (token != null) header("Authorization", "Bearer $token") }
    private suspend fun execute(request: Request, empty: Boolean = false): JsonObject = withContext(Dispatchers.IO) {
        client().newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty().take(1_048_577)
            if (text.length > 1_048_576) throw AccountAuthFailure("invalid_response")
            val json = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
            if (!response.isSuccessful) {
                val code = runCatching { json?.get("code")?.asString ?: json?.get("error")?.asString }.getOrNull().orEmpty()
                val reason = when {
                    code.contains("two_factor", true) || code.contains("two-factor", true) || code.contains("otp", true) -> "two_factor"
                    code in setOf("invalid_grant", "invalid_token", "invalid_client") -> "invalid_credentials"
                    response.code == 301 || response.code == 302 || response.code == 303 || response.code == 307 || response.code == 308 -> "redirect"
                    response.code == 401 -> "invalid_credentials"
                    response.code == 403 -> "forbidden"
                    response.code == 404 -> "not_peertube"
                    response.code == 429 -> "rate_limited"
                    response.code >= 500 -> "server_error"
                    else -> "request_failed"
                }
                throw AccountAuthFailure(reason, response.code)
            }
            if (empty) JsonObject() else json ?: throw AccountAuthFailure("invalid_response")
        }
    }
    private fun JsonObject.text(name: String) = runCatching { get(name)?.asString }.getOrNull().orEmpty()
    private fun tokens(json: JsonObject): AccountOAuthTokens {
        val access = json.text("access_token"); val refresh = json.text("refresh_token")
        val expires = runCatching { json.get("expires_in").asLong }.getOrDefault(0)
        if (access.isBlank() || refresh.isBlank() || expires !in 1..31_536_000 || json.text("token_type").lowercase(Locale.ROOT) != "bearer") throw AccountAuthFailure("invalid_response")
        return AccountOAuthTokens(access, refresh, expires)
    }
    suspend fun oauthClient(server: String): AccountOAuthClient {
        val json = execute(request(server, "/api/v1/oauth-clients/local").build())
        return AccountOAuthClient(json.text("client_id"), json.text("client_secret")).also {
            if (it.id.isBlank() || it.secret.isBlank()) throw AccountAuthFailure("not_peertube")
        }
    }
    suspend fun login(server: String, oauth: AccountOAuthClient, username: String, password: String, otp: String): AccountOAuthTokens {
        val body = FormBody.Builder().add("client_id", oauth.id).add("client_secret", oauth.secret).add("grant_type", "password")
            .add("response_type", "code").add("username", username).add("password", password).build()
        return tokens(execute(request(server, "/api/v1/users/token").apply { if (otp.isNotBlank()) header("x-peertube-otp", otp) }.post(body).build()))
    }
    suspend fun refresh(server: String, oauth: AccountOAuthClient, token: String): AccountOAuthTokens {
        val body = FormBody.Builder().add("client_id", oauth.id).add("client_secret", oauth.secret).add("grant_type", "refresh_token").add("refresh_token", token).build()
        return tokens(execute(request(server, "/api/v1/users/token").post(body).build()))
    }
    suspend fun me(server: String, token: String): AccountProfile {
        val json = execute(request(server, "/api/v1/users/me", token).build())
        val id = runCatching { json.get("id").asLong }.getOrDefault(0)
        val username = json.text("username")
        if (id <= 0 || username.isBlank()) throw AccountAuthFailure("invalid_response")
        val account = runCatching { json.getAsJsonObject("account") }.getOrNull()
        val display = account?.text("displayName")?.takeIf(String::isNotBlank) ?: username
        val avatar = runCatching { account?.getAsJsonArray("avatars")?.firstOrNull()?.asJsonObject?.let { it.text("fileUrl").ifBlank { it.text("path") } } }.getOrNull()
        val safeAvatar = avatar?.takeIf(String::isNotBlank)?.let { value -> runCatching {
            URI(accountServerOrigin(server) + "/").resolve(value).takeIf { it.scheme == "https" && it.userInfo == null && it.host != null }?.toString()
        }.getOrNull() }
        return AccountProfile(id, username, display, safeAvatar)
    }
    suspend fun revoke(server: String, token: String) { execute(request(server, "/api/v1/users/revoke-token", token).post(FormBody.Builder().build()).build(), true) }
}

private class EncryptedPeerTubeAccountStore(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "peertube-account-sessions.bin"))
    private val gson = Gson()
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("peertubetv.account.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("peertubetv.account.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    suspend fun load(): StoredPeerTubeAccounts = withContext(Dispatchers.IO) {
        val secret = key()
        val input = try { file.openRead() } catch (error: java.io.FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists() || File(file.baseFile.path + ".new").exists()) throw error
            return@withContext StoredPeerTubeAccounts()
        }
        val bytes = input.use { it.readBytes().also { require(it.size in 29..1_048_576) } }
        require(bytes[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        val plain = cipher.doFinal(bytes, 13, bytes.size - 13)
        try { gson.fromJson(plain.toString(Charsets.UTF_8), StoredPeerTubeAccounts::class.java) ?: throw AccountAuthFailure("storage_error") }
        finally { plain.fill(0) }
    }
    suspend fun save(data: StoredPeerTubeAccounts) = withContext(Dispatchers.IO) {
        val plain = gson.toJson(data).toByteArray(Charsets.UTF_8)
        val encrypted = try { val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key()); byteArrayOf(1) + cipher.iv + cipher.doFinal(plain) } finally { plain.fill(0) }
        val stream = file.startWrite()
        try { stream.write(encrypted); file.finishWrite(stream) } catch (error: Exception) { file.failWrite(stream); throw error }
    }
    suspend fun clear() = withContext(Dispatchers.IO) {
        file.delete()
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("peertubetv.account.v1") }
    }
}

internal class PeerTubeAccountManager(context: Context) {
    private val store = EncryptedPeerTubeAccountStore(context.applicationContext)
    @Volatile private var api = PeerTubeAccountTransport { ApiFactory.authHttpClient() }
    private val mutex = Mutex()
    private var saved = StoredPeerTubeAccounts()
    private var loaded = false
    private val mutable = MutableStateFlow(PeerTubeAccountsState())
    val state = mutable.asStateFlow()
    private fun publish(error: Boolean = false) { mutable.value = PeerTubeAccountsState(true, saved.sessions.map { PeerTubeAccountSummary(it.id, it.server, it.profile.username, it.profile.displayName, it.profile.avatar, it.needsLogin) }, saved.activeId, error) }
    private suspend fun initializeLocked() {
        if (loaded) return
        try {
            val data = store.load()
            require(data.sessions.size <= 20 && data.sessions.map { it.id }.distinct().size == data.sessions.size)
            data.sessions.forEach { require(accountServerOrigin(it.server) == it.server && it.profile.userId > 0 && it.profile.username.isNotBlank()) }
            saved = data.copy(activeId = data.activeId?.takeIf { id -> data.sessions.any { it.id == id } } ?: data.sessions.firstOrNull()?.id)
            loaded = true; publish()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { publish(true); throw if (error is AccountAuthFailure) error else AccountAuthFailure("storage_error") }
    }
    suspend fun initialize() = mutex.withLock { initializeLocked() }
    private suspend fun commit(next: StoredPeerTubeAccounts) {
        withContext(NonCancellable) { try { store.save(next) } catch (_: Exception) { throw AccountAuthFailure("storage_error") }; saved = next; publish() }
    }
    private fun expiry(tokens: AccountOAuthTokens): Long {
        if (tokens.access.isBlank() || tokens.refresh.isBlank() || tokens.expiresIn !in 1..31_536_000) throw AccountAuthFailure("invalid_response")
        return System.currentTimeMillis() + tokens.expiresIn * 1000
    }
    suspend fun login(serverInput: String, usernameInput: String, password: String, otp: String): PeerTubeAccountSummary = mutex.withLock {
        initializeLocked()
        val server = accountServerOrigin(serverInput); val username = usernameInput.trim()
        if (username.isBlank() || password.isEmpty()) throw AccountAuthFailure("missing_credentials")
        val oauth = api.oauthClient(server)
        val tokens = api.login(server, oauth, username, password, otp.trim())
        try {
            val profile = api.me(server, tokens.access)
            val session = AccountSession(server, oauth, tokens.access, tokens.refresh, expiry(tokens), profile)
            if (saved.sessions.size >= 20 && saved.sessions.none { it.id == session.id }) throw AccountAuthFailure("account_limit")
            commit(StoredPeerTubeAccounts(saved.sessions.filterNot { it.id == session.id } + session, session.id))
            state.value.active!!
        } catch (error: Exception) {
            if (saved.sessions.none { it.server == server && it.access == tokens.access }) withContext(NonCancellable) { withTimeoutOrNull(3000) { runCatching { api.revoke(server, tokens.access) } } }
            throw error
        }
    }
    suspend fun activate(id: String) = mutex.withLock { initialize(); if (saved.sessions.none { it.id == id }) throw AccountAuthFailure("session_expired"); commit(saved.copy(activeId = id)) }
    suspend fun refreshProfile(id: String): PeerTubeAccountSummary = mutex.withLock {
        initializeLocked()
        val old = saved.sessions.firstOrNull { it.id == id } ?: throw AccountAuthFailure("session_expired")
        if (old.needsLogin) throw AccountAuthFailure("session_expired")
        var session = old
        if (session.expiresAt <= System.currentTimeMillis() + 60_000) session = refresh(session)
        val profile = try { api.me(session.server, session.access) } catch (error: AccountAuthFailure) {
            if (error.status != 401) throw error
            session = refresh(session); api.me(session.server, session.access)
        }
        if (profile.userId != session.profile.userId) throw AccountAuthFailure("invalid_response")
        commit(saved.copy(sessions = saved.sessions.map { if (it.id == id) session.copy(profile = profile) else it }))
        state.value.accounts.first { it.id == id }
    }
    private suspend fun refresh(session: AccountSession): AccountSession {
        val tokens = try { api.refresh(session.server, session.client, session.refresh) } catch (error: AccountAuthFailure) {
            if (error.status == 401 || error.reason == "invalid_credentials") {
                val expired = session.copy(access = "", refresh = "", needsLogin = true)
                commit(saved.copy(sessions = saved.sessions.map { if (it.id == session.id) expired else it }))
                throw AccountAuthFailure("session_expired")
            }; throw error
        }
        val next = session.copy(access = tokens.access, refresh = tokens.refresh, expiresAt = expiry(tokens))
        commit(saved.copy(sessions = saved.sessions.map { if (it.id == session.id) next else it }))
        return next
    }
    suspend fun logout(id: String) {
        val session = mutex.withLock {
            initializeLocked(); val found = saved.sessions.firstOrNull { it.id == id } ?: return
            val remaining = saved.sessions.filterNot { it.id == id }
            commit(StoredPeerTubeAccounts(remaining, if (saved.activeId == id) remaining.firstOrNull()?.id else saved.activeId)); found
        }
        if (!session.needsLogin && session.access.isNotBlank()) withContext(NonCancellable) { withTimeoutOrNull(4000) { runCatching { api.revoke(session.server, session.access) } } }
    }
    suspend fun reset() = mutex.withLock { store.clear(); saved = StoredPeerTubeAccounts(); loaded = true; publish() }
}

internal object PeerTubeAccounts {
    @Volatile private var instance: PeerTubeAccountManager? = null
    fun get(context: Context) = instance ?: synchronized(this) { instance ?: PeerTubeAccountManager(context).also { instance = it } }
}
