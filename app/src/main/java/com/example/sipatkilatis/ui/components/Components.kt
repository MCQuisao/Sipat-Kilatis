package com.example.sipatkilatis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.model.Verdict
import com.example.sipatkilatis.ui.theme.HighlightYellow
import com.example.sipatkilatis.ui.theme.color

/** Top bar with an optional back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null) {
    CenterAlignedTopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
    )
}

@Composable
fun verdictLabel(verdict: Verdict): String = stringResource(
    when (verdict) {
        Verdict.SAFE -> R.string.verdict_safe
        Verdict.SUSPICIOUS -> R.string.verdict_suspicious
        Verdict.SCAM -> R.string.verdict_scam
    }
)

fun verdictIcon(verdict: Verdict): ImageVector = when (verdict) {
    Verdict.SAFE -> Icons.Filled.CheckCircle
    Verdict.SUSPICIOUS -> Icons.Filled.Warning
    Verdict.SCAM -> Icons.Filled.Dangerous
}

/** Colored pill with icon + label: Safe (green), Suspicious (amber), Scam (red). */
@Composable
fun VerdictChip(verdict: Verdict, large: Boolean = false) {
    Surface(color = verdict.color(), contentColor = Color.White, shape = RoundedCornerShape(50)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = if (large) 16.dp else 10.dp, vertical = if (large) 8.dp else 4.dp),
        ) {
            Icon(verdictIcon(verdict), contentDescription = null, modifier = Modifier.size(if (large) 22.dp else 16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                verdictLabel(verdict),
                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Horizontal bar showing a 0..1 risk score in the verdict's color. */
@Composable
fun RiskBar(score: Float, verdict: Verdict) {
    LinearProgressIndicator(
        progress = { score.coerceIn(0f, 1f) },
        color = verdict.color(),
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round,
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(6.dp)),
    )
}

/** The message text with risky character ranges highlighted. */
@Composable
fun HighlightedText(text: String, highlights: List<IntRange>) {
    val annotated = buildAnnotatedString {
        var pos = 0
        for (range in highlights) {
            if (range.first < pos || range.last >= text.length) continue   // skip overlaps / bad ranges
            append(text.substring(pos, range.first))
            withStyle(SpanStyle(background = HighlightYellow, color = Color(0xFF15181E), fontWeight = FontWeight.Bold)) {
                append(text.substring(range.first, range.last + 1))
            }
            pos = range.last + 1
        }
        append(text.substring(pos))
    }
    Text(annotated, style = MaterialTheme.typography.bodyLarge)
}

/** Simple white card with a title, used for most sections. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** Small "Offline protected" badge shown on the home screen. */
@Composable
fun OfflineBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondary)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(Icons.Filled.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(stringResource(R.string.home_offline_badge), color = MaterialTheme.colorScheme.onSecondary,
            style = MaterialTheme.typography.labelLarge)
    }
}

/** A bullet line, used in "What to do now" lists. */
@Composable
fun BulletItem(text: String) {
    Row {
        Text("•  ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
