package com.example.sipatkilatis.detection

import com.example.sipatkilatis.model.Flag
import com.example.sipatkilatis.model.ScanResult

// Interfaces for the on-device detection engine. Everything runs on the phone; nothing is uploaded.

/** One entry point the UI calls. */
interface ScamDetector {
    suspend fun detect(text: String, sender: String? = null): ScanResult
}

/** Result of one check: a 0..1 score, the reasons, and which characters of the original text to highlight. */
data class CheckResult(
    val score: Float,
    val flags: List<Flag>,
    val ranges: List<IntRange>,
)

/** Weighted regex rules for scam phrases (English + Filipino). */
interface RulesEngine {
    fun check(input: PreprocessedText): CheckResult
}

/** Offline URL checks. [blocklisted] = a link is on the known scam list (forces score >= 0.9). */
data class UrlCheckResult(val result: CheckResult, val blocklisted: Boolean)

interface UrlChecker {
    fun check(input: PreprocessedText): UrlCheckResult
}

/** ONNX model(s) returning P(scam) in 0..1, or null if no model could be loaded. */
interface MlClassifier {
    suspend fun scamProbability(text: String): Float?
}

/** Writes a short explanation for SUSPICIOUS / SCAM results (local LLM or template). Phase 6. */
interface Explainer {
    suspend fun explain(result: ScanResult, filipino: Boolean): String
}
