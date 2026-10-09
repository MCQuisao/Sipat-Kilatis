package com.example.sipatkilatis.detection

import com.example.sipatkilatis.model.Sensitivity
import com.example.sipatkilatis.model.Verdict

/**
 * Combines the three checks into one risk score and a verdict.
 * Weights and thresholds are the ones in CLAUDE.md (source of truth): change them there first.
 */
object RiskScorer {
    const val W_ML = 0.6f
    const val W_URL = 0.25f
    const val W_RULES = 0.15f
    const val BLOCKLIST_FLOOR = 0.9f

    /** (SUSPICIOUS from, SCAM from) for each sensitivity setting. */
    fun thresholds(sensitivity: Sensitivity): Pair<Float, Float> = when (sensitivity) {
        Sensitivity.LOW -> 0.5f to 0.8f
        Sensitivity.NORMAL -> 0.4f to 0.7f
        Sensitivity.HIGH -> 0.3f to 0.6f
    }

    /** score = 0.6*ML + 0.25*URL + 0.15*Rules; blocklisted link -> at least 0.9; no ML -> URL + rules only. */
    fun score(ml: Float?, url: Float, rules: Float, blocklisted: Boolean): Float {
        val base = if (ml != null) W_ML * ml + W_URL * url + W_RULES * rules
                   else (W_URL * url + W_RULES * rules) / (W_URL + W_RULES)
        val s = if (blocklisted) maxOf(base, BLOCKLIST_FLOOR) else base
        return s.coerceIn(0f, 1f)
    }

    fun verdict(score: Float, sensitivity: Sensitivity): Verdict {
        val (suspicious, scam) = thresholds(sensitivity)
        return when {
            score >= scam -> Verdict.SCAM
            score >= suspicious -> Verdict.SUSPICIOUS
            else -> Verdict.SAFE
        }
    }

    /** Sort and merge overlapping highlight ranges so the UI can draw them in one pass. */
    fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        val out = mutableListOf<IntRange>()
        for (r in ranges.sortedBy { it.first }) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) out[out.size - 1] = last.first..maxOf(last.last, r.last)
            else out += r
        }
        return out
    }
}
