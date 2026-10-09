package com.example.sipatkilatis.detection

import com.example.sipatkilatis.model.Flag

/**
 * Weighted regex rules for scam phrases in English, Filipino, and Taglish.
 * Each rule that fires adds its weight; the score is capped at 1.0.
 * Rules run on [PreprocessedText.matchText] (lowercase, lookalikes fixed), so "V3RIFY" still matches "verify".
 */
class RegexRulesEngine : RulesEngine {

    private class Rule(
        val flag: Flag,
        val weight: Float,
        pattern: String,
        /** If true, a match right after "do not / huwag / never ..." is a warning, not a scam ask. */
        val negatable: Boolean = false,
    ) {
        val regex = Regex(pattern)
    }

    // A few words in between, e.g. "send (your 6-digit) code"
    private val gap = """(?:\W+\S+){0,5}?\W+"""

    private val rules = listOf(
        Rule(Flag("locked", "Says your account is locked, suspended, or will be closed",
            "Sinasabing naka-lock, suspendido, o isasara ang account mo"), 0.30f,
            """\b(account|acct|gcash|maya|card|sim|wallet)\b$gap(locked|suspended|blocked|deactivated|frozen|restricted|disabled|closed|na-?lock|naka-?lock|na-?block|isasara|ide-?deactivate)|\b(naka-?lock|na-?lock|na-?block|suspendido|na-?suspend)\b"""),
        Rule(Flag("verify", "Asks you to verify, update, or confirm your account",
            "Pinapa-verify, pinapa-update, o pinapa-confirm ang account mo"), 0.20f,
            """\b(i-?verify|verify|i-?update|update your|i-?confirm|confirm your|re-?activate|i-?activate|validate)\b"""),
        Rule(Flag("click_link", "Pushes you to click or open a link", "Pinapapindot o pinapabukas ang isang link"), 0.15f,
            """\b(click|tap|pindutin|pindot|i-?click|i-?tap|visit|bisitahin|open|buksan)\b$gap(link|here|dito|below|url)\b|\bclick here\b"""),
        Rule(Flag("prize", "Says you won a prize or reward you didn't join for",
            "Sinasabing nanalo ka ng premyo na hindi mo sinalihan"), 0.30f,
            """\b(nanalo|panalo|you (have )?won|winner|congrat\w*|i-?claim|claim (your|now|na|the|it)|premyo|prize|raffle|jackpot|lucky draw|reward)\b"""),
        Rule(Flag("delivery_fee", "Asks for a parcel or delivery fee", "Humihingi ng bayad para sa parcel o delivery"), 0.30f,
            """\b(parcel|package|padala|delivery|shipment|item)\b$gap(on hold|naka-?hold|fee|bayad|pay|customs|unpaid|clearance)\b|\b(delivery fee|redelivery|customs fee|clearance fee)\b"""),
        Rule(Flag("otp", "Asks you to send or share your OTP, PIN, or code",
            "Hinihingi ang OTP, PIN, o code mo"), 0.40f,
            """\b(send|reply|share|ibigay|i-?send|ipadala|provide|enter|give|i-?type|i-?reply|i-?share)\b$gap(otp|code|pin|mpin|one-?time)\b|\b(otp|mpin)\b$gap(send|reply|ibigay|i-?send|ipadala|i-?reply)\b""",
            negatable = true),
        Rule(Flag("loan", "Offers an instant or pre-approved loan", "Nag-aalok ng instant o pre-approved na loan"), 0.25f,
            """\b(loan (is )?approved|approved (ang )?loan|pre-?approved|pautang|instant (cash|loan)|cash loan|no requirements?|walang requirements?|low interest)\b"""),
        Rule(Flag("job", "Offers easy work with high daily pay", "Nag-aalok ng madaling trabaho na malaki ang kita araw-araw"), 0.25f,
            """\b(earn|kumita|sahod|sweldo|salary|income|kita)\b$gap(daily|per day|kada araw|a day|araw-araw|weekly)\b|\b(work from home|part-?time job|easy job|hiring now|commission per)\b"""),
        Rule(Flag("urgency", "Pressures you to act right away", "Minamadali kang kumilos agad"), 0.15f,
            """\bwithin \d+ ?(hours?|hrs?|minutes?|mins?|days?)\b|\bsa loob ng \d+\b|\b(agad|ngayon din|ngayon na|asap|urgent|immediately|right away|last chance|today only|limited time|final notice|bago mag|expire[sd]?|mag-?e-?expire)\b"""),
        Rule(Flag("send_money", "Asks you to send money, load, or pay a fee", "Pinapapadala ka ng pera, load, o bayad"), 0.30f,
            """\b(send|magpadala|ipadala|transfer|pay|bayaran|magbayad|deposit|mag-?deposit)\b$gap(money|pera|load|fee|payment|bayad|processing|reservation)\b|\b(processing fee|reservation fee|registration fee|activation fee)\b""",
            negatable = true),
        Rule(Flag("threat", "Threatens you with closure, blocking, or legal action",
            "Tinatakot ka na isasara, iba-block, o kakasuhan"), 0.15f,
            """\b(will be (closed|terminated|deleted|blocked)|permanent(ly)? (lock|block)\w*|legal action|kakasuhan|makukulong|ipapa-?block)\b"""),
        Rule(Flag("gambling", "Promotes online casino or betting", "Nagpo-promote ng online casino o sugal"), 0.25f,
            """\b(casino|slots?|sabong|betting|free spins?|jili|welcome bonus|daily rebate|baccarat)\b"""),
        Rule(Flag("credentials", "Asks for your password, login, or card details",
            "Hinihingi ang password, login, o detalye ng card mo"), 0.30f,
            """\b(enter|provide|ibigay|send|i-?update|input|i-?type)\b$gap(password|login details|card number|cvv|account details|personal (info|details)|username)\b""",
            negatable = true),
        Rule(Flag("gov_aid", "Mentions government aid or assistance (often faked)",
            "Binabanggit ang ayuda o tulong ng gobyerno (madalas peke)"), 0.20f,
            """\b(ayuda|dswd|cash assistance|financial assistance|4ps|amelioration)\b"""),
    )

    // "do not share", "huwag ibigay", "never send" -> a safety warning, not a request
    private val negation = Regex("""\b(do not|don't|dont|never|huwag|wag|hindi)\s+(\S+\s+){0,2}$""")

    override fun check(input: PreprocessedText): CheckResult {
        val text = input.matchText
        val flags = mutableListOf<Flag>()
        val ranges = mutableListOf<IntRange>()
        var score = 0f
        for (rule in rules) {
            val matches = rule.regex.findAll(text).filter { m ->
                !(rule.negatable && negation.containsMatchIn(text.substring(maxOf(0, m.range.first - 25), m.range.first)))
            }.toList()
            if (matches.isEmpty()) continue
            score += rule.weight
            flags += rule.flag
            ranges += matches.map { it.range }
        }
        return CheckResult(score.coerceAtMost(1f), flags, ranges)
    }
}
