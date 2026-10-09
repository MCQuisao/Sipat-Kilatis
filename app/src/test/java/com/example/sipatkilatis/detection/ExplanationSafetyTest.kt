package com.example.sipatkilatis.detection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationSafetyTest {

    @Test
    fun rejectsAnswersThatRepeatTheScam() {
        // Real output from Gemma 1B on the phone (phase 6 test)
        assertFalse(ExplanationSafety.isSafe("Ang text ay isang scam dahil nagpapanggap ang link na \"gcash-verify.xyz\" " +
            "bilang opisyal na website ng gcash. I-verify ang account mo para magkaroon ng access."))
        assertFalse(ExplanationSafety.isSafe("This is a scam. Please click the link to secure your account."))
        assertFalse(ExplanationSafety.isSafe("Tip: Send the OTP to unlock your account."))
        assertFalse(ExplanationSafety.isSafe("Payo: Ibigay agad ang OTP para hindi ma-lock."))
        // Real Gemma output (after the crash fix): garbled "get used to verifying the account"
        assertFalse(ExplanationSafety.isSafe("Ang text ay scam dahil nagpapanggap ang link ay isang nakatagong gcash " +
            "website para mag-log-in ng account. Sanayang mag-verify ng account."))
    }

    @Test
    fun keepsRealWarnings() {
        // Real outputs from Gemma 1B on the phone
        assertTrue(ExplanationSafety.isSafe("This text looks like an attempt at phishing; it's using Gcash as an excuse " +
            "for account locking. Don't click any link! Tip: If you see an account locking message, be very cautious " +
            "about clicking anything."))
        assertTrue(ExplanationSafety.isSafe("This text looks like an attempt at scamming you by requesting an OTP. " +
            "They're trying to trick you into giving your account information.\nTip: Don't provide your OTP!"))
        assertTrue(ExplanationSafety.isSafe("Scam ito dahil gusto nilang i-verify mo ang account mo. Payo: Huwag pindutin ang link."))
        assertTrue(ExplanationSafety.isSafe("Never share your OTP. Huwag ibigay ang PIN mo."))
    }
}
