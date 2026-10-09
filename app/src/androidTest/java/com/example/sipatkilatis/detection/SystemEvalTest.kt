package com.example.sipatkilatis.detection

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sipatkilatis.model.Sensitivity
import com.example.sipatkilatis.model.Verdict
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Accuracy of the WHOLE app pipeline (rules + URL checker + RoBERTa/baseline ensemble + risk scorer), not just
 * one model, on the held-out real PH test split (split == test in messages.csv). Normal sensitivity, no sender.
 *
 * The dataset is not in git, so push it to the phone first (see README → Tests):
 *   adb push ml/data/processed/messages.csv /sdcard/Android/data/com.example.sipatkilatis/files/eval/messages.csv
 * Writes the report next to it as system_eval.txt (and to logcat, tag SipatKilatis, "EVAL").
 */
@RunWith(AndroidJUnit4::class)
class SystemEvalTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fullPipelineOnTestSet() = runBlocking {
        val dir = File(app.getExternalFilesDir(null), "eval").apply { mkdirs() }
        val csv = File(dir, "messages.csv")
        assumeTrue("dataset not on the phone: $csv", csv.exists())

        val rows = parseCsv(csv.readText(Charsets.UTF_8))
        val header = rows.first()
        val iText = header.indexOf("text"); val iLabel = header.indexOf("label"); val iSplit = header.indexOf("split")
        val test = rows.drop(1).filter { it.size > iSplit && it[iSplit] == "test" }.map { it[iText] to (it[iLabel] == "scam") }

        val detector = OnDeviceScamDetector(app, { Sensitivity.NORMAL }, { emptyList() })
        detector.warmUp()

        val scamOnly = Counts(); val anyWarning = Counts(); val mlOnly = Counts()
        var bothModels = 0
        val mistakes = StringBuilder()
        val start = System.nanoTime()
        for ((text, isScam) in test) {
            val r = detector.detect(text)
            val (roberta, baseline) = detector.ml.lastParts
            if (roberta != null && baseline != null) bothModels++
            scamOnly.add(isScam, r.verdict == Verdict.SCAM)
            anyWarning.add(isScam, r.verdict != Verdict.SAFE)
            mlOnly.add(isScam, r.mlScore >= 0.5f)
            if (isScam == (r.verdict == Verdict.SAFE)) {
                mistakes.appendLine("  %s -> %-10s score=%.2f ML=%.2f URL=%.2f rules=%.2f | %s".format(
                    if (isScam) "SCAM" else "HAM ", r.verdict, r.score, r.mlScore, r.urlScore, r.rulesScore,
                    text.replace(Regex("\\s+"), " ").take(110)))
            }
        }
        val avgMs = (System.nanoTime() - start) / 1_000_000.0 / test.size

        val report = buildString {
            appendLine("Full pipeline on the held-out real PH test set: n=${test.size} " +
                "(${test.count { it.second }} scam, ${test.count { !it.second }} legit), Normal sensitivity")
            appendLine("Both ONNX models loaded for $bothModels / ${test.size} messages; avg %.0f ms per message".format(avgMs))
            appendLine(anyWarning.line("App warns (SUSPICIOUS or SCAM)"))
            appendLine(scamOnly.line("App says SCAM"))
            appendLine(mlOnly.line("ML ensemble only (P >= 0.5)"))
            appendLine("Mistakes (scam shown as SAFE, or legit shown with a warning):")
            append(mistakes)
        }
        File(dir, "system_eval.txt").writeText(report, Charsets.UTF_8)
        report.lines().forEach { Log.d("SipatKilatis", "EVAL $it") }

        assertTrue("ONNX models missing: copy them into app/src/main/assets", bothModels == test.size)
    }

    private class Counts {
        var tp = 0; var fp = 0; var fn = 0; var tn = 0
        fun add(isScam: Boolean, flagged: Boolean) {
            when { isScam && flagged -> tp++; isScam -> fn++; flagged -> fp++; else -> tn++ }
        }
        fun line(name: String): String {
            val p = tp.toDouble() / (tp + fp); val r = tp.toDouble() / (tp + fn)
            return "%-32s precision=%.3f recall=%.3f F1=%.3f FPR=%.3f  [TP=%d FP=%d FN=%d TN=%d]"
                .format(name, p, r, 2 * p * r / (p + r), fp.toDouble() / (fp + tn), tp, fp, fn, tn)
        }
    }

    /** Minimal CSV reader: commas, double-quoted fields, "" escapes (same as RuleFloorReportTest). */
    private fun parseCsv(s: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                quoted && c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == ',' -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++
                    row += field.toString(); field.clear()
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}
