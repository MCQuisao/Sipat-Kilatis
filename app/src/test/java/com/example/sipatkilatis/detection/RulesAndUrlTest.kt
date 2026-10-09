package com.example.sipatkilatis.detection

import com.example.sipatkilatis.model.Sensitivity
import com.example.sipatkilatis.model.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules engine + URL checker on 15 Filipino / English / Taglish messages (scam and normal). */
class RulesAndUrlTest {
    private val rules = RegexRulesEngine()
    private val urls = OfflineUrlChecker(setOf("gcash-verify.xyz", "bdo-secure-ph.com"))

    /** Message and the flag ids it must raise (rules + URL). Empty set = normal message, must raise nothing. */
    private val cases = listOf(
        // ---- scams
        "Your GCash account has been locked. Verify now at gcash-verify.xyz/login within 24 hours."
            to setOf("locked", "verify", "urgency", "url_blocklist", "url_tld"),
        "Nanalo ka ng P50,000! I-claim agad sa bit.ly/claim-premyo"
            to setOf("prize", "urgency", "url_short"),
        "BDO Alert: Na-suspend ang account mo. I-update agad ang details sa http://bdo-secure-login.com"
            to setOf("locked", "verify", "urgency", "url_lookalike"),
        "Your parcel is on hold due to unpaid delivery fee. Pay PHP 150 here: jnt-express.top/pay"
            to setOf("delivery_fee", "url_lookalike", "url_tld"),
        "Para ma-activate ulit ang account mo, i-send ang OTP na natanggap mo."
            to setOf("otp"),
        "Loan APPROVED! Get P20,000 cash today, no requirements. Reply YES and pay the processing fee."
            to setOf("loan", "send_money"),
        "Hiring! Kumita ng P1,500 kada araw sa pag-like lang ng videos. Message us sa Telegram"
            to setOf("job"),
        "Congrats! Your number won a FREE iPhone. Claim now: http://192.168.10.5/prize"
            to setOf("prize", "url_ip"),
        "Your GC@SH acc0unt is l0cked. V3rify at gc4sh-promo.com"          // lookalike characters
            to setOf("locked", "verify", "url_lookalike"),
        // ---- normal messages
        "Anak, uuwi ako mamaya mga 7pm. May ulam pa ba tayo?" to emptySet(),
        "Your OTP is 482913. Do not share this code with anyone." to emptySet(),
        "Ok po ma'am, salamat sa update. Kita tayo bukas sa office." to emptySet(),
        "You have paid P215.00 of GCash to Dunkin. Ref. No. 156419820." to emptySet(),
        "Your order is out for delivery. Track it at https://www.lazada.com.ph/orders" to emptySet(),
        "Huwag ibigay ang OTP mo kahit kanino. Ang GCash ay hindi humihingi nito." to emptySet(),
    )

    @Test
    fun fifteenMessages() {
        assertEquals(15, cases.size)
        for ((text, expected) in cases) {
            val input = Preprocessor.process(text)
            val r = rules.check(input)
            val u = urls.check(input)
            val got = (r.flags + u.result.flags).map { it.id }.toSet()
            if (expected.isEmpty()) {
                assertTrue("normal message flagged $got: $text", got.isEmpty())
                assertEquals(0f, r.score + u.result.score, 0f)
            } else {
                assertTrue("missing ${expected - got} (got $got): $text", got.containsAll(expected))
                assertTrue("score should be > 0: $text", r.score + u.result.score > 0f)
            }
        }
    }

    @Test
    fun urlScoresFollowSpec() {
        fun u(text: String) = urls.check(Preprocessor.process(text)).result.score
        assertEquals(1.0f, u("go to gcash-verify.xyz now"), 0f)       // blocklist
        assertEquals(0.8f, u("visit gcash-rewards.com"), 0f)          // lookalike
        assertEquals(0.5f, u("see http://10.0.0.1/login"), 0f)        // raw IP
        assertEquals(0.4f, u("tap bit.ly/abc"), 0f)                   // shortener
        assertEquals(0.4f, u("promo at freestuff.xyz"), 0f)           // odd TLD
        assertEquals(0f, u("GIGA 50 promo: smrt.ph/giga"), 0f)        // official Smart short link
        assertEquals(0f, u("pay via https://www.gcash.com/help"), 0f) // official GCash
        assertTrue(urls.check(Preprocessor.process("gcash-verify.xyz")).blocklisted)
    }

