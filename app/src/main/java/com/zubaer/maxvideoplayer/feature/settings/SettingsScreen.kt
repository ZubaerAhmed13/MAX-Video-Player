package com.zubaer.maxvideoplayer.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zubaer.maxvideoplayer.feature.privatevault.auth.PrivateVaultAuthenticator
import com.zubaer.maxvideoplayer.feature.privatevault.auth.VaultAuthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    repository: SettingsRepository,
    vaultAuthenticator: PrivateVaultAuthenticator,
    biometricConfigured: Boolean,
    onEnableBiometric: () -> Unit,
    onDisableBiometric: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by repository.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var importReady by remember { mutableStateOf<SettingsImportResult.Ready?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var currentCredential by remember { mutableStateOf("") }
    var newCredential by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "w")?.let(repository::exportTo) != null
            }.getOrDefault(false)
            withContext(Dispatchers.Main) { message = if (ok) "Non-sensitive settings exported" else "Settings export failed" }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val result = runCatching {
                context.contentResolver.openInputStream(uri)?.let(repository::parseImport)
                    ?: SettingsImportResult.Failure("Settings file could not be opened.")
            }.getOrElse { SettingsImportResult.Failure("Settings file could not be read.") }
            withContext(Dispatchers.Main) {
                when (result) {
                    is SettingsImportResult.Ready -> importReady = result
                    is SettingsImportResult.Failure -> message = result.message
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Back") }
                Text("Settings", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item { message?.let { Text(it, modifier = Modifier.testTag("settings_status")) } }

        item { SectionTitle("Playback & Decoder") }
        item { Text("Playback interaction, decoder, audio and subtitle controls continue to use their existing production repositories and in-player controls. Step 9 does not duplicate those systems.") }

        item { SectionTitle("Library") }
        item { Text("Excluded folders are hidden from the normal library only. They are not encrypted. Use Private Vault for encrypted private media.") }

        item { SectionTitle("Network, Cloud & Output") }
        item { Text("Saved network/cloud locations and Cast/external-display behavior remain managed by their existing screens. Private Vault media is intentionally excluded from Cast and external presentation output.") }

        item { SectionTitle("Privacy & Security") }
        item {
            SettingSwitch(
                title = "App Lock",
                detail = if (vaultAuthenticator.isConfigured()) "Require the vault credential after the selected background timeout." else "Create Private Vault first so App Lock has a recovery credential.",
                checked = settings.appLockEnabled,
                enabled = vaultAuthenticator.isConfigured(),
                onChecked = repository::setAppLockEnabled,
            )
        }
        item {
            SettingChoice(
                title = "Auto-lock timeout",
                value = settings.autoLockTimeout.readable(),
                onNext = {
                    val entries = AutoLockTimeout.entries
                    repository.setAutoLockTimeout(entries[(entries.indexOf(settings.autoLockTimeout) + 1) % entries.size])
                },
            )
        }
        item {
            SettingSwitch(
                title = "Protect private screens from screenshots",
                detail = "Uses the Android secure-window policy while Private Vault or private playback is visible.",
                checked = settings.protectPrivateScreens,
                onChecked = repository::setProtectPrivateScreens,
            )
        }
        item {
            SettingSwitch(
                title = "Biometric unlock",
                detail = if (biometricConfigured) "A device-protected biometric wrapper is configured. PIN/passphrase remains the recovery path." else "Optional convenience unlock. PIN/passphrase remains required for recovery.",
                checked = settings.biometricUnlockEnabled && biometricConfigured,
                enabled = vaultAuthenticator.isConfigured(),
                onChecked = { enabled ->
                    if (enabled) onEnableBiometric() else {
                        onDisableBiometric()
                        repository.setBiometricUnlockEnabled(false)
                    }
                },
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Change vault PIN / passphrase", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = currentCredential,
                    onValueChange = { currentCredential = it.take(256) },
                    label = { Text("Current credential") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = newCredential,
                    onValueChange = { newCredential = it.take(256) },
                    label = { Text("New credential") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        val old = currentCredential
                        val replacement = newCredential
                        currentCredential = ""
                        newCredential = ""
                        scope.launch(Dispatchers.Default) {
                            val result = vaultAuthenticator.changeCredential(old.toCharArray(), replacement.toCharArray())
                            withContext(Dispatchers.Main) {
                                message = when (result) {
                                    VaultAuthResult.Success -> "Vault credential changed"
                                    is VaultAuthResult.InvalidCredential -> "Current credential is incorrect"
                                    is VaultAuthResult.Rejected -> result.message
                                    is VaultAuthResult.Failure -> result.message
                                }
                            }
                        }
                    },
                    enabled = vaultAuthenticator.isConfigured() && currentCredential.isNotEmpty() && newCredential.isNotEmpty(),
                    modifier = Modifier.sizeIn(minHeight = 48.dp),
                ) { Text("Change credential") }
            }
        }

        item { SectionTitle("Sleep Timer") }
        item {
            SettingChoice(
                title = "Fade before timer expiry",
                value = settings.sleepFadeDuration.readable(),
                onNext = {
                    val entries = SleepFadeDuration.entries
                    repository.setSleepFadeDuration(entries[(entries.indexOf(settings.sleepFadeDuration) + 1) % entries.size])
                },
            )
        }

        item { SectionTitle("Accessibility") }
        item {
            SettingSwitch(
                title = "Reduce motion",
                detail = "Disables non-essential Step-9 motion while preserving functional playback progress.",
                checked = settings.reduceMotion,
                onChecked = repository::setReduceMotion,
            )
        }
        item {
            SettingChoice(
                title = "Player control contrast",
                value = if (settings.contrastMode == AccessibilityContrastMode.HIGH_CONTRAST) "High contrast" else "Standard",
                onNext = {
                    repository.setContrastMode(
                        if (settings.contrastMode == AccessibilityContrastMode.STANDARD) AccessibilityContrastMode.HIGH_CONTRAST
                        else AccessibilityContrastMode.STANDARD,
                    )
                },
            )
        }
        item {
            SettingSwitch(
                title = "Use system caption style",
                detail = "Makes Android caption preferences available to the subtitle presentation policy without removing MAX custom styles.",
                checked = settings.useSystemCaptionStyle,
                onChecked = repository::setUseSystemCaptionStyle,
            )
        }

        item { SectionTitle("Diagnostics") }
        item { Text("Diagnostic text exported by Step 9 passes through centralized token, password, cookie, Authorization and signed-query redaction.") }

        item { SectionTitle("Settings import / export") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { exportLauncher.launch("max-video-player-settings.json") }, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) }, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Import") }
            }
        }
        item {
            OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text("Reset all non-sensitive settings")
            }
        }
        item { Text("This reset does not delete Private Vault media, cloud accounts, network credentials, history or playlists.") }
        item { SectionTitle("About") }
        item { Text("MAX Video Player · Step 9 security, settings, sleep timer and accessibility implementation") }
        item { Column(Modifier.padding(bottom = 24.dp)) {} }
    }

    importReady?.let { ready ->
        AlertDialog(
            onDismissRequest = { importReady = null },
            title = { Text("Import settings?") },
            text = {
                Text(
                    if (ready.summary.changedKeys.isEmpty()) "No supported setting changes were found."
                    else "Apply ${ready.summary.changedKeys.size} supported setting change(s)? Unknown future fields ignored: ${ready.summary.ignoredUnknownKeys}.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = repository.applyImport(ready)
                    importReady = null
                    message = if (ok) "Settings imported" else "Settings import could not be committed; previous settings were kept"
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { importReady = null }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset non-sensitive settings?") },
            text = { Text("Private media, credentials, accounts, history and playlists will not be erased.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; repository.resetAllNonSensitive(); message = "Non-sensitive settings reset" }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
            modifier = Modifier.semantics { stateDescription = if (checked) "On" else "Off" },
        )
    }
}

@Composable
private fun SettingChoice(title: String, value: String, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onNext, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Change") }
    }
}

private fun AutoLockTimeout.readable(): String = when (this) {
    AutoLockTimeout.IMMEDIATELY -> "Immediately"
    AutoLockTimeout.THIRTY_SECONDS -> "After 30 seconds"
    AutoLockTimeout.ONE_MINUTE -> "After 1 minute"
    AutoLockTimeout.FIVE_MINUTES -> "After 5 minutes"
}

private fun SleepFadeDuration.readable(): String = when (this) {
    SleepFadeDuration.OFF -> "Off"
    SleepFadeDuration.FIFTEEN_SECONDS -> "15 seconds"
    SleepFadeDuration.THIRTY_SECONDS -> "30 seconds"
    SleepFadeDuration.SIXTY_SECONDS -> "60 seconds"
}
