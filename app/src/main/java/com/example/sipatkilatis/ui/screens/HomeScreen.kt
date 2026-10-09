package com.example.sipatkilatis.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.ui.components.OfflineBadge
import com.example.sipatkilatis.ui.components.PermissionCard
import com.example.sipatkilatis.ui.components.rememberPermissionController
import com.example.sipatkilatis.ui.components.SectionCard
import com.example.sipatkilatis.ui.theme.ScamRed

/** Dashboard: protection status, offline badge, counters, big Check button, links to other screens. */
@Composable
fun HomeScreen(
    protectionOn: Boolean,
    scannedCount: Int,
    scamCount: Int,
    onProtectionChange: (Boolean) -> Unit,
    onCheckMessage: () -> Unit,
    onHistory: () -> Unit,
    onGuide: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                OfflineBadge()
            }

            ProtectionCard(protectionOn, onProtectionChange)

            // Status of SMS / alert / chat-app permissions, each with a button to fix it
            PermissionCard(rememberPermissionController())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CounterCard(scannedCount.toString(), stringResource(R.string.home_scanned), Modifier.weight(1f))
                CounterCard(scamCount.toString(), stringResource(R.string.home_caught), Modifier.weight(1f), ScamRed)
            }

            Button(
                onClick = onCheckMessage,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.home_check_button), style = MaterialTheme.typography.titleLarge)
            }
            Text(stringResource(R.string.home_check_hint), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))

            NavRow(Icons.Filled.History, stringResource(R.string.home_history), onHistory)
            NavRow(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.home_guide), onGuide)
            NavRow(Icons.Filled.Settings, stringResource(R.string.home_settings), onSettings)
        }
    }
}

@Composable
private fun ProtectionCard(on: Boolean, onChange: (Boolean) -> Unit) {
    val container = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val content = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (on) Icons.Filled.Shield else Icons.Outlined.Shield, contentDescription = null,
                modifier = Modifier.size(48.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(if (on) R.string.home_protection_on else R.string.home_protection_off),
                    style = MaterialTheme.typography.titleLarge)
                Text(stringResource(if (on) R.string.home_protection_on_body else R.string.home_protection_off_body),
                    style = MaterialTheme.typography.bodyMedium)
            }
            // Mango track so the switch stays visible on the blue card
            Switch(
                checked = on,
                onCheckedChange = onChange,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.secondary,
                    checkedThumbColor = Color.White,
                    checkedBorderColor = Color.White,
                ),
            )
        }
    }
}

@Composable
private fun CounterCard(value: String, label: String, modifier: Modifier, valueColor: Color = Color.Unspecified) {
    SectionCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.primary else valueColor)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NavRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
