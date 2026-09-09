package com.zubaer.maxvideoplayer.feature.privatevault.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateImportMode
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultItem
import com.zubaer.maxvideoplayer.feature.privatevault.PrivateVaultState
import com.zubaer.maxvideoplayer.feature.privatevault.repository.PrivateVaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PrivateVaultScreen(
    viewModel: PrivateVaultViewModel,
    repository: PrivateVaultRepository,
    onBack: () -> Unit,
    onPlay: (PrivateVaultItem) -> Unit,
    biometricUnlockEnabled: Boolean,
    onBiometricUnlock: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var credential by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<PrivateVaultItem?>(null) }
    var confirmErase by remember { mutableStateOf(false) }
    var restoreItem by remember { mutableStateOf<PrivateVaultItem?>(null) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }

    val copyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri, PrivateImportMode.COPY)
    }
    val movePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri, PrivateImportMode.MOVE)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/*")) { destination ->
        val item = restoreItem
        restoreItem = null
        if (destination != null && item != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                            repository.restore(item.vaultId, output)
                        } == true
                    }.getOrDefault(false)
                }
                restoreMessage = if (ok) "Private media restored to the selected location" else "Restore failed; the private copy was kept"
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Back") }
            Text("Private Vault", style = MaterialTheme.typography.headlineMedium)
        }

        state.message?.let { Text(it, modifier = Modifier.testTag("private_vault_status")) }
        restoreMessage?.let { Text(it) }
        if (state.busy) CircularProgressIndicator(modifier = Modifier.testTag("private_vault_busy"))

        when (state.vaultState) {
            PrivateVaultState.UNCONFIGURED -> Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Create Private Vault", style = MaterialTheme.typography.titleLarge)
                Text("Private Vault creates encrypted private copies managed by MAX Video Player. Excluding a folder from the library is not the same as making it private.")
                OutlinedTextField(
                    value = credential,
                    onValueChange = { credential = it.take(256) },
                    label = { Text("PIN or passphrase") },
                    supportingText = { Text("Use at least 6 digits for a PIN, or at least 8 characters for a passphrase.") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("private_create_credential"),
                    singleLine = true,
                )
                Button(
                    onClick = { viewModel.create(credential); credential = "" },
                    enabled = !state.busy,
                    modifier = Modifier.sizeIn(minHeight = 48.dp).testTag("private_create"),
                ) { Text("Create Private Vault") }
            }

            PrivateVaultState.LOCKED, PrivateVaultState.ERROR -> Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).testTag("private_vault_locked"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Private Vault locked", style = MaterialTheme.typography.titleLarge)
                Text("Unlock to view private videos. Private filenames and titles are not exposed while locked.")
                OutlinedTextField(
                    value = credential,
                    onValueChange = { credential = it.take(256) },
                    label = { Text("PIN or passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("private_unlock_credential"),
                    singleLine = true,
                )
                Button(
                    onClick = { viewModel.unlock(credential); credential = "" },
                    enabled = !state.busy,
                    modifier = Modifier.sizeIn(minHeight = 48.dp).testTag("private_unlock"),
                ) { Text("Unlock") }
                if (biometricUnlockEnabled && onBiometricUnlock != null) {
                    OutlinedButton(
                        onClick = onBiometricUnlock,
                        modifier = Modifier.sizeIn(minHeight = 48.dp).testTag("private_biometric_unlock"),
                    ) { Text("Unlock with biometrics") }
                }
                Text("If you forget the PIN/passphrase and biometric unlock is unavailable, the encrypted vault cannot be recovered. Resetting the vault permanently removes its private media.")
            }

            PrivateVaultState.UNLOCKING -> Text("Unlocking Private Vault…", modifier = Modifier.testTag("private_vault_unlocking"))

            PrivateVaultState.UNLOCKED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { copyPicker.launch(arrayOf("video/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).sizeIn(minHeight = 48.dp),
                    ) { Text("Copy to Private") }
                    Button(
                        onClick = { movePicker.launch(arrayOf("video/*")) },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f).sizeIn(minHeight = 48.dp),
                    ) { Text("Move to Private") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::lock, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Lock now") }
                    OutlinedButton(onClick = { confirmErase = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Erase Private Vault") }
                }
                if (state.items.isEmpty() && !state.busy) {
                    Text("No private videos yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("private_items"),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.items, key = { it.vaultId }) { item ->
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(item.metadata.title, style = MaterialTheme.typography.titleMedium)
                                Text(item.metadata.mimeType ?: "Video", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onPlay(item) },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                    ) { Text("Play") }
                                    OutlinedButton(
                                        onClick = {
                                            restoreItem = item
                                            restorePicker.launch(item.metadata.originalDisplayName.ifBlank { "restored-video" })
                                        },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                    ) { Text("Restore") }
                                    OutlinedButton(
                                        onClick = { pendingDelete = item },
                                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                                    ) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete from Private Vault?") },
            text = { Text("This removes the encrypted private copy. It does not claim secure physical erasure from flash storage.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; viewModel.delete(item.vaultId) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase Private Vault?") },
            text = { Text("All encrypted private files will be permanently inaccessible/deleted. Normal videos, playlists, history and ordinary settings are not erased.") },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; viewModel.eraseVault() }) { Text("Erase Private Vault") }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } },
        )
    }
}
