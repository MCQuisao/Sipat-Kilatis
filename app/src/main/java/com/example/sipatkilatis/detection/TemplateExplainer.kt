package com.example.sipatkilatis.detection

import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Verdict

/**
 * Explanation built only from the detector's flags (each flag already has a reason in both languages).
 * Instant and always available, so the app has an explanation even without the local LLM.
 */
class TemplateExplainer : Explainer {

    override suspend fun explain(result: ScanResult, filipino: Boolean): String = build(result, filipino)

    fun build(result: ScanResult, filipino: Boolean): String {
        val intro = when (result.verdict) {
            Verdict.SCAM -> if (filipino) "Mukhang scam ang mensaheng ito." else "This message looks like a scam."
            Verdict.SUSPICIOUS -> if (filipino) "Kahina-hinala ang mensaheng ito." else "This message is suspicious."
            Verdict.SAFE -> return if (filipino)
                "Wala kaming nakitang karaniwang senyales ng scam. Gayunpaman, huwag ibigay kaninuman ang OTP, PIN, o password mo."
            else "We didn't find common scam signs. Still, never share your OTP, PIN, or password with anyone."
        }
        val signs = result.flags.take(4).joinToString("\n") { "• " + if (filipino) it.reasonFil else it.reasonEn }
        val tip = if (filipino)
            "Huwag pindutin ang link at huwag ibigay ang OTP o PIN mo. Kung may duda, kontakin ang kumpanya sa opisyal nilang app o hotline."
        else "Don't tap any link and never share your OTP or PIN. If in doubt, contact the company through its official app or hotline."
        val signsLabel = if (filipino) "Mga babalang nakita:" else "Warning signs found:"
        return if (signs.isEmpty()) "$intro\n\n$tip" else "$intro\n\n$signsLabel\n$signs\n\n$tip"
    }
}
