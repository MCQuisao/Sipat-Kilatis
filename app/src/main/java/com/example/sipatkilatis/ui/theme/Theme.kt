package com.example.sipatkilatis.ui.theme

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.example.sipatkilatis.model.Appearance

/**
 * Every Material 3 role mapped to a neutral token, so no default purple / tonal color can leak in.
 * Only "error" uses a status color (Scam red). Dynamic (wallpaper) color is never used.
 */
private fun scheme(t: SipatColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = t.ink, onPrimary = t.paper,
        primaryContainer = t.wash, onPrimaryContainer = t.ink,
        inversePrimary = t.paper,
        secondary = t.ink, onSecondary = t.paper,
        secondaryContainer = t.wash, onSecondaryContainer = t.ink,
        tertiary = t.ink, onTertiary = t.paper,
        tertiaryContainer = t.wash, onTertiaryContainer = t.ink,
        background = t.paper, onBackground = t.ink,
        surface = t.paper, onSurface = t.ink,
        surfaceVariant = t.wash, onSurfaceVariant = t.subtle,
        surfaceTint = Color.Transparent,
        inverseSurface = t.ink, inverseOnSurface = t.paper,
        error = t.scam.strong, onError = t.card,
        errorContainer = t.scam.container, onErrorContainer = t.scam.onContainer,
        outline = t.mute, outlineVariant = t.line,
        scrim = t.ink.copy(alpha = 0.4f),
        surfaceBright = t.card, surfaceDim = t.wash,
        surfaceContainerLowest = t.card, surfaceContainerLow = t.card,
        surfaceContainer = t.card, surfaceContainerHigh = t.card, surfaceContainerHighest = t.wash,
    )
}

@Composable
fun SipatKilatisTheme(
    appearance: Appearance = Appearance.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (appearance) {
        Appearance.LIGHT -> false
        Appearance.DARK -> true
        Appearance.SYSTEM -> isSystemInDarkTheme()
    }
    val tokens = if (dark) DarkTokens else LightTokens

    // Transparent edge-to-edge system bars; their icons follow the app theme (not only the system setting)
    val activity = LocalContext.current as? Activity
    SideEffect {
        (activity as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                             else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
            navigationBarStyle = if (dark) SystemBarStyle.dark(Color.Transparent.toArgb())
                                 else SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb()),
        )
    }

    CompositionLocalProvider(LocalSipatColors provides tokens) {
        MaterialTheme(colorScheme = scheme(tokens, dark), typography = Typography, shapes = AppShapes, content = content)
    }
}

/** Shortcut: Sipat.colors.ink, Sipat.colors.status(verdict), ... */
object Sipat {
    val colors: SipatColors @Composable get() = LocalSipatColors.current
}