    @Test
    fun disguisedLinksAreRecognized() {
        fun hosts(text: String) = Preprocessor.process(text).urls.map { it.value }
        assertEquals(listOf("gcash-verify.xyz/login"), hosts("I-verify agad sa gcash-verify[.]xyz/login"))
        assertEquals(listOf("gcash-verify.xyz"), hosts("go to gcash-verify(.)xyz now"))
        assertEquals(listOf("gcash-verify.xyz"), hosts("go to gcash-verify(dot)xyz now"))
        assertEquals(listOf("gcash-verify.xyz"), hosts("go to gcash-verify dot xyz now"))
        assertEquals(listOf("bdo-secure.com/login"), hosts("visit bdo-secure . com/login"))
        assertEquals(listOf("bit.ly/abc"), hosts("i-claim sa bit ly/abc"))
        // Blocklist works on the rebuilt domain -> forces the 0.9 floor
        assertTrue(urls.check(Preprocessor.process("I-verify agad sa gcash-verify[.]xyz/login")).blocklisted)
        // Normal sentences are not links
        assertTrue(hosts("Uuwi ako. Top ka talaga anak!").isEmpty())
        assertTrue(hosts("Ok po, salamat. Kita tayo bukas.").isEmpty())
    }

    @Test
    fun strongRulesSetMinimumScore() {
        // Link stripped by the network: ML unsure, rules strong -> SCAM
        assertEquals(0.7f, RiskScorer.score(0.12f, 0f, 0.65f, false), 1e-6f)
        assertEquals(Verdict.SCAM, RiskScorer.verdict(RiskScorer.score(0.12f, 0f, 0.65f, false), Sensitivity.NORMAL))
        // Medium rules -> at least SUSPICIOUS
        assertEquals(Verdict.SUSPICIOUS, RiskScorer.verdict(RiskScorer.score(0.28f, 0f, 0.55f, false), Sensitivity.NORMAL))
        // Weak rules -> formula unchanged
        assertEquals(0.6f * 0.1f + 0.15f * 0.3f, RiskScorer.score(0.1f, 0f, 0.3f, false), 1e-6f)
        // A floor never lowers a higher score
        assertEquals(0.6f * 1f + 0.25f * 1f + 0.15f * 0.65f, RiskScorer.score(1f, 1f, 0.65f, false), 1e-6f)
    }

    @Test
    fun officialLinksCapTheScore() {
        fun official(text: String) = urls.check(Preprocessor.process(text)).allOfficial
        assertTrue(official("Here is your GCash receipt: https://www.gcash.com/help"))
        assertTrue(official("Track it at https://www.lazada.com.ph/orders"))
        assertTrue(official("Details at smrt.ph/giga50"))
        assertTrue(official("meeting: https://us02web.zoom.us/j/123456789"))
        assertTrue(!official("no link here"))
        assertTrue(!official("gcash.com/help and also gcash-promo.com/register"))   // one unofficial link is enough
        assertTrue(!official("see docs.google.com/forms/abc"))                     // anyone can make a Google Form
        assertTrue(!official("bit.ly/abc"))
        // ML says scam (0.98) but every link is official and no rules fire -> SAFE
        assertEquals(Verdict.SAFE, RiskScorer.verdict(RiskScorer.score(0.98f, 0f, 0f, false, true), Sensitivity.HIGH))
        // Official link + strong scam wording ("send your OTP") -> rule floor still wins
        assertEquals(Verdict.SCAM, RiskScorer.verdict(RiskScorer.score(0.98f, 0f, 0.65f, false, true), Sensitivity.NORMAL))
    }

    @Test
    fun highlightsPointAtOriginalText() {
        val text = "Your GC@SH acc0unt is l0cked. V3rify now"
        val r = rules.check(Preprocessor.process(text))
        val highlighted = r.ranges.map { text.substring(it) }
        assertTrue(highlighted.toString(), highlighted.any { it.contains("V3rify") })
    }

    @Test
    fun riskScorerFollowsClaudeMd() {
        assertEquals(0.6f * 0.5f + 0.25f * 0.4f + 0.15f * 0.2f, RiskScorer.score(0.5f, 0.4f, 0.2f, false), 1e-6f)
        assertEquals(0.9f, RiskScorer.score(0.1f, 1.0f, 0f, blocklisted = true), 1e-6f)   // blocklist floor
        assertEquals(1.0f, RiskScorer.score(null, 1.0f, 1.0f, false), 1e-6f)               // no ML -> renormalized
        assertEquals(Verdict.SAFE, RiskScorer.verdict(0.39f, Sensitivity.NORMAL))
        assertEquals(Verdict.SUSPICIOUS, RiskScorer.verdict(0.4f, Sensitivity.NORMAL))
        assertEquals(Verdict.SCAM, RiskScorer.verdict(0.7f, Sensitivity.NORMAL))
        assertEquals(Verdict.SAFE, RiskScorer.verdict(0.45f, Sensitivity.LOW))
        assertEquals(Verdict.SCAM, RiskScorer.verdict(0.6f, Sensitivity.HIGH))
        assertEquals(listOf(0..5, 8..9), RiskScorer.mergeRanges(listOf(3..5, 0..4, 8..9)))
    }
}
