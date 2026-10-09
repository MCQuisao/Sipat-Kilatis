package com.example.sipatkilatis.detection

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Measures the rule-based minimum scores on the real dataset (ml/data/processed/messages.csv):
 * how many HAM messages the rules alone would push to SUSPICIOUS (rules >= 0.4) or SCAM (rules >= 0.6),
 * and how many SCAM messages they catch. Writes app/build/reports/rule_floor_report.txt.
 * Skipped when the dataset is not on this machine (it is not in git).
 */
class RuleFloorReportTest {
    private val rules = RegexRulesEngine()

    @Test
    fun report() {
        val csv = File("../ml/data/processed/messages.csv")
        assumeTrue("dataset not present", csv.exists())
        val rows = parseCsv(csv.readText(Charsets.UTF_8))
        val header = rows.first()
        val iText = header.indexOf("text"); val iLabel = header.indexOf("label"); val iSource = header.indexOf("source")

        val out = StringBuilder()
        for (source in listOf("real_ph", "synthetic", "uci")) {
            for (label in listOf("ham", "scam")) {
                val texts = rows.drop(1).filter { it[iSource] == source && it[iLabel] == label }.map { it[iText] }
                val scores = texts.map { it to rules.check(Preprocessor.process(it)) }
                val medium = scores.filter { it.second.score >= RiskScorer.RULES_MEDIUM }
                val strong = scores.filter { it.second.score >= RiskScorer.RULES_STRONG }
                out.appendLine("%-9s %-4s n=%-5d rules>=0.4 (>= SUSPICIOUS): %4d (%5.1f%%)   rules>=0.6 (SCAM): %4d (%5.1f%%)"
                    .format(source, label, texts.size, medium.size, 100.0 * medium.size / texts.size,
                        strong.size, 100.0 * strong.size / texts.size))
                if (label == "ham" && source == "real_ph") {
                    out.appendLine("  real_ph HAM messages the rules would flag:")
                    medium.sortedByDescending { it.second.score }.forEach { (t, r) ->
                        out.appendLine("    rules=%.2f %s | %s".format(r.score, r.flags.map { it.id }, t.take(110)))
                    }
                }
            }
        }
        File("build/reports").mkdirs()
        File("build/reports/rule_floor_report.txt").writeText(out.toString(), Charsets.UTF_8)
    }

    /** Minimal CSV reader: commas, double-quoted fields, "" escapes. */
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
