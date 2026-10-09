package com.example.sipatkilatis.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.ui.components.AppTopBar
import com.example.sipatkilatis.ui.components.PrimaryButton
import com.example.sipatkilatis.ui.components.SecondaryButton
import com.example.sipatkilatis.ui.components.TextAction
import com.example.sipatkilatis.ui.theme.BodyL
import com.example.sipatkilatis.ui.theme.BodyM
import com.example.sipatkilatis.ui.theme.MessageText
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space

/**
 * Message field (Wash fill, 2dp Ink outline when focused) + optional sender. Shared text arrives pre-filled;
 * nothing is scanned until the user taps "Check message".
 */
@Composable
fun CheckMessageScreen(
    text: String,
    sender: String,
    scanning: Boolean,
    onTextChange: (String) -> Unit,
    onSenderChange: (String) -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val c = Sipat.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = c.wash, unfocusedContainerColor = c.wash, disabledContainerColor = c.wash,
        focusedBorderColor = c.ink, unfocusedBorderColor = c.subtle, disabledBorderColor = c.line,
        focusedTextColor = c.ink, unfocusedTextColor = c.ink, cursorColor = c.ink,
        focusedLabelColor = c.ink, unfocusedLabelColor = c.subtle,
        focusedPlaceholderColor = c.subtle, unfocusedPlaceholderColor = c.subtle,
    )
    Scaffold(containerColor = c.paper, topBar = { AppTopBar(stringResource(R.string.check_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            OutlinedTextField(
                value = text, onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.check_hint), style = MessageText) },
                textStyle = MessageText, minLines = 6, enabled = !scanning,
                shape = Radius.input, colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sender, onValueChange = onSenderChange, singleLine = true, enabled = !scanning,
                label = { Text(stringResource(R.string.check_sender), style = BodyM) },
                textStyle = BodyL, shape = Radius.input, colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                stringResource(R.string.check_paste),
                onClick = { pasteFromClipboard(context)?.let(onTextChange) },
                enabled = !scanning, icon = Icons.Filled.ContentPaste, modifier = Modifier.fillMaxWidth(),
            )
            if (scanning) {
                // Checking state: progress + words; the user can cancel
                Row(Modifier.fillMaxWidth().padding(vertical = Space.s), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = c.ink, trackColor = c.wash, strokeWidth = 3.dp)
                    Spacer(Modifier.width(Space.m))
                    Text(stringResource(R.string.check_scanning), style = BodyL, color = c.ink, modifier = Modifier.weight(1f))
                    TextAction(stringResource(R.string.cancel), onClick = onCancel)
                }
            } else {
                PrimaryButton(stringResource(R.string.check_scan), onClick = onScan, enabled = text.isNotBlank())
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = c.subtle, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Space.s))
                Text(stringResource(R.string.check_privacy), style = BodyM, color = c.subtle)
            }
            Text(stringResource(R.string.check_share_tip), style = BodyM, color = c.subtle)
            Spacer(Modifier.size(Space.l))
        }
    }
}

private fun pasteFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}
