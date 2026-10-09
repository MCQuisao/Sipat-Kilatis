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

    // Strong phrase rules set a minimum score, so a scam without a link (PH networks strip links from SMS)
    // can't be outvoted by an unsure ML model. Rules >= 0.6 -> at least 0.7; Rules >= 0.4 -> at least 0.4.
    const val RULES_STRONG = 0.6f
    const val RULES_STRONG_FLOOR = 0.7f
    const val RULES_MEDIUM = 0.4f
    const val RULES_MEDIUM_FLOOR = 0.4f

    // Links that all go to official sites (gcash.com, lazada.com.ph, zoom.us...): the ML models treat ANY link as
    // scam-like (their training links were nearly all scams), so without strong rules the score is capped at SAFE
    // for every sensitivity (High starts SUSPICIOUS at 0.3).
    const val OFFICIAL_LINK_CAP = 0.25f

    /** (SUSPICIOUS from, SCAM from) for each sensitivity setting. */
    fun thresholds(sensitivity: Sensitivity): Pair<Float, Float> = when (sensitivity) {
        Sensitivity.LOW -> 0.5f to 0.8f
        Sensitivity.NORMAL -> 0.4f to 0.7f
        Sensitivity.HIGH -> 0.3f to 0.6f
    }

    /**
     * score = 0.6*ML + 0.25*URL + 0.15*Rules; no ML -> URL + rules only.
     * Floors: blocklisted link -> at least 0.9; strong rules -> at least 0.7; medium rules -> at least 0.4.
     * Cap: all links official and rules below medium -> at most 0.25 (SAFE).
     */
    fun score(ml: Float?, url: Float, rules: Float, blocklisted: Boolean, officialLinksOnly: Boolean = false): Float {
        var s = if (ml != null) W_ML * ml + W_URL * url + W_RULES * rules
                else (W_URL * url + W_RULES * rules) / (W_URL + W_RULES)
        if (officialLinksOnly && rules < RULES_MEDIUM) s = minOf(s, OFFICIAL_LINK_CAP)
        if (rules >= RULES_STRONG) s = maxOf(s, RULES_STRONG_FLOOR)
        else if (rules >= RULES_MEDIUM) s = maxOf(s, RULES_MEDIUM_FLOOR)
        if (blocklisted) s = maxOf(s, BLOCKLIST_FLOOR)
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
