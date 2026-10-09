package com.example.sipatkilatis.detection

/**
 * Safety check for LLM explanations. A small model sometimes repeats the scammer's instruction
 * ("I-verify ang account mo...") instead of warning against it. Such an answer is thrown away and the
 * template explanation is shown instead.
 *
 * Rule: a sentence that STARTS with a risky action (verify, click, send, pay, ibigay, ...) within its first
 * few words, with no negation before it ("don't", "huwag", ...), is an instruction to do something risky.
 * Mentions later in a sentence ("they want you to verify your account") are fine.
 */
object ExplanationSafety {
    private val risky = setOf(
        "verify", "i-verify", "iverify", "click", "i-click", "tap", "pindutin", "pindot", "open", "buksan",
        "send", "i-send", "isend", "ipadala", "magpadala", "pay", "magbayad", "bayaran", "transfer",
        "ibigay", "provide", "share", "i-share", "reply", "i-reply", "enter", "login", "log", "mag-login",
        "claim", "i-claim", "register", "mag-register", "update", "i-update", "confirm", "i-confirm",
        "visit", "bisitahin", "download", "install",
        // Tagalog "mag-" forms ("Sanayang mag-verify ng account" was a real Gemma output)
        "mag-verify", "magverify", "mag-click", "magclick", "mag-log-in", "mag-login", "maglogin", "mag-reply",
        "magreply", "mag-send", "magsend", "mag-update", "mag-claim", "mag-confirm", "mag-download",
    )
    private val negations = setOf(
        "don't", "dont", "do", "never", "avoid", "ignore", "huwag", "wag", "hindi", "iwasan", "not",
    )
    private val sentenceSplit = Regex("""[.!?\n]+|(?i)\b(tip|payo)\s*:""")
    private val words = Regex("""[\p{L}'’-]+""")

    fun isSafe(text: String): Boolean {
        for (sentence in text.split(sentenceSplit)) {
            val w = words.findAll(sentence.lowercase().replace('’', '\'')).map { it.value }.toList()
            if (w.isEmpty()) continue
            // Look at the first 3 words (allowing "please" / "pakiusap" / "agad" before the verb)
            for ((i, word) in w.take(3).withIndex()) {
                if (word in negations) break
                if (word in risky && w.take(i).none { it in negations }) return false
            }
        }
        return true
    }
}
