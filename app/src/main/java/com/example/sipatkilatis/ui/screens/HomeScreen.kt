package com.example.sipatkilatis.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.ui.components.EmptyState
import com.example.sipatkilatis.ui.components.PermissionCard
import com.example.sipatkilatis.ui.components.PrimaryButton
import com.example.sipatkilatis.ui.components.SecondaryButton
import com.example.sipatkilatis.ui.components.navBarClearance
import com.example.sipatkilatis.ui.components.rememberPermissionController
import com.example.sipatkilatis.ui.theme.Badge
import com.example.sipatkilatis.ui.theme.BodyM
import com.example.sipatkilatis.ui.theme.Display
import com.example.sipatkilatis.ui.theme.Headline
import com.example.sipatkilatis.ui.theme.Label
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space
import com.example.sipatkilatis.ui.theme.Title

/**
 * Home: title + online/offline line, the protection card, two stat cards, the permission checklist.
 * "Check a message" is pinned just above the floating nav bar (History / Guide / Settings live in the nav bar).
 */
@Composable
fun HomeScreen(
    protectionOn: Boolean,
    scannedCount: Int,
    scamCount: Int,
    onProtectionChange: (Boolean) -> Unit,
    onCheckMessage: () -> Unit,
) {
    val c = Sipat.colors
    val permissions = rememberPermissionController()
    val online = rememberIsOnline()
    val needsAttention = !protectionOn || !permissions.status.allOn
    val clearance = navBarClearance()

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Space.screen),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Column(Modifier.statusBarsPadding().padding(top = Space.l)) {
                Text(stringResource(R.string.app_name), style = Headline, color = c.ink, modifier = Modifier.semantics { heading() })
                StatusLine(online)
            }

            ProtectionCard(
                on = protectionOn,
                needsAttention = needsAttention,
                onChange = onProtectionChange,
                onFix = {
                    when {
                        !protectionOn -> onProtectionChange(true)
                        !permissions.status.sms -> permissions.requestSms()
                        !permissions.status.alerts -> permissions.requestAlerts()
                        else -> permissions.openChatAccess()
                    }
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                StatCard(scannedCount, stringResource(R.string.home_scanned), Modifier.weight(1f))
                StatCard(scamCount, stringResource(R.string.home_caught), Modifier.weight(1f))
            }
            if (scannedCount == 0) EmptyState(Icons.Outlined.Inbox, stringResource(R.string.home_empty))

            PermissionCard(permissions)

            // Room for the pinned button + floating nav bar
            Spacer(Modifier.height(clearance + Space.button + Space.l))
        }

        // Main action in thumb reach, floating just above the nav bar, over a soft fade
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.paper, c.paper)))
                .padding(start = Space.screen, end = Space.screen, top = Space.xl, bottom = clearance - Space.xs),
        ) {
            PrimaryButton(stringResource(R.string.home_check_button), onClick = onCheckMessage, icon = Icons.Rounded.Search)
        }
    }
}

/** Small "Protected on this phone" / "Offline. Still protecting you." line. */
@Composable
private fun StatusLine(online: Boolean) {
    val c = Sipat.colors
    Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (online) Icons.Rounded.PhoneAndroid else Icons.Rounded.CloudOff, contentDescription = null,
            tint = c.subtle, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Space.s))
        Text(stringResource(if (online) R.string.home_online else R.string.home_offline), style = BodyM, color = c.subtle)
    }
}


/**
 * Protection on and all set: deep ink card with a soft sheen, green "Active" badge, clean switch.
 * Needs attention: amber card with a Fix button. Always told in words too, never color alone.
 */
@Composable
private fun ProtectionCard(on: Boolean, needsAttention: Boolean, onChange: (Boolean) -> Unit, onFix: () -> Unit) {
    val c = Sipat.colors
    val good = on && !needsAttention
    // The "good" card is always dark (also in dark mode), the attention card uses the amber tint
    val bg = if (good) Brush.linearGradient(listOf(Color(0xFF1B1E24), Color(0xFF2B3039)))
             else Brush.linearGradient(listOf(c.careful.container, c.careful.container))
    val text = if (good) Color(0xFFF4F5F7) else c.careful.onContainer
    val soft = if (good) Color(0xFFB8BDC7) else c.careful.onContainer.copy(alpha = 0.8f)

    Column(
        Modifier.fillMaxWidth().background(bg, Radius.hero)
            .then(if (good) Modifier else Modifier.border(1.dp, c.careful.strong.copy(alpha = 0.4f), Radius.hero))
            .padding(Space.xl),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        // Title + status badge on one line, description under it
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(when {
                !on -> R.string.home_protection_off
                needsAttention -> R.string.home_protection_attention
                else -> R.string.home_protection_on
            }), style = Title.copy(fontSize = 20.sp, lineHeight = 26.sp), color = text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(Space.m))
            // Status badge: green dot + "On" / amber dot + "Off"
            Row(
                Modifier.background(if (good) Color.White.copy(alpha = 0.08f) else c.card.copy(alpha = 0.6f), Radius.pill)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(if (good) c.safe.strong else c.careful.strong, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (on) R.string.settings_on else R.string.settings_off), style = Badge, color = text)
            }
        }
        Text(stringResource(if (on) R.string.home_protection_on_body else R.string.home_protection_off_body),
            style = BodyM, color = soft)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.home_auto_check), style = Label, color = text, modifier = Modifier.weight(1f))
            if (needsAttention) {
                SecondaryButton(stringResource(R.string.home_fix), onClick = onFix)
                Spacer(Modifier.width(Space.m))
            }
            Switch(
                checked = on, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White, checkedTrackColor = c.safe.strong, checkedBorderColor = c.safe.strong,
                    uncheckedThumbColor = c.careful.onContainer, uncheckedTrackColor = c.card,
                    uncheckedBorderColor = c.careful.onContainer.copy(alpha = 0.5f),
                ),
            )
        }
    }
}

/** One metric: big number + label. */
@Composable
private fun StatCard(value: Int, label: String, modifier: Modifier) {
    val c = Sipat.colors
    Column(modifier.background(c.card, Radius.card).border(1.dp, c.line, Radius.card).padding(Space.l)) {
        Text(value.toString(), style = Display, color = c.ink)
        Text(label, style = BodyM, color = c.subtle)
    }
}

/** Live online / offline state (read-only network-state permission). */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun check() = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    var online by remember { mutableStateOf(check()) }
    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = check() }
            override fun onLost(network: Network) { online = check() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { online = check() }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
    return online
}
