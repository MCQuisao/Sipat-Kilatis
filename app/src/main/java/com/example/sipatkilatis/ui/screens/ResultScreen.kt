package com.example.sipatkilatis.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.example.sipatkilatis.R
import com.example.sipatkilatis.detection.MessageMarks
import com.example.sipatkilatis.detection.RiskSpan
import com.example.sipatkilatis.model.Feedback
import com.example.sipatkilatis.model.ScanRecord
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Verdict
import com.example.sipatkilatis.ui.AppLanguage
import com.example.sipatkilatis.ui.ExplanationUi
import com.example.sipatkilatis.ui.components.AppTopBar
import com.example.sipatkilatis.ui.components.CheckRow
import com.example.sipatkilatis.ui.components.ConfirmSheet
import com.example.sipatkilatis.ui.components.ContentCard
import com.example.sipatkilatis.ui.components.HighlightedMessage
import com.example.sipatkilatis.ui.components.InfoSheet
import com.example.sipatkilatis.ui.components.PrimaryButton
import com.example.sipatkilatis.ui.components.SecondaryButton
import com.example.sipatkilatis.ui.components.TonalButton
import com.example.sipatkilatis.ui.components.VerdictCard
import com.example.sipatkilatis.ui.theme.BodyL
import com.example.sipatkilatis.ui.theme.BodyM
import com.example.sipatkilatis.ui.theme.Label
import com.example.sipatkilatis.ui.theme.Radius
import com.example.sipatkilatis.ui.theme.Sipat
import com.example.sipatkilatis.ui.theme.Space

/**
 * Hero screen. Verdict card with gauge, then cards: what to do now, the message (highlighted),
 * why we think so (template first, then the on-phone AI text), then the actions.
 */
