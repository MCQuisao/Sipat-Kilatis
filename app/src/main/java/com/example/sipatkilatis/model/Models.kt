package com.example.sipatkilatis.model

/** Final verdict shown to the user. Thresholds live in detection (see CLAUDE.md). */
enum class Verdict { SAFE, SUSPICIOUS, SCAM }

/** How strict the detector is. Shifts the SUSPICIOUS / SCAM thresholds (wired up in phase 4/7). */
enum class Sensitivity { LOW, NORMAL, HIGH }

/** Where a message came from. */
enum class MessageSource { SMS, NOTIFICATION, MANUAL }

/** One reason the detector raised its score, with text in both languages. */
data class Flag(
    val id: String,
    val reasonEn: String,
    val reasonFil: String,
)

/** Output of one scan. Score parts are each 0..1. */
data class ScanResult(
    val text: String,
    val sender: String?,
    val verdict: Verdict,
    val score: Float,
    val mlScore: Float,
    val urlScore: Float,
    val rulesScore: Float,
    val flags: List<Flag>,
    val highlights: List<IntRange>,   // character ranges in [text] to highlight as risky
    val isPreview: Boolean = false,   // true while the real detection engine is not connected yet
)

/** One row in the scan history. */
data class ScanRecord(
    val id: Long,
    val sender: String,
    val text: String,
    val source: MessageSource,
    val verdict: Verdict,
    val score: Float,
    val timestamp: Long,
)
