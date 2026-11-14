package app.peertube.tv

import android.text.InputType
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PeerTubeAccountSettingsPanel(model: HomeModel, onUseServer: (String) -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { PeerTubeAccounts.get(context) }
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(model.server) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var showOtp by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var focusedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(manager) { runCatching { manager.initialize() }.onFailure { error = accountMessage(context, it) } }
    fun launchAction(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                error = accountMessage(context, failure)
                if (failure is AccountAuthFailure && failure.reason == "two_factor") { showOtp = true; otp = "" }
            } finally { busy = false }
        }
    }

    if (busy || !state.ready) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (state.storageError) {
        Text(stringResource(R.string.account_storage_error), color = MaterialTheme.colorScheme.error)
        OutlinedButton(enabled = !busy, onClick = { launchAction { manager.reset() } }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_reset)) }
    }
    state.accounts.forEach { account ->
        val selected = account.id == state.activeId
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (focusedId == account.id) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (selected) 3.dp else 0.dp
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!account.avatar.isNullOrBlank()) AsyncImage(account.avatar, account.displayName, Modifier.size(42.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentScale = ContentScale.Crop)
                    else Icon(Icons.Filled.AccountCircle, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text("${account.username} · ${runCatching { java.net.URI(account.server).host }.getOrNull() ?: account.server}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        Text(stringResource(when { account.needsLogin -> R.string.account_expired; selected -> R.string.account_active; else -> R.string.account_saved }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = {
                        if (account.needsLogin) { server = account.server; username = account.username; password = ""; otp = ""; showOtp = false; showForm = true }
                        else launchAction { manager.activate(account.id); manager.refreshProfile(account.id); onUseServer(account.server) }
                    }, modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) focusedId = account.id }) {
                        Text(stringResource(if (account.needsLogin) R.string.account_sign_in_again else R.string.account_use))
                    }
                    OutlinedButton(enabled = !busy, onClick = { launchAction { manager.logout(account.id) } }, modifier = Modifier.onFocusChanged { if (it.isFocused) focusedId = account.id }) {
                        Text(stringResource(R.string.account_sign_out))
                    }
                }
            }
        }
    }
    if (!showForm) {
        Button(enabled = !busy && !state.storageError, onClick = { server = model.server; username = ""; password = ""; otp = ""; showOtp = false; showForm = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (state.accounts.isEmpty()) R.string.account_sign_in else R.string.account_add))
        }
    } else {
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text(stringResource(R.string.account_sign_in), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(server, { server = it; error = null }, enabled = !busy, singleLine = true,
            label = { Text(stringResource(R.string.account_server)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(username, { username = it; error = null }, enabled = !busy, singleLine = true,
            label = { Text(stringResource(R.string.account_username)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it; error = null }, enabled = !busy, singleLine = true,
            label = { Text(stringResource(R.string.account_password)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null) } }, modifier = Modifier.fillMaxWidth())
        if (showOtp) OutlinedTextField(otp, { otp = it; error = null }, enabled = !busy, singleLine = true,
            label = { Text(stringResource(R.string.account_otp)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        else TextButton(enabled = !busy, onClick = { showOtp = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.account_otp_prompt)) }
        Button(enabled = !busy && server.isNotBlank() && username.isNotBlank() && password.isNotEmpty(), onClick = {
            launchAction {
                val account = manager.login(server, username, password, otp)
                password = ""; otp = ""; showForm = false; showOtp = false
                onUseServer(account.server)
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(if (busy) R.string.account_signing_in else R.string.account_sign_in)) }
        TextButton(enabled = !busy, onClick = { showForm = false; password = ""; otp = "" }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Text(stringResource(R.string.account_security_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun accountMessage(context: android.content.Context, error: Throwable): String = context.getString(when ((error as? AccountAuthFailure)?.reason) {
    "invalid_server" -> R.string.account_error_server
    "not_peertube" -> R.string.account_error_not_peertube
    "invalid_credentials" -> R.string.account_error_credentials
    "two_factor" -> R.string.account_error_two_factor
    "session_expired" -> R.string.account_error_session
    "storage_read_error", "storage_key_missing", "storage_key_mismatch" -> R.string.account_storage_error
    "storage_write_error" -> R.string.account_error_storage
    "rate_limited" -> R.string.account_error_rate
    "forbidden" -> R.string.account_error_forbidden
    "redirect" -> R.string.account_error_redirect
    "invalid_response" -> R.string.account_error_response
    "account_limit" -> R.string.account_error_limit
    else -> R.string.account_error_network
})
