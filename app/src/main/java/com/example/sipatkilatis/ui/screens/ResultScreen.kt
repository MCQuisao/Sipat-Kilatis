package com.example.sipatkilatis.ui.screens

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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.sipatkilatis.R
import com.example.sipatkilatis.model.Feedback
import com.example.sipatkilatis.model.ScanRecord
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Verdict
import com.example.sipatkilatis.ui.AppLanguage
import com.example.sipatkilatis.ui.ExplanationUi
import com.example.sipatkilatis.ui.components.AppTopBar
import com.example.sipatkilatis.ui.components.BulletItem
import com.example.sipatkilatis.ui.components.HighlightedText
import com.example.sipatkilatis.ui.components.RiskBar
import com.example.sipatkilatis.ui.components.SectionCard
import com.example.sipatkilatis.ui.components.VerdictChip
import com.example.sipatkilatis.ui.components.verdictIcon
import com.example.sipatkilatis.ui.theme.SafeGreen
import com.example.sipatkilatis.ui.theme.ScamRed
import com.example.sipatkilatis.ui.theme.color
import com.example.sipatkilatis.ui.theme.tint

private val OnTint = Color(0xFF15181E)   // dark text on the light verdict tints (also in dark mode)

/** Verdict, risk bar, highlighted message, explanation, and what to do now. */
@Composable
fun ResultScreen(
    result: ScanResult,
    explanation: ExplanationUi,
    record: ScanRecord?,
    onMarkSafe: () -> Unit,
    onReport: () -> Unit,
    onScanAnother: () -> Unit,
    onBack: () -> Unit,
) {
    val pct = { v: Float -> (v * 100).toInt() }
    Scaffold(topBar = { AppTopBar(stringResource(R.string.result_title), onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Verdict header
            Card(
                colors = CardDefaults.cardColors(containerColor = result.verdict.tint(), contentColor = OnTint),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(verdictIcon(result.verdict), contentDescription = null, tint = result.verdict.color(),
                            modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(headline(result.verdict), style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f))
                    }
                    VerdictChip(result.verdict, large = true)
                    Text(stringResource(R.string.result_risk, pct(result.score)), style = MaterialTheme.typography.titleMedium)
                    RiskBar(result.score, result.verdict)
                    Text(stringResource(R.string.result_parts, pct(result.mlScore), pct(result.urlScore), pct(result.rulesScore)),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (result.isPreview) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.result_preview_note), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionCard(title = stringResource(R.string.result_message)) {
                HighlightedText(result.text, result.highlights)
                if (result.highlights.isNotEmpty()) {
                    Text(stringResource(R.string.result_highlight_note), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Explanation: template shown instantly; the local LLM's explanation replaces it when ready (streamed).
            SectionCard(title = stringResource(R.string.result_why)) {
                Text(explanation.text, style = MaterialTheme.typography.bodyLarge)
                if (explanation.generating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.result_ai_generating), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (explanation.fromAi) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.result_ai_written), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // The AI text replaces the template's list, so keep the detector's exact warning signs visible too
            if (explanation.fromAi && result.flags.isNotEmpty()) {
                val filipino = AppLanguage.current() == AppLanguage.FILIPINO
                SectionCard(title = stringResource(R.string.result_signs)) {
                    result.flags.forEach { BulletItem(if (filipino) it.reasonFil else it.reasonEn) }
                }
            }

            // Feedback: saved in the on-device database (and in "Export my reports")
            if (record != null) {
                SectionCard(title = stringResource(R.string.result_feedback_title)) {
                    when (record.feedback) {
                        Feedback.MARKED_SAFE -> Text(
                            stringResource(if (record.result?.sender != null) R.string.result_marked_safe
                                           else R.string.result_marked_safe_no_sender),
                            style = MaterialTheme.typography.bodyLarge, color = SafeGreen)
                        Feedback.REPORTED -> Text(stringResource(R.string.result_reported),
                            style = MaterialTheme.typography.bodyLarge, color = ScamRed)
                        Feedback.NONE -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onMarkSafe, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.result_mark_safe))
                            }
                            OutlinedButton(onClick = onReport, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.result_report))
                            }
                        }
                    }
                }
            }

            SectionCard(title = stringResource(R.string.result_todo)) {
                todoItems(result.verdict).forEach { BulletItem(stringResource(it)) }
            }

            OutlinedButton(onClick = onScanAnother, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.result_scan_another))
            }
        }
    }
}

@Composable
private fun headline(verdict: Verdict) = stringResource(
    when (verdict) {
        Verdict.SAFE -> R.string.result_headline_safe
        Verdict.SUSPICIOUS -> R.string.result_headline_suspicious
        Verdict.SCAM -> R.string.result_headline_scam
    }
)

private fun todoItems(verdict: Verdict): List<Int> = when (verdict) {
    Verdict.SCAM -> listOf(R.string.todo_scam_1, R.string.todo_scam_2, R.string.todo_scam_3,
        R.string.todo_scam_4, R.string.todo_scam_5)
    Verdict.SUSPICIOUS -> listOf(R.string.todo_suspicious_1, R.string.todo_suspicious_2, R.string.todo_suspicious_3)
    Verdict.SAFE -> listOf(R.string.todo_safe_1, R.string.todo_safe_2)
}
