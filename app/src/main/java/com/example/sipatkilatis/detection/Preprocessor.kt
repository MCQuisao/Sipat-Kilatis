package com.example.sipatkilatis.detection

/** A found item (URL, phone number, amount) and where it is in the original text. */
data class Span(val value: String, val range: IntRange)

/**
 * Text prepared for the rules engine and URL checker.
 * [matchText] has exactly the same length as [original], so a regex match on it can be highlighted in the
 * original text at the same positions.
 */
data class PreprocessedText(
    val original: String,
    val matchText: String,       // lowercase + lookalike characters fixed (v3rify -> verify, gc@sh -> gcash)
    val urls: List<Span>,
    val phones: List<Span>,
    val amounts: List<Span>,
)

/** Lowercase, fix lookalike characters, and extract URLs, phone numbers, and peso amounts. */
object Preprocessor {
    // Lookalike characters scammers use to dodge filters
    private val LOOKALIKES = mapOf('0' to 'o', '1' to 'l', '3' to 'e', '4' to 'a', '5' to 's', '7' to 't',
        '@' to 'a', '$' to 's')

    private val TOKEN = Regex("""[\p{L}\p{N}@$]+""")
    private val AMOUNT_TOKEN = Regex("""(p|php)\d+k?""", RegexOption.IGNORE_CASE)   // p50k, php500

    // URLs incl. ones without http (bit.ly/abc, gcash-verify.com) and raw IPs
    val URL = Regex(
        """https?://[^\s<>"']+|www\.[^\s<>"']+|""" +
            """\b\d{1,3}(\.\d{1,3}){3}(:\d+)?(/[^\s<>"']*)?|""" +
            """\b[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}/[^\s<>"']*|""" +
            """\b[a-z0-9-]+(\.[a-z0-9-]+)*\.(com|ph|net|org|info|live|life|site|online|shop|xyz|top|io|me|cc|co|ly|""" +
            """gl|gd|to|tk|ml|ga|cf|gq|gg|win|store|rest|pw|app|club|vip|bet|link|click|icu|buzz|sbs|cfd|cyou)\b""",
        RegexOption.IGNORE_CASE,
    )

    // Disguised links that scammers use to get past the networks' link filters:
    //   gcash-verify[.]xyz   gcash-verify(.)xyz   gcash-verify(dot)xyz   gcash-verify dot xyz   gcash-verify . xyz
    // The LAST separator must be disguised and followed by a known TLD, so normal sentences don't match.
    private const val TLDS = "com|ph|net|org|info|live|life|site|online|shop|xyz|top|io|me|cc|co|ly|gl|gd|to|tk|" +
        "ml|ga|cf|gq|gg|win|store|rest|pw|app|club|vip|bet|link|click|icu|buzz|sbs|cfd|cyou"
    private const val SEP = """(?:\s*[\[({]\s*(?:\.|dot)\s*[\])}]\s*|\s+dot\s+|\s+\.\s+)"""
    private val DISGUISED = Regex("""\b[a-z0-9-]+(?:(?:$SEP|\.)[a-z0-9-]+)*$SEP(?:$TLDS)\b(?:\s*/[^\s<>"']*)?""")
    // Shorteners written with a space: "bit ly/abc", "cutt ly / promo"
    private val SPACED_SHORTENER = Regex("""\b(bit|cutt|ow|rb|tinyurl)\s+(ly|gy|com)\b(?:\s*/\s*[^\s<>"']+)?""")
    private val SEP_REGEX = Regex(SEP)
    private val SPACES = Regex("""\s+""")

    // Philippine mobile numbers: 09171234567, +63 917 123 4567, 0917-123-4567
    private val PHONE = Regex("""(\+?63|\b0)9\d{2}[\s-]?\d{3}[\s-]?\d{4}\b""")

    // Peso amounts: P5,000  PHP 500  ₱1k  500 pesos  P50k
    private val AMOUNT = Regex(
        """(₱|\bphp\.?|\bp)\s?\d[\d,]*(\.\d+)?\s?k?\b|\b\d[\d,]*(\.\d+)?\s?k?\s?(php|pesos?)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun process(text: String): PreprocessedText {
        // Per-character lowercase keeps the length identical to the original
        val lower = CharArray(text.length) { text[it].lowercaseChar() }
        fixLookalikes(lower)
        val match = String(lower)
        return PreprocessedText(
            original = text,
            matchText = match,
            urls = findUrls(match),
            phones = PHONE.findAll(match).map { Span(it.value, it.range) }.toList(),
            amounts = AMOUNT.findAll(match).map { Span(it.value.trim(), it.range) }.toList(),
        )
    }

    /**
     * Replace lookalike characters that sit INSIDE a word ("v3rify", "acc0unt", "gc@sh", "l0gin").
     * Left alone: tokens starting with a digit ("24hrs", "5pm") and trailing digits ("p500", "php1000",
     * "gcash1"), so amounts and numbers stay intact.
     */
    private fun fixLookalikes(chars: CharArray) {
        val s = String(chars)
        for (m in TOKEN.findAll(s)) {
            val token = m.value
            if (token.count { it.isLetter() } < 2 || token.first().isDigit() || AMOUNT_TOKEN.matches(token)) continue
            val lastLetter = m.range.first + token.indexOfLast { it.isLetter() }
            for (i in m.range.first until lastLetter) chars[i] = LOOKALIKES[chars[i]] ?: chars[i]
        }
    }

    /**
     * Normal links plus disguised ones. A disguised link's value is rebuilt as a normal domain
     * ("gcash-verify[.]xyz/login" -> "gcash-verify.xyz/login") so the blocklist and lookalike checks work on it;
     * its range still points at the original text for highlighting.
     */
    private fun findUrls(text: String): List<Span> {
        val urls = URL.findAll(text).map { Span(trimUrl(it.value), it.range.first..it.range.first + trimUrl(it.value).length - 1) }
            .toMutableList()
        fun overlaps(r: IntRange) = urls.any { it.range.first <= r.last && r.first <= it.range.last }
        for (m in DISGUISED.findAll(text)) {
            if (overlaps(m.range)) continue
            val cleaned = trimUrl(SPACES.replace(SEP_REGEX.replace(m.value, "."), ""))
            urls += Span(cleaned, m.range)
        }
        for (m in SPACED_SHORTENER.findAll(text)) {
            if (overlaps(m.range)) continue
            val cleaned = trimUrl(SPACES.replace(SPACES.replaceFirst(m.value, "."), ""))
            urls += Span(cleaned, m.range)
        }
        return urls.sortedBy { it.range.first }
    }

    /** Drop trailing punctuation that is part of the sentence, not the link ("...xyz/login." -> "...xyz/login"). */
    private fun trimUrl(url: String) = url.trimEnd('.', ',', '!', '?', ')', ';', ':')
}
