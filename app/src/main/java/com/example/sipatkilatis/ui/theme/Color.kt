package com.example.sipatkilatis.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.sipatkilatis.model.Verdict

/** One status color set: [strong] for icons / gauges, [container] for soft fills, [onContainer] for text on it. */
@Immutable
data class StatusColors(val strong: Color, val container: Color, val onContainer: Color)

/**
 * Neutral app colors + three status colors (see docs/DESIGN.md).
 * Everything is neutral EXCEPT verdicts: Safe = green, Careful = amber, Scam = red.
 * Verdicts are never told by color alone: they always have their own icon and word too.
 */
@Immutable
data class SipatColors(
    val paper: Color,    // app background
    val card: Color,     // cards, nav bar, sheets
    val wash: Color,     // quiet fills: search field, filter track, active nav pill
    val line: Color,     // card borders, dividers
    val mute: Color,     // disabled / placeholder
    val subtle: Color,   // secondary text
    val ink: Color,      // primary text, icons, primary buttons
    val inkSoft: Color,  // pressed state on ink
    val safe: StatusColors,
    val careful: StatusColors,
    val scam: StatusColors,
) {
    fun status(verdict: Verdict) = when (verdict) {
        Verdict.SAFE -> safe
        Verdict.SUSPICIOUS -> careful
        Verdict.SCAM -> scam
    }
}

val LightTokens = SipatColors(
    paper = Color(0xFFF4F5F7),
    card = Color(0xFFFFFFFF),
    wash = Color(0xFFE9EBEF),
    line = Color(0xFFE2E4E9),
    mute = Color(0xFF9CA0A8),
    subtle = Color(0xFF5F646D),
    ink = Color(0xFF14161A),
    inkSoft = Color(0xFF2A2D33),
    safe = StatusColors(Color(0xFF16A34A), Color(0xFFDCFCE7), Color(0xFF14532D)),
    careful = StatusColors(Color(0xFFD97706), Color(0xFFFEF3C7), Color(0xFF78350F)),
    scam = StatusColors(Color(0xFFDC2626), Color(0xFFFEE2E2), Color(0xFF7F1D1D)),
)

val DarkTokens = SipatColors(
    paper = Color(0xFF0F1114),
    card = Color(0xFF1A1D22),
    wash = Color(0xFF262A31),
    line = Color(0xFF2C3038),
    mute = Color(0xFF6B7079),
    subtle = Color(0xFFA3A8B1),
    ink = Color(0xFFF1F2F4),
    inkSoft = Color(0xFFD9DBDF),
    safe = StatusColors(Color(0xFF4ADE80), Color(0xFF12301F), Color(0xFFBBF7D0)),
    careful = StatusColors(Color(0xFFFBBF24), Color(0xFF3A2A08), Color(0xFFFDE68A)),
    scam = StatusColors(Color(0xFFF87171), Color(0xFF3D1414), Color(0xFFFECACA)),
)

val LocalSipatColors = staticCompositionLocalOf { LightTokens }
