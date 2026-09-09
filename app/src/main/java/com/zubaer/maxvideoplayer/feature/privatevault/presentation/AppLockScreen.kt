package com.zubaer.maxvideoplayer.feature.privatevault.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.zubaer.maxvideoplayer.feature.privatevault.auth.AppLockController
import com.zubaer.maxvideoplayer.feature.privatevault.auth.VaultAuthResult

/** Generic full-screen privacy surface; underlying application/private item semantics are not composed. */
@Composable
fun AppLockScreen(controller: AppLockController) {
    var credential by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
            .testTag("app_lock_screen"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("MAX Video Player locked", style = MaterialTheme.typography.headlineMedium)
            Text("Enter your Private Vault PIN or passphrase to continue.")
            OutlinedTextField(
                value = credential,
                onValueChange = { credential = it.take(256); message = null },
                label = { Text("PIN or passphrase") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("app_lock_credential"),
            )
            message?.let { Text(it, modifier = Modifier.testTag("app_lock_status")) }
            Button(
                onClick = {
                    val entered = credential
                    credential = ""
                    message = when (val result = controller.unlock(entered.toCharArray())) {
                        VaultAuthResult.Success -> null
                        is VaultAuthResult.InvalidCredential -> if (result.retryAfterMs > 0L) {
                            "Incorrect credential. Try again in ${(result.retryAfterMs + 999L) / 1000L} seconds."
                        } else "Incorrect credential."
                        is VaultAuthResult.Rejected -> result.message
                        is VaultAuthResult.Failure -> result.message
                    }
                },
                enabled = credential.isNotEmpty(),
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).testTag("app_lock_unlock"),
            ) { Text("Unlock") }
        }
    }
}
