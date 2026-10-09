package com.example.sipatkilatis.detection

import java.util.regex.Pattern

/**
 * Model input normalization: an exact port of ml/text_normalize.py. Both ONNX models were trained on
 * text that went through these steps, in this order. Checked against the Python output by
 * TextNormalizerTest (test vectors).
 *
 * Python's \w \d \s \b are Unicode-aware. Android's regex engine (ICU) already is, and rejects the
 * UNICODE_CHARACTER_CLASS flag; desktop Java (unit tests) needs the flag to behave the same.
 */
object TextNormalizer {
    /** UNICODE_CHARACTER_CLASS where supported (desktop JVM), 0 on Android (ICU is Unicode-aware by default). */
    internal val UNICODE_FLAG: Int = runCatching {
        Pattern.compile("a", Pattern.UNICODE_CHARACTER_CLASS)
        Pattern.UNICODE_CHARACTER_CLASS
    }.getOrDefault(0)

    private val FLAGS = UNICODE_FLAG

    // Domains / URLs, with or without http(s):// or www.
    private val URL = Pattern.compile(
        "https?://\\S+|www\\.\\S+|" +
            "\\b[\\w-]+(\\.[\\w-]+)*\\.[a-z]{2,}/\\S*|" +
            "\\b[\\w-]+\\.(com|ph|net|org|info|live|life|site|online|shop|xyz|top|im|io|me|cc|co|ly|gl|gd|to|tk|gg|win|" +
            "store|rest|pw|app|club|vip|bet|link|click|icu)\\b\\S*",
        FLAGS,
    )

    // Peso amounts: ₱500, php 1,000, p50 (prefix) or 500php, 20 pesos, 100p (suffix)
    private val AMOUNT = Pattern.compile(
        "(₱|\\bphp\\s?\\.?|\\bp)\\s?\\d[\\d,]*(\\.\\d+)?|\\b\\d[\\d,]*\\s?(php|pesos?|p)\\b",
        FLAGS,
    )
    private val NUMBER = Pattern.compile("\\d+", FLAGS)
    private val SPACES = Pattern.compile("\\s+", FLAGS)

    fun normalize(text: String): String {
        var t = text.replace("<REAL NAME>", " ")             // 1. anonymization marker (training data only)
        t = t.lowercase()                                     // 2. lowercase
        t = URL.matcher(t).replaceAll(" urltoken ")           // 3. URLs -> urltoken
        t = AMOUNT.matcher(t).replaceAll(" amttoken ")        // 4. peso amounts -> amttoken
        t = NUMBER.matcher(t).replaceAll(" numtoken ")        // 5. other numbers -> numtoken
        return SPACES.matcher(t).replaceAll(" ").trim()       // 6. collapse whitespace
    }
}
