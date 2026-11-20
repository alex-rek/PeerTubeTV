package app.peertube.tv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChannelNavigationTarget(val name: String, val host: String)

class MainActivity : AppCompatActivity() {
    internal var pendingChannelNavigation by mutableStateOf<ChannelNavigationTarget?>(null)

    private var availableUpdate by mutableStateOf<GitHubRelease?>(null)
    private var updateMessage by mutableStateOf<String?>(null)
    private var updateBusy by mutableStateOf(false)
    private var pendingUpdateApk: java.io.File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingChannelNavigation = channelNavigationFrom(intent)
        setContent {
            val model: HomeModel = viewModel()
            val theme = PeerIcheThemes.fromId(model.selectedTheme)
            MaterialTheme(colorScheme = theme.colors) {
                CompositionLocalProvider(LocalPeerIcheTheme provides theme) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Home(model)
                    }
                    availableUpdate?.takeIf { updateMessage == null }?.let { release ->
                        AlertDialog(
                            onDismissRequest = { if (!updateBusy) availableUpdate = null },
                            title = { Text(stringResource(R.string.update_available_title, release.version)) },
                            text = { Text(release.notes.ifBlank { stringResource(R.string.update_available_message) }) },
                            confirmButton = {
                                TextButton(enabled = !updateBusy, onClick = { downloadAndInstall(release) }) {
                                    Text(if (updateBusy) stringResource(R.string.update_downloading) else stringResource(R.string.update_action))
                                }
                            },
                            dismissButton = {
                                TextButton(enabled = !updateBusy, onClick = { availableUpdate = null }) { Text(stringResource(R.string.update_later)) }
                            }
                        )
                    }
                    updateMessage?.let { message ->
                        AlertDialog(
                            onDismissRequest = { updateMessage = null },
                            title = { Text(stringResource(R.string.update_error_title)) },
                            text = { Text(message) },
                            confirmButton = { TextButton(onClick = { updateMessage = null }) { Text(stringResource(R.string.ok)) } }
                        )
                    }
                }
            }
        }
        lifecycleScope.launch {
            availableUpdate = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    GitHubUpdates.latestRelease(this@MainActivity, BuildConfig.VERSION_NAME)
                }.getOrNull()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingChannelNavigation = channelNavigationFrom(intent)
    }

    private fun channelNavigationFrom(intent: Intent): ChannelNavigationTarget? {
        if (!intent.getBooleanExtra("navigateChannelVideos", false)) return null
        val name = intent.getStringExtra("navigateChannelName")?.trim()?.takeIf(String::isNotBlank) ?: return null
        val host = intent.getStringExtra("navigateChannelHost")?.trim()?.removePrefix("https://")
            ?.removePrefix("http://")?.substringBefore('/')?.trimEnd('.')
            ?.takeIf { it.matches(Regex("(?i)[a-z0-9.-]{1,253}")) } ?: return null
        return ChannelNavigationTarget(name, host)
    }

    override fun onResume() {
        super.onResume()
        val apk = pendingUpdateApk ?: return
        if (android.os.Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) {
            pendingUpdateApk = null
            updateMessage = null
            launchInstaller(apk)
        } else {
            updateMessage = getString(R.string.update_permission_required)
        }
    }

    private fun downloadAndInstall(release: GitHubRelease) {
        updateBusy = true
        lifecycleScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { GitHubUpdates.downloadApk(this@MainActivity, release.apkUrl, release.sha256) }
            }
            updateBusy = false
            result.onSuccess { apk ->
                availableUpdate = null
                if (android.os.Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                    pendingUpdateApk = apk
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                } else launchInstaller(apk)
            }.onFailure { error ->
                updateMessage = getString(R.string.update_error_download, error.localizedMessage ?: getString(R.string.update_network_error))
            }
        }
    }

    internal fun checkForUpdates() {
        if (updateBusy) return
        android.widget.Toast.makeText(this, getString(R.string.update_checking), android.widget.Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { GitHubUpdates.latestRelease(this@MainActivity, BuildConfig.VERSION_NAME, includeCurrent = true) }
            }
            result.onFailure { updateMessage = getString(R.string.update_network_error) }
                .onSuccess { release ->
                    if (release == null) updateMessage = getString(R.string.update_network_error)
                    else if (GitHubUpdates.compareVersions(release.version, BuildConfig.VERSION_NAME) > 0) { updateMessage = null; availableUpdate = release }
                    else { updateMessage = null; android.widget.Toast.makeText(this@MainActivity, getString(R.string.update_no_new_version), android.widget.Toast.LENGTH_LONG).show() }
                }
        }
    }

    private fun launchInstaller(apk: java.io.File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { startActivity(intent) }.onFailure {
            updateMessage = getString(R.string.update_error_installer, apk.name)
        }
    }

}
