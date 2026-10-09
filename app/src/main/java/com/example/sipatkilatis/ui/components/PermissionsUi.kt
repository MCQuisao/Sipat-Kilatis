package com.example.sipatkilatis.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.sipatkilatis.R
import com.example.sipatkilatis.capture.Permissions
import com.example.sipatkilatis.ui.theme.BodyL
import com.example.sipatkilatis.ui.theme.BodyM
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space

/** Current state of the three things automatic screening needs. */
data class PermissionStatus(val sms: Boolean, val alerts: Boolean, val chatApps: Boolean) {
    val allOn get() = sms && alerts && chatApps
}

/** Permission status + actions to fix each one. Re-checked every time the screen resumes (back from Settings). */
class PermissionController(
    val status: PermissionStatus,
    val requestSms: () -> Unit,
    val requestAlerts: () -> Unit,
    val openChatAccess: () -> Unit,
)

@Composable
fun rememberPermissionController(): PermissionController {
    val context = LocalContext.current
    fun read() = PermissionStatus(Permissions.hasSms(context), Permissions.hasNotifications(context),
        Permissions.hasChatAccess(context))
    var status by remember { mutableStateOf(read()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { status = read() }
    var showChatDialog by rememberSaveable { mutableStateOf(false) }

    // If the user denied twice, Android stops showing the dialog: send them to the app's settings instead
    fun onResult(permission: String, granted: Boolean) {
        status = read()
        val activity = context.findActivity() ?: return
        if (!granted && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            context.startActivity(Permissions.appSettings(context))
        }
    }

    val alertsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (Build.VERSION.SDK_INT >= 33) onResult(Manifest.permission.POST_NOTIFICATIONS, granted)
    }
    val requestAlerts = {
        if (Build.VERSION.SDK_INT >= 33) alertsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(Permissions.appSettings(context))
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onResult(Manifest.permission.RECEIVE_SMS, granted)
        // SMS screening is only useful with alerts, so ask for that next
        if (granted && !Permissions.hasNotifications(context) && Build.VERSION.SDK_INT >= 33) requestAlerts()
    }

    if (showChatDialog) {
        AlertDialog(
            onDismissRequest = { showChatDialog = false },
            title = { Text(stringResource(R.string.perm_chat_dialog_title)) },
            text = { Text(stringResource(R.string.perm_chat_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showChatDialog = false
                    context.startActivity(Permissions.chatAccessSettings(context))
                }) { Text(stringResource(R.string.perm_open_settings)) }
            },
            dismissButton = { TextButton(onClick = { showChatDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    return PermissionController(
        status = status,
        requestSms = { smsLauncher.launch(Manifest.permission.RECEIVE_SMS) },
        requestAlerts = requestAlerts,
        openChatAccess = { showChatDialog = true },
    )
}

/**
 * Home checklist: one plain row per permission with a check or a "Turn on" action. Collapses to "All set"
 * once everything is granted. Plus the Xiaomi Autostart tip on those phones.
 */
@Composable
fun PermissionCard(controller: PermissionController) {
    val context = LocalContext.current
    val c = Sipat.colors
    val s = controller.status
    ContentCard(title = stringResource(R.string.onb_perm_title)) {
        if (s.allOn) {
            CheckRow(Icons.Filled.Check, stringResource(R.string.perm_all_on), tint = c.safe.strong, tileColor = c.safe.container)
        } else {
            if (!s.sms) PermissionRow(Icons.Filled.Sms, R.string.perm_sms, R.string.perm_sms_why, controller.requestSms)
            if (!s.alerts) PermissionRow(Icons.Filled.NotificationsActive, R.string.perm_alerts, R.string.perm_alerts_why,
                controller.requestAlerts)
            if (!s.chatApps) PermissionRow(Icons.AutoMirrored.Filled.Chat, R.string.perm_chat, R.string.perm_chat_why,
                controller.openChatAccess)
        }
        if (Permissions.isXiaomi) {
            DividerLine()
            Text(stringResource(R.string.perm_xiaomi), style = BodyM, color = c.subtle)
            SecondaryButton(stringResource(R.string.perm_xiaomi_button),
                onClick = { context.startActivity(Permissions.appSettings(context)) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** One unresolved permission: what it is, why, and a "Turn on" button. */
@Composable
private fun PermissionRow(icon: ImageVector, title: Int, why: Int, fix: () -> Unit) {
    val c = Sipat.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = Space.s)) {
            IconTile(icon, c.careful.strong, c.careful.container, size = 36.dp, iconSize = 20.dp)
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = BodyL, color = c.ink)
                Text(stringResource(why), style = BodyM, color = c.subtle)
            }
            Spacer(Modifier.width(Space.s))
            SecondaryButton(stringResource(R.string.perm_turn_on), onClick = fix)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
