package com.example.sipatkilatis.detection

import android.content.Context
import android.util.Log
import com.example.sipatkilatis.model.Flag
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Sensitivity
import com.example.sipatkilatis.model.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real detection engine: trusted contacts -> preprocess -> rules + URL checker + ML -> risk score.
 * All on-device. Target: under 1 second per message on a mid-range phone (timing is logged).
 */
class OnDeviceScamDetector(
    context: Context,
    private val sensitivity: () -> Sensitivity,
    private val trustedContacts: () -> List<String>,
) : ScamDetector {
    private val appContext = context.applicationContext
    private val rules: RulesEngine = RegexRulesEngine()
    private val urls: UrlChecker = OfflineUrlChecker(loadBlocklist(appContext))
    val ml = EnsembleClassifier(appContext)

    /** Load the models early (e.g. at app start) so the first scan is fast. */
    suspend fun warmUp() = ml.load()

    override suspend fun detect(text: String, sender: String?): ScanResult = withContext(Dispatchers.Default) {
        val start = System.nanoTime()

        // 1. Trusted contacts are never flagged
        if (sender != null && isTrusted(sender)) {
            return@withContext ScanResult(text, sender, Verdict.SAFE, 0f, 0f, 0f, 0f,
                listOf(Flag("trusted", "Sender is in your trusted contacts", "Nasa pinagkakatiwalaang contact mo ang nagpadala")),
                emptyList())
        }

        // 2. Rules + URL checks (fast), then the ML ensemble
        val input = Preprocessor.process(text)
        val r = rules.check(input)
        val u = urls.check(input)
        val mlStart = System.nanoTime()
        val m = ml.scamProbability(text)
        val mlMs = (System.nanoTime() - mlStart) / 1_000_000

        // 3. Combine
        val score = RiskScorer.score(m, u.result.score, r.score, u.blocklisted)
        val verdict = RiskScorer.verdict(score, sensitivity())
        val flags = buildList {
            addAll(u.result.flags)
            addAll(r.flags)
            if (m != null && m >= 0.5f) {
                val pct = (m * 100).toInt()
                add(Flag("ml", "The on-device AI finds this similar to known scam messages ($pct%)",
                    "Kahawig ito ng mga kilalang scam ayon sa AI sa phone mo ($pct%)"))
            }
        }

        val totalMs = (System.nanoTime() - start) / 1_000_000
        val (t, b) = ml.lastParts
        Log.d("SipatKilatis", "detect: ${totalMs} ms total (ml ${mlMs} ms) | score=%.2f ML=%s [roberta=%s baseline=%s] URL=%.2f rules=%.2f -> $verdict"
            .format(score, m?.let { "%.2f".format(it) }, t?.let { "%.2f".format(it) }, b?.let { "%.2f".format(it) }, u.result.score, r.score))

        ScanResult(
            text = text,
            sender = sender,
            verdict = verdict,
            score = score,
            mlScore = m ?: 0f,
            urlScore = u.result.score,
            rulesScore = r.score,
            // A SAFE verdict shows no warning list or highlights: minor signs alone are not worth alarming people
            flags = if (verdict == Verdict.SAFE) emptyList() else flags,
            highlights = if (verdict == Verdict.SAFE) emptyList() else RiskScorer.mergeRanges(r.ranges + u.result.ranges),
        )
    }

    /** Match by name (case-insensitive) or by the last 10 digits of a phone number. */
    private fun isTrusted(sender: String): Boolean {
        val digits = sender.filter { it.isDigit() }.takeLast(10)
        return trustedContacts().any { c ->
            c.equals(sender.trim(), ignoreCase = true) ||
                (digits.length >= 7 && c.filter { it.isDigit() }.takeLast(10) == digits)
        }
    }

    private fun loadBlocklist(context: Context): Set<String> = runCatching {
        context.assets.open("blocklist.txt").bufferedReader().readLines()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
    }.getOrElse { emptySet() }
}
