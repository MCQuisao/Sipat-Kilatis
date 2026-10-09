package com.example.sipatkilatis.detection

import com.example.sipatkilatis.model.Flag

/**
 * Offline URL checks. Scores per link: blocklist 1.0, lookalike brand domain 0.8, raw IP 0.5,
 * odd TLD 0.3-0.5, shortener 0.4. The URL score U is the highest score of any link.
 *
 * @param blocklist known scam domains (assets/blocklist.txt; refreshed online in phase 7)
 */
class OfflineUrlChecker(private val blocklist: Set<String>) : UrlChecker {

    // Official domains: a link on one of these (or a subdomain) is never flagged as a lookalike
    private val official = listOf(
        "gcash.com", "maya.ph", "paymaya.com", "bdo.com.ph", "bpi.com.ph", "metrobank.com.ph", "landbank.com",
        "unionbankph.com", "securitybank.com", "rcbc.com", "pnb.com.ph", "chinabank.ph", "lazada.com.ph",
        "shopee.ph", "shp.ee", "lbcexpress.com", "jtexpress.ph", "ninjavan.co", "grab.com", "foodpanda.ph",
        "smart.com.ph", "smrt.ph", "globe.com.ph", "meralco.com.ph", "pldthome.com", "dito.ph",
        "sss.gov.ph", "philhealth.gov.ph", "pagibigfund.gov.ph", "dswd.gov.ph", "gov.ph",
    )

    // Meeting links people really send each other. Deliberately NOT all of google.com / facebook.com:
    // anyone can publish there, and fake Facebook pages and Google Forms phishing pages are common scams.
    private val trustedMeetingLinks = listOf("zoom.us", "meet.google.com", "teams.microsoft.com", "teams.live.com")

    // Brand names scammers put into fake domains (gcash-verify.xyz, bdo-secure-ph.com, lazada-promo.top)
    private val brands = listOf(
        "gcash", "maya", "paymaya", "bdo", "bpi", "metrobank", "landbank", "unionbank", "securitybank", "rcbc",
        "lazada", "shopee", "lbc", "jnt", "jtexpress", "ninjavan", "meralco", "pldt", "globe", "smart",
        "philhealth", "pagibig", "sss", "dswd", "paypal", "facebook",
    )

