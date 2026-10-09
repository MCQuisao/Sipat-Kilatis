package com.example.sipatkilatis.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.sipatkilatis.model.Appearance
import com.example.sipatkilatis.model.Sensitivity
import com.example.sipatkilatis.ui.AppLanguage
import com.example.sipatkilatis.ui.components.ContentCard
import com.example.sipatkilatis.ui.components.IconTile
import com.example.sipatkilatis.ui.components.ScreenHeader
import com.example.sipatkilatis.ui.components.navBarClearance
import com.example.sipatkilatis.ui.components.ConfirmSheet
import com.example.sipatkilatis.ui.components.SecondaryButton
import com.example.sipatkilatis.ui.components.SegmentedChoice
import com.example.sipatkilatis.ui.components.TextAction
import com.example.sipatkilatis.ui.theme.BodyL
import com.example.sipatkilatis.ui.theme.BodyM
import com.example.sipatkilatis.ui.theme.Label
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space
import kotlinx.coroutines.launch

/** One card per group of settings. Every toggle shows On / Off in words, not only a switch. */
@Composable
fun SettingsScreen(
    sensitivity: Sensitivity,
    appearance: Appearance,
    trustedContacts: List<String>,
    aiExplanations: Boolean,
    llmInstalled: Boolean,
    llmSizeMb: Int,
    onSensitivityChange: (Sensitivity) -> Unit,
    onAppearanceChange: (Appearance) -> Unit,
    onAiExplanationsChange: (Boolean) -> Unit,
    onExport: (onReady: (Intent?) -> Unit) -> Unit,
    onClearHistory: () -> Unit,
    onOpenDemo: () -> Unit,
    onAddContact: (String) -> Unit,
    onRemoveContact: (String) -> Unit,
) {
    val c = Sipat.colors
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showClear by rememberSaveable { mutableStateOf(false) }
    val noReportsMsg = stringResource(R.string.settings_export_none)
    val clearedMsg = stringResource(R.string.settings_clear_done)

    val clearance = navBarClearance()
    Scaffold(
        containerColor = c.paper,
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = clearance - Space.l)) },
    ) { _ ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.m),
        ) {
            ScreenHeader(stringResource(R.string.settings_title))

            ContentCard(title = stringResource(R.string.settings_language)) {
                val current = AppLanguage.current()
                LanguageOption(stringResource(R.string.lang_filipino), current == AppLanguage.FILIPINO) { AppLanguage.set(context, AppLanguage.FILIPINO) }
                LanguageOption(stringResource(R.string.lang_english), current == AppLanguage.ENGLISH) { AppLanguage.set(context, AppLanguage.ENGLISH) }
            }

            ContentCard(title = stringResource(R.string.settings_appearance)) {
                val looks = listOf(Appearance.LIGHT, Appearance.DARK, Appearance.SYSTEM)
                SegmentedChoice(
                    listOf(stringResource(R.string.appearance_light), stringResource(R.string.appearance_dark), stringResource(R.string.appearance_system)),
                    selected = looks.indexOf(appearance), onSelect = { onAppearanceChange(looks[it]) },
                )
            }

            // Protection (sensitivity)
            ContentCard(title = stringResource(R.string.settings_protection)) {
                val levels = listOf(Sensitivity.LOW, Sensitivity.NORMAL, Sensitivity.HIGH)
                SegmentedChoice(
                    listOf(stringResource(R.string.sens_low), stringResource(R.string.sens_normal), stringResource(R.string.sens_high)),
                    selected = levels.indexOf(sensitivity), onSelect = { onSensitivityChange(levels[it]) },
                )
                Text(stringResource(when (sensitivity) {
                    Sensitivity.LOW -> R.string.sens_low_desc
                    Sensitivity.NORMAL -> R.string.sens_normal_desc
                    Sensitivity.HIGH -> R.string.sens_high_desc
                }), style = BodyM, color = c.subtle)
            }

            ContentCard(title = stringResource(R.string.settings_trusted)) {
                Text(stringResource(R.string.settings_trusted_desc), style = BodyM, color = c.subtle)
                if (trustedContacts.isEmpty()) {
                    Text(stringResource(R.string.settings_trusted_empty), style = BodyL, color = c.ink)
                }
                trustedContacts.forEach { contact ->
                    Row(Modifier.fillMaxWidth().heightIn(min = Space.touch), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Person, c.safe.strong, c.safe.container, size = 36.dp, iconSize = 20.dp)
                        Spacer(Modifier.width(Space.m))
                        Text(contact, style = BodyL, color = c.ink, modifier = Modifier.weight(1f))
                        IconButton(onClick = { onRemoveContact(contact) }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.settings_trusted_remove, contact), tint = c.subtle)
                        }
                    }
                }
                SecondaryButton(stringResource(R.string.settings_trusted_add), onClick = { showAddDialog = true },
                    icon = Icons.Rounded.PersonAdd, modifier = Modifier.fillMaxWidth())
            }

            ContentCard(title = stringResource(R.string.settings_ai)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_ai_desc), style = BodyM, color = c.subtle, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(Space.m))
                    Text(stringResource(if (aiExplanations && llmInstalled) R.string.settings_on else R.string.settings_off),
                        style = Label, color = c.ink)
                    Spacer(Modifier.width(Space.s))
                    Switch(
                        checked = aiExplanations && llmInstalled, onCheckedChange = onAiExplanationsChange, enabled = llmInstalled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = c.card, checkedTrackColor = c.safe.strong, checkedBorderColor = c.safe.strong,
                            uncheckedThumbColor = c.subtle, uncheckedTrackColor = c.wash, uncheckedBorderColor = c.mute,
                            disabledUncheckedThumbColor = c.mute, disabledUncheckedTrackColor = c.wash, disabledUncheckedBorderColor = c.line,
                        ),
                    )
                }
                Text(
                    if (llmInstalled) stringResource(R.string.settings_ai_installed, llmSizeMb) else stringResource(R.string.settings_ai_missing),
                    style = BodyM, color = c.subtle,
                )
            }

            // Data (only on this phone)
            ContentCard(title = stringResource(R.string.settings_data)) {
                Text(stringResource(R.string.settings_data_desc), style = BodyM, color = c.subtle)
                Text(stringResource(R.string.settings_export_desc), style = BodyM, color = c.ink)
                SecondaryButton(stringResource(R.string.settings_export), icon = Icons.Rounded.IosShare, modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onExport { intent ->
                            if (intent != null) context.startActivity(intent)
                            else scope.launch { snackbar.showSnackbar(noReportsMsg) }
                        }
                    })
                SecondaryButton(stringResource(R.string.settings_clear), icon = Icons.Rounded.DeleteSweep, modifier = Modifier.fillMaxWidth(),
                    onClick = { showClear = true })
            }

            ContentCard(title = stringResource(R.string.settings_privacy)) {
                Text(stringResource(R.string.settings_privacy_body), style = BodyM, color = c.ink)
            }

            // About (tap the version 5 times for demo mode)
            ContentCard(title = stringResource(R.string.settings_about)) {
                var versionTaps by remember { mutableIntStateOf(0) }
                Text(
                    stringResource(R.string.settings_version, appVersion()), style = BodyL, color = c.subtle,
                    modifier = Modifier.fillMaxWidth().heightIn(min = Space.touch).clickable {
                        versionTaps++
                        if (versionTaps >= 5) { versionTaps = 0; onOpenDemo() }
                    }.padding(vertical = Space.m),
                )
            }
            Spacer(Modifier.heightIn(min = clearance))
        }
    }

    if (showClear) {
        ConfirmSheet(
            stringResource(R.string.settings_clear_confirm_title), stringResource(R.string.settings_clear_confirm_body),
            stringResource(R.string.delete),
            onConfirm = { onClearHistory(); scope.launch { snackbar.showSnackbar(clearedMsg) } },
            onDismiss = { showClear = false },
        )
    }
    if (showAddDialog) {
        AddContactDialog(onAdd = { onAddContact(it); showAddDialog = false }, onDismiss = { showAddDialog = false })
    }
}

@Composable
private fun AddContactDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Sipat.colors
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = c.card, shape = Radius.hero,
        title = { Text(stringResource(R.string.settings_trusted_add), color = c.ink) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, textStyle = BodyL,
                label = { Text(stringResource(R.string.settings_trusted_hint)) }, shape = Radius.input,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = c.ink, unfocusedBorderColor = c.line,
                    focusedLabelColor = c.ink, cursorColor = c.ink))
        },
        confirmButton = { TextAction(stringResource(R.string.add), onClick = { if (name.isNotBlank()) onAdd(name) }) },
        dismissButton = { TextAction(stringResource(R.string.cancel), onClick = onDismiss) },
    )
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
}