@Composable
fun ResultScreen(
    result: ScanResult,
    explanation: ExplanationUi,
    record: ScanRecord?,
    marks: MessageMarks,
    thresholds: Pair<Float, Float>,
    onMarkSafe: () -> Unit,
    onReport: () -> Unit,
    onScanAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val c = Sipat.colors
    val s = c.status(result.verdict)
    val context = LocalContext.current
    val filipino = AppLanguage.current() == AppLanguage.FILIPINO
    var openSpan by remember { mutableStateOf<RiskSpan?>(null) }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }   // "safe" / "report"
    val copied = stringResource(R.string.result_copied)

    Scaffold(containerColor = c.paper, topBar = { AppTopBar(stringResource(R.string.result_title), onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = Space.screen).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            VerdictCard(result.verdict, result.score, thresholds)

            ContentCard(title = stringResource(R.string.result_todo)) {
                todoItems(result.verdict).forEach { (icon, text) ->
                    CheckRow(icon, stringResource(text), tint = s.strong, tileColor = s.container)
                }
            }

            ContentCard(title = stringResource(R.string.result_message)) {
                Column(Modifier.fillMaxWidth().background(c.wash, Radius.bubble).padding(Space.l)) {
                    HighlightedMessage(result.text, marks, onSpanClick = { openSpan = it })
                }
                if (marks.spans.isNotEmpty()) Hint(Icons.Rounded.TouchApp, stringResource(R.string.result_highlight_note))
                if (marks.lookalikes.isNotEmpty()) Hint(Icons.Rounded.Search, stringResource(R.string.lookalike_hint))
            }

            ContentCard(title = stringResource(R.string.result_why)) {
                // Template first; the AI text cross-fades in when ready (never blocks the screen)
                Crossfade(targetState = explanation.text, label = "explanation") { text ->
                    Text(text, style = BodyL, color = c.ink)
                }
                if (explanation.generating) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = c.ink, trackColor = c.wash)
                    Text(stringResource(R.string.result_ai_generating), style = BodyM, color = c.subtle)
                } else if (explanation.fromAi) {
                    Hint(Icons.Outlined.AutoAwesome, stringResource(R.string.result_ai_written))
                }
            }

            // The AI replaces the template's list, so keep the detector's exact warning signs visible
            if (explanation.fromAi && !explanation.generating && result.flags.isNotEmpty()) {
                ContentCard(title = stringResource(R.string.result_signs)) {
                    result.flags.forEach {
                        CheckRow(Icons.Rounded.Check, if (filipino) it.reasonFil else it.reasonEn, tint = s.strong, tileColor = s.container)
                    }
                }
            }

            // Feedback: done state, or the two choices
            when (record?.feedback) {
                Feedback.MARKED_SAFE -> DoneBanner(stringResource(if (result.sender != null) R.string.result_marked_safe
                    else R.string.result_marked_safe_no_sender), Icons.Rounded.CheckCircle, c.safe.strong, c.safe.container)
                Feedback.REPORTED -> DoneBanner(stringResource(R.string.result_reported), Icons.Rounded.Flag,
                    c.scam.strong, c.scam.container)
                else -> if (record != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                        TonalButton(stringResource(R.string.result_mark_safe), onClick = { confirm = "safe" },
                            colors = c.safe, modifier = Modifier.weight(1f))
                        TonalButton(stringResource(R.string.result_report), onClick = { confirm = "report" },
                            colors = c.scam, modifier = Modifier.weight(1f))
                    }
                }
            }
            SecondaryButton(stringResource(R.string.result_copy), icon = Icons.Rounded.ContentCopy, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("message", result.text))
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                })
            PrimaryButton(stringResource(R.string.result_scan_another), onClick = onScanAnother, icon = Icons.Rounded.Search)
            if (result.durationMs > 0) {
                Row(Modifier.fillMaxWidth().padding(bottom = Space.l), horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.mute, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Space.xs))
                    Text(stringResource(R.string.result_checked_in, "%.1f".format(result.durationMs / 1000.0)),
                        style = BodyM, color = c.mute)
                }
            } else {
                Spacer(Modifier.height(Space.l))
            }
        }
    }

    openSpan?.let { span ->
        val reason = if (filipino) span.flag.reasonFil else span.flag.reasonEn
        val hasLookalike = marks.lookalikes.any { it in span.range }
        InfoSheet(
            title = stringResource(R.string.highlight_why_title),
            body = if (hasLookalike) reason + "\n\n" + stringResource(R.string.lookalike_hint) else reason,
            onDismiss = { openSpan = null },
        )
    }
    when (confirm) {
        "safe" -> ConfirmSheet(stringResource(R.string.confirm_safe_title), stringResource(R.string.confirm_safe_body),
            stringResource(R.string.result_mark_safe), onConfirm = onMarkSafe, onDismiss = { confirm = null })
        "report" -> ConfirmSheet(stringResource(R.string.confirm_report_title), stringResource(R.string.confirm_report_body),
            stringResource(R.string.result_report), onConfirm = onReport, onDismiss = { confirm = null })
    }
}

/** Small grey hint line with an icon. */
@Composable
private fun Hint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Sipat.colors.subtle, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.width(Space.s))
        Text(text, style = BodyM, color = Sipat.colors.subtle)
    }
}

/** Shown after the user marked the message safe or reported it. */
@Composable
private fun DoneBanner(text: String, icon: ImageVector, tint: androidx.compose.ui.graphics.Color, bg: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().background(bg, Radius.card).padding(Space.l), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(Space.m))
        Text(text, style = Label, color = Sipat.colors.ink)
    }
}

/** Most important first. */
private fun todoItems(verdict: Verdict): List<Pair<ImageVector, Int>> = when (verdict) {
    Verdict.SCAM -> listOf(Icons.Rounded.Block to R.string.todo_scam_1, Icons.Rounded.Block to R.string.todo_scam_2,
        Icons.Rounded.DeleteOutline to R.string.todo_scam_4, Icons.Rounded.Flag to R.string.todo_scam_5)
    Verdict.SUSPICIOUS -> listOf(Icons.Rounded.Block to R.string.todo_suspicious_1, Icons.Rounded.Phone to R.string.todo_suspicious_2,
        Icons.Rounded.Block to R.string.todo_suspicious_3)
    Verdict.SAFE -> listOf(Icons.Rounded.Check to R.string.todo_safe_1, Icons.Rounded.Check to R.string.todo_safe_2)
}
