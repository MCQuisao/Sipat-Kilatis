package com.example.sipatkilatis.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.model.Sensitivity
import com.example.sipatkilatis.ui.AppLanguage
import com.example.sipatkilatis.ui.components.AppTopBar
import com.example.sipatkilatis.ui.components.SectionCard
import kotlinx.coroutines.launch

/** Language, sensitivity, trusted contacts, and the (placeholder) online update button. */
@Composable
fun SettingsScreen(
    sensitivity: Sensitivity,
    trustedContacts: List<String>,
    onSensitivityChange: (Sensitivity) -> Unit,
    onAddContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
    onBack: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val updateMsg = stringResource(R.string.settings_update_placeholder)
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.settings_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(title = stringResource(R.string.settings_language)) {
                val current = AppLanguage.current()
                LanguageOption(stringResource(R.string.lang_filipino), current == AppLanguage.FILIPINO) { AppLanguage.set(AppLanguage.FILIPINO) }
                LanguageOption(stringResource(R.string.lang_english), current == AppLanguage.ENGLISH) { AppLanguage.set(AppLanguage.ENGLISH) }
            }

            SectionCard(title = stringResource(R.string.settings_sensitivity)) {
                val options = listOf(Sensitivity.LOW to R.string.sens_low, Sensitivity.NORMAL to R.string.sens_normal,
                    Sensitivity.HIGH to R.string.sens_high)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = sensitivity == value,
                            onClick = { onSensitivityChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                        ) { Text(stringResource(label)) }
                    }
                }
                Text(
                    stringResource(when (sensitivity) {
                        Sensitivity.LOW -> R.string.sens_low_desc
                        Sensitivity.NORMAL -> R.string.sens_normal_desc
                        Sensitivity.HIGH -> R.string.sens_high_desc
                    }),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = stringResource(R.string.settings_trusted)) {
                Text(stringResource(R.string.settings_trusted_desc), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (trustedContacts.isEmpty()) {
                    Text(stringResource(R.string.settings_trusted_empty), style = MaterialTheme.typography.bodyLarge)
                }
                trustedContacts.forEach { contact ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(contact, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveContact(contact) }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.settings_trusted_remove, contact))
                        }
                    }
                }
                OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_trusted_add))
                }
            }

            SectionCard(title = stringResource(R.string.settings_update)) {
                Text(stringResource(R.string.settings_update_desc), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Placeholder: the optional online blocklist update is built in phase 7.
                OutlinedButton(onClick = { scope.launch { snackbar.showSnackbar(updateMsg) } },
                    modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_update))
                }
            }

            SectionCard(title = stringResource(R.string.settings_privacy)) {
                Text(stringResource(R.string.settings_privacy_body), style = MaterialTheme.typography.bodyMedium)
            }

            Text(stringResource(R.string.settings_version, appVersion()), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onAdd = { onAddContact(it); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun AddContactDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_trusted_add)) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.settings_trusted_hint)) })
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
}
