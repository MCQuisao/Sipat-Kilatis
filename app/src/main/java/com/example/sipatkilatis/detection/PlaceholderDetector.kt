package com.example.sipatkilatis.detection

import com.example.sipatkilatis.model.Flag
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Verdict
import kotlinx.coroutines.delay

/**
 * TEMPORARY stand-in so the screens can be tried end to end. It only counts a few keywords.
 * Replaced by the real engine (rules + URL checker + ONNX model) in phase 4.
 */
class PlaceholderDetector : ScamDetector {

    private data class Keyword(val regex: Regex, val flag: Flag)

    private val keywords = listOf(
        Keyword(Regex("""\b(i-?verify|verify|i-?update)\b""", RegexOption.IGNORE_CASE),
            Flag("verify", "Asks you to verify or update your account", "Pinapa-verify o pinapa-update ang account mo")),
        Keyword(Regex("""\b(otp|one[- ]time pin|mpin)\b""", RegexOption.IGNORE_CASE),
            Flag("otp", "Mentions your OTP or PIN", "Binabanggit ang OTP o PIN mo")),
        Keyword(Regex("""\b(nanalo|panalo|won|winner|claim|prize|premyo)\b""", RegexOption.IGNORE_CASE),
            Flag("prize", "Promises a prize or reward", "Nangangako ng premyo o gantimpala")),
        Keyword(Regex("""\b(agad|ngayon na|urgent|within 24 hours|last chance)\b""", RegexOption.IGNORE_CASE),
            Flag("urgency", "Pushes you to act right away", "Minamadali kang kumilos")),
        Keyword(Regex("""(https?://\S+|\b[\w-]+\.(xyz|top|click|icu|ly)\b\S*)""", RegexOption.IGNORE_CASE),
            Flag("link", "Contains a link", "May kasamang link")),
        Keyword(Regex("""\b(locked|suspended|blocked|na-?lock)\b""", RegexOption.IGNORE_CASE),
            Flag("locked", "Says your account is locked or suspended", "Sinasabing naka-lock o suspendido ang account mo")),
    )

    override suspend fun detect(text: String, sender: String?): ScanResult {
        delay(400)   // feels like work is happening; the real engine targets < 1 s
        val hits = keywords.mapNotNull { k -> k.regex.findAll(text).toList().takeIf { it.isNotEmpty() }?.let { k to it } }
        val score = (hits.size * 0.22f).coerceAtMost(1f)
        val verdict = when {
            score < 0.4f -> Verdict.SAFE
            score < 0.7f -> Verdict.SUSPICIOUS
            else -> Verdict.SCAM
        }
        return ScanResult(
            text = text,
            sender = sender,
            verdict = verdict,
            score = score,
            mlScore = score,
            urlScore = if (hits.any { it.first.flag.id == "link" }) 0.5f else 0f,
            rulesScore = score,
            flags = hits.map { it.first.flag },
            highlights = hits.flatMap { (_, matches) -> matches.map { it.range } }.sortedBy { it.first },
            isPreview = true,
        )
    }
}
