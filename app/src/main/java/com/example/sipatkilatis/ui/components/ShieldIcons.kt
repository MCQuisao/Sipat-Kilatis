package com.example.sipatkilatis.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.model.Verdict

/**
 * Verdict icons: the same shield with a different mark (check / exclamation / X), so the verdict reads by
 * shape, not color. Drawn as line vectors; Icon(tint = ...) colors them with a token.
 */
object ShieldIcons {
    private fun shield(name: String, mark: PathBuilder.() -> Unit) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            val stroke = SolidColor(Color.Black)
            // Shield outline
            path(
                fill = null, stroke = stroke, strokeLineWidth = 1.8f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 2.5f); lineTo(4.5f, 5.5f); verticalLineTo(11f)
                curveTo(4.5f, 15.8f, 7.6f, 20.1f, 12f, 21.5f)
                curveTo(16.4f, 20.1f, 19.5f, 15.8f, 19.5f, 11f)
                verticalLineTo(5.5f); close()
            }
            // The mark inside the shield
            path(
                stroke = stroke,
                strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                pathBuilder = mark,
            )
        }.build()

    val Safe: ImageVector = shield("ShieldCheck", { moveTo(8.5f, 12f); lineTo(11f, 14.5f); lineTo(15.5f, 9.5f) })

    val Careful: ImageVector = shield("ShieldExclamation", {
        moveTo(12f, 7.5f); verticalLineTo(12.5f)
        moveTo(12f, 16f); lineTo(12f, 16.1f)
    })

    val Scam: ImageVector = shield("ShieldX", { moveTo(9f, 9f); lineTo(15f, 15f); moveTo(15f, 9f); lineTo(9f, 15f) })

    fun of(verdict: Verdict) = when (verdict) {
        Verdict.SAFE -> Safe
        Verdict.SUSPICIOUS -> Careful
        Verdict.SCAM -> Scam
    }
}
