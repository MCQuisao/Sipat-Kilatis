package com.example.sipatkilatis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.sipatkilatis.model.Verdict

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Sky,
    onPrimaryContainer = Navy,
    secondary = Mango,
    onSecondary = Color(0xFF2B1D00),
    secondaryContainer = Sky,          // selected chips / segmented buttons use brand blue, not default lavender
    onSecondaryContainer = Navy,
    background = Color(0xFFF7F8FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EDF5),
    onSurface = Color(0xFF15181E),
    onSurfaceVariant = Color(0xFF3F4652),
)

private val DarkColors = darkColorScheme(
    primary = NavyLight,
    onPrimary = Color(0xFF0A1F45),
    primaryContainer = Color(0xFF233F78),
    onPrimaryContainer = Sky,
    secondary = Mango,
    onSecondary = Color(0xFF2B1D00),
)

/** Brand colors only (no dynamic wallpaper colors) so verdict colors always read the same. */
@Composable
fun SipatKilatisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}

/** Strong color for a verdict (chips, bars, icons). */
fun Verdict.color(): Color = when (this) {
    Verdict.SAFE -> SafeGreen
    Verdict.SUSPICIOUS -> SuspiciousAmber
    Verdict.SCAM -> ScamRed
}

/** Light background tint for a verdict. */
fun Verdict.tint(): Color = when (this) {
    Verdict.SAFE -> SafeTint
    Verdict.SUSPICIOUS -> SuspiciousTint
    Verdict.SCAM -> ScamTint
}
