package com.example.sipatkilatis.detection

import com.example.sipatkilatis.model.Flag
import com.example.sipatkilatis.model.ScanResult

// Interfaces for the on-device detection engine. Real implementations arrive in phase 4.

/** One entry point the UI calls. Runs fully on-device. */
interface ScamDetector {
    suspend fun detect(text: String, sender: String? = null): ScanResult
}

/** Weighted regex rules for scam phrases (English + Filipino). Returns a 0..1 score and flags. */
interface RulesEngine {
    fun check(normalizedText: String): Pair<Float, List<Flag>>
}

/** Offline URL checks: blocklist, lookalike brand domains, shorteners, raw IPs, odd TLDs. */
interface UrlChecker {
    fun check(urls: List<String>): Pair<Float, List<Flag>>
}

/** ONNX model returning P(scam) in 0..1. */
interface MlClassifier {
    suspend fun scamProbability(text: String): Float
}

/** Writes a short explanation for SUSPICIOUS / SCAM results (local LLM or template). */
interface Explainer {
    suspend fun explain(result: ScanResult, filipino: Boolean): String
}