    // Brands that are also everyday words ("start", "smartphone", "globes"): exact match only
    private val commonWords = setOf("smart", "globe", "maya")

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "cutt.ly", "t.co", "goo.gl", "is.gd", "ow.ly", "rb.gy", "shorturl.at", "tiny.cc",
        "s.id", "t.ly", "bit.do", "rebrand.ly", "shorturl.asia", "u.to", "tny.im", "v.gd",
    )

    // TLDs that are cheap and common in scam links, with their score
    private val oddTlds = mapOf(
        "xyz" to 0.4f, "top" to 0.4f, "click" to 0.4f, "icu" to 0.4f, "vip" to 0.4f, "win" to 0.4f, "rest" to 0.4f,
        "pw" to 0.4f, "buzz" to 0.4f, "sbs" to 0.4f, "cfd" to 0.4f, "cyou" to 0.4f, "bet" to 0.5f, "tk" to 0.5f,
        "ml" to 0.5f, "ga" to 0.5f, "cf" to 0.5f, "gq" to 0.5f, "info" to 0.3f, "online" to 0.3f, "site" to 0.3f,
        "live" to 0.3f, "shop" to 0.3f, "club" to 0.3f, "link" to 0.3f, "life" to 0.3f, "store" to 0.3f,
    )

    private val ipHost = Regex("""\d{1,3}(\.\d{1,3}){3}""")

    override fun check(input: PreprocessedText): UrlCheckResult {
        var score = 0f
        var blocklisted = false
        val flags = linkedMapOf<String, Flag>()   // one flag per kind, in order found
        val ranges = mutableListOf<IntRange>()
        val spans = mutableListOf<RiskSpan>()

        for (url in input.urls) {
            val host = hostOf(url.value)
            var s = 0f
            var worst: Flag? = null   // the strongest reason for this link, shown when it is tapped
            fun hit(value: Float, flag: Flag) {
                if (value > s || worst == null) worst = flag
                s = maxOf(s, value); flags.putIfAbsent(flag.id, flag)
            }

            if (inList(host, blocklist)) {
                blocklisted = true
                hit(1.0f, Flag("url_blocklist", "Contains a known scam link ($host)", "May kilalang scam link ($host)"))
            }
            val brand = lookalikeBrand(host)
            if (brand != null) {
                hit(0.8f, Flag("url_lookalike", "Link pretends to be $brand but is not its official website ($host)",
                    "Nagpapanggap na $brand ang link pero hindi ito ang opisyal na website ($host)"))
            }
            if (ipHost.matches(host)) {
                hit(0.5f, Flag("url_ip", "Link uses a raw IP address instead of a website name",
                    "Gumagamit ang link ng IP address imbes na pangalan ng website"))
            }
            oddTlds[host.substringAfterLast('.')]?.let {
                hit(it, Flag("url_tld", "Link uses an unusual domain ending (.${host.substringAfterLast('.')})",
                    "Kakaiba ang dulo ng link (.${host.substringAfterLast('.')})"))
            }
            if (host in shorteners) {
                hit(0.4f, Flag("url_short", "Uses a short link that hides where it really goes ($host)",
                    "Gumagamit ng maikling link na nagtatago kung saan talaga ito papunta ($host)"))
            }
            if (s > 0f) {
                ranges += url.range
                worst?.let { spans += RiskSpan(url.range, it, it.id in STRONG_FLAGS) }
            }
            score = maxOf(score, s)
        }
        // Every link goes to an official brand site or a known meeting service -> the risk scorer may cap the score
        val allOfficial = input.urls.isNotEmpty() && !blocklisted &&
            input.urls.all { inList(hostOf(it.value), official + trustedMeetingLinks) }
        return UrlCheckResult(CheckResult(score, flags.values.toList(), ranges, spans), blocklisted, allOfficial)
    }

    /** "https://www.Gcash-Verify.xyz/login?x=1" -> "gcash-verify.xyz" */
    fun hostOf(url: String): String =
        url.lowercase()
            .substringAfter("://")
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfter('@')        // user@host tricks
            .substringBefore(':')       // port
            .removePrefix("www.")

    /** True if [host] equals a domain in [domains] or is a subdomain of one. */
    private fun inList(host: String, domains: Collection<String>) =
        domains.any { host == it || host.endsWith(".$it") }

    /** Returns the brand a non-official domain imitates, or null. */
    private fun lookalikeBrand(host: String): String? {
        if (inList(host, official) || host in shorteners) return null
        val parts = host.split('.', '-')
        for (part in parts) {
            for (brand in brands) {
                val fuzzy = brand.length >= 5 && brand !in commonWords
                val match = when {
                    part == brand -> true
                    fuzzy && part.contains(brand) -> true                           // gcashverify
                    fuzzy && editDistance(part, brand) == 1 -> true                 // gcassh, lazadda
                    else -> false
                }
                if (match) return displayName(brand)
            }
        }
        return null
    }

    /** How the brand is written for people: "BDO", "GCash", "J&T", "Lazada". */
    private fun displayName(brand: String) = when (brand) {
        "gcash" -> "GCash"
        "paymaya" -> "PayMaya"
        "paypal" -> "PayPal"
        "pagibig" -> "Pag-IBIG"
        "philhealth" -> "PhilHealth"
        "jnt", "jtexpress" -> "J&T"
        "ninjavan" -> "Ninja Van"
        "unionbank" -> "UnionBank"
        "securitybank" -> "Security Bank"
        else -> if (brand.length <= 4) brand.uppercase() else brand.replaceFirstChar { it.uppercase() }
    }

    private fun editDistance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2   // only need to know if it is exactly 1
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
