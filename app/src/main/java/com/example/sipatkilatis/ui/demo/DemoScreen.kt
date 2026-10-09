package com.example.sipatkilatis.ui.demo

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import com.example.sipatkilatis.detection.ExplanationSafety
import com.example.sipatkilatis.graph
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.ui.components.AppTopBar
import com.example.sipatkilatis.ui.components.MessageBlock
import com.example.sipatkilatis.ui.components.StatusBadge
import com.example.sipatkilatis.ui.theme.Sipat

private data class DemoRow(val msg: DemoMessage, val result: ScanResult, val ms: Long)

/**
 * Hidden demo mode (tap the version number in Settings 5 times): runs all demo messages through the real
 * detector and shows verdict, score, and timing. Results are NOT saved to history. Also logged as "DEMO ..."
 * lines, so it can be checked over adb.
 */
@Composable
fun DemoScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val rows = remember { mutableStateListOf<DemoRow>() }
    var aiStatus by remember { mutableStateOf<String?>(null) }   // AI explanation check (local LLM)

    LaunchedEffect(Unit) {
        val detector = context.graph.detector
        detector.warmUp()
        rows.clear()
        for (msg in DEMO_MESSAGES) {
            val start = System.currentTimeMillis()
            val result = detector.detect(msg.text, null)   // no sender: trusted contacts must not affect the demo
            val ms = System.currentTimeMillis() - start
            rows += DemoRow(msg, result, ms)
            Log.d("SipatKilatis", "DEMO ${if (result.verdict == msg.expected) "OK  " else "DIFF"} " +
                "expected=${msg.expected} got=${result.verdict} score=%.2f %d ms | %s".format(result.score, ms, msg.label))
        }
        val ok = rows.count { it.result.verdict == it.msg.expected }
        Log.d("SipatKilatis", "DEMO summary: $ok/${rows.size} as expected, avg ${rows.map { it.ms }.average().toLong()} ms")

        // AI explanation check: Gemma explains the first scam (proves the local LLM runs on this phone)
        val llm = context.graph.llmExplainer
        if (!llm.isInstalled) {
            aiStatus = "AI model not installed: template explanations are used."
        } else {
            aiStatus = "Loading the AI model on this phone…"
            val t0 = System.currentTimeMillis()
            val loaded = runCatching { llm.load() }.getOrDefault(false)
            val loadMs = System.currentTimeMillis() - t0
            aiStatus = if (!loaded) "AI model failed to load: template explanations are used." else {
                aiStatus = "Writing an explanation for message 1…"
                val start = System.currentTimeMillis()
                var text = ""
                val done = try {
                    withTimeoutOrNull(30_000) { llm.stream(rows.first().result, filipino = false).collect { text = it }; true }
                } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; null }
                val ms = System.currentTimeMillis() - start
                val safe = ExplanationSafety.isSafe(text)
                Log.d("SipatKilatis", "DEMO llm load=$loadMs ms gen=$ms ms done=${done == true} safe=$safe words=${text.split(Regex("\\s+")).size}")
                when {
                    done != true -> "AI explanation took over 30 s (template would be shown)."
                    !safe -> "AI answer failed the safety check (template would be shown). ${ms} ms"
                    else -> "AI explanation (load ${loadMs} ms, written in ${ms} ms):\n\n$text"
                }
            }
        }
    }

    Scaffold(containerColor = Sipat.colors.paper, topBar = { AppTopBar("Demo mode", onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val ok = rows.count { it.result.verdict == it.msg.expected }
                val avg = if (rows.isEmpty()) 0 else rows.map { it.ms }.average().toLong()
                MessageBlock {
                    if (rows.size < DEMO_MESSAGES.size) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 12.dp))
                            Text("Checking ${rows.size + 1} of ${DEMO_MESSAGES.size} on this phone…")
                        }
                    }
                    Text("$ok / ${rows.size} as expected · average ${avg} ms per message",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Offline, on-device. Not saved to history.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            aiStatus?.let { status ->
                item {
                    MessageBlock {
                        Text("AI explanation on this phone", style = MaterialTheme.typography.titleMedium)
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            itemsIndexed(rows) { i, row ->
                val match = row.result.verdict == row.msg.expected
                MessageBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${row.msg.label}", style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f))
                        Text(if (match) "✓" else "≠", color = Sipat.colors.ink,
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(row.msg.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(row.result.verdict)
                        Spacer(Modifier.width(12.dp))
                        Text("score %.2f · %d ms".format(row.result.score, row.ms), style = MaterialTheme.typography.bodyMedium)
                        if (!match) {
                            Spacer(Modifier.width(8.dp))
                            Text("(expected ${row.msg.expected.name.lowercase()})", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
