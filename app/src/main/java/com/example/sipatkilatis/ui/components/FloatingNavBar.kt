package com.example.sipatkilatis.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.ui.theme.Badge
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space

/** Height of the floating bar itself (without the gap under it). */
val FloatingNavHeight = 68.dp

/** One tab of the floating nav bar. Outlined icon when inactive, filled when active. */
data class NavTab(val route: String, @param:StringRes val label: Int, val icon: ImageVector, val activeIcon: ImageVector)

/** The four main sections. Routes match [com.example.sipatkilatis.ui.Routes]. */
val MainTabs = listOf(
    NavTab("home", R.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    NavTab("history", R.string.nav_history, Icons.Outlined.History, Icons.Rounded.History),
    NavTab("guide", R.string.nav_guide, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Rounded.MenuBook),
    NavTab("settings", R.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings),
)

/**
 * Floating, pill-shaped bar that sits above the content on the four main screens.
 * Inactive tabs: ink icon + label. Active tab: soft grey pill behind icon and label, filled icon, bolder label.
 */
@Composable
fun FloatingNavBar(current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Sipat.colors
    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = Space.l, vertical = Space.m)
            .fillMaxWidth()
            .height(FloatingNavHeight)
            .shadow(16.dp, Radius.pill, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .background(c.card, Radius.pill)
            .border(1.dp, c.line, Radius.pill)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MainTabs.forEach { tab ->
            val active = tab.route == current
            val pill by animateColorAsState(if (active) c.wash else Color.Transparent, label = "navPill")
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .background(pill, Radius.pill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.Tab,
                    ) { if (!active) onSelect(tab.route) }
                    .semantics { selected = active },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (active) tab.activeIcon else tab.icon, contentDescription = null, tint = c.ink,
                        modifier = Modifier.size(24.dp))
                    Text(stringResource(tab.label), color = c.ink, maxLines = 1,
                        style = Badge.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium))
                }
            }
        }
    }
}
