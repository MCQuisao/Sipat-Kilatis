package com.example.sipatkilatis.detection

import org.json.JSONObject
import java.io.InputStream
import java.util.regex.Pattern

/**
 * Byte-level BPE tokenizer for RoBERTa (same algorithm as GPT-2 / Hugging Face "ByteLevel" + "BPE").
 * Matches ml/models/tokenizer: no prefix space, <s> ... </s>, max 128 tokens total.
 * Checked against the Python token ids by RobertaTokenizerTest.
 *
 * Steps for one text:
 *  1. split into pieces with the GPT-2 regex (words, numbers, punctuation, keeping the leading space)
 *  2. map each piece's UTF-8 bytes to printable characters (bytes_to_unicode)
 *  3. merge characters with the BPE merge list (lowest rank first)
 *  4. look up each merged piece in the vocabulary
 */
class RobertaTokenizer(
    private val vocab: Map<String, Int>,
    merges: List<Pair<String, String>>,
) {
    companion object {
        const val BOS = 0        // <s>
        const val EOS = 2        // </s>
        const val MAX_LEN = 128  // including <s> and </s>

        private val PIECES: Pattern = Pattern.compile(
            "'s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+",
            TextNormalizer.UNICODE_FLAG,   // Unicode \s like Hugging Face's tokenizer (see TextNormalizer)
        )

        /** GPT-2's reversible byte -> printable character table. */
        private val BYTE_TO_CHAR: CharArray = run {
            val bs = (('!'.code..'~'.code) + ('¡'.code..'¬'.code) + ('®'.code..'ÿ'.code)).toMutableList()
            val cs = bs.toMutableList()
            var n = 0
            for (b in 0 until 256) {
                if (b !in bs) {
                    bs += b
                    cs += 256 + n
                    n++
                }
            }
            CharArray(256).also { table -> bs.forEachIndexed { i, b -> table[b] = cs[i].toChar() } }
        }

        /** Load from vocab.json + merges.txt streams (assets on the phone, files in unit tests). */
        fun load(vocabJson: InputStream, mergesTxt: InputStream): RobertaTokenizer {
            val json = JSONObject(vocabJson.bufferedReader(Charsets.UTF_8).readText())
            val vocab = HashMap<String, Int>(json.length() * 2)
            json.keys().forEach { vocab[it] = json.getInt(it) }
            val merges = mergesTxt.bufferedReader(Charsets.UTF_8).readLines()
                .filter { it.isNotBlank() && !it.startsWith("#version") }
                .map { line -> line.split(' ').let { it[0] to it[1] } }
            return RobertaTokenizer(vocab, merges)
        }
    }

    private val ranks: Map<Pair<String, String>, Int> = merges.withIndex().associate { (i, m) -> m to i }
    private val cache = HashMap<String, List<String>>()

    /** Token ids for one message: [<s>, ..., </s>], truncated to [MAX_LEN]. */
    fun encode(text: String): LongArray {
        val ids = ArrayList<Long>()
        val m = PIECES.matcher(text)
        while (m.find() && ids.size < MAX_LEN - 2) {
            val mapped = m.group().toByteArray(Charsets.UTF_8)
                .map { BYTE_TO_CHAR[it.toInt() and 0xFF] }.joinToString("")
            for (token in bpe(mapped)) {
                vocab[token]?.let { ids += it.toLong() }   // byte-level vocab covers every byte, so this never misses
            }
        }
        val body = ids.take(MAX_LEN - 2)
        return LongArray(body.size + 2).also { out ->
            out[0] = BOS.toLong()
            body.forEachIndexed { i, id -> out[i + 1] = id }
            out[out.size - 1] = EOS.toLong()
        }
    }

    /** Standard BPE: repeatedly merge the adjacent pair with the lowest merge rank. */
    private fun bpe(piece: String): List<String> = synchronized(cache) {
        cache[piece]?.let { return it }
        var word = piece.map { it.toString() }
        while (word.size > 1) {
            var best: Pair<String, String>? = null
            var bestRank = Int.MAX_VALUE
            for (i in 0 until word.size - 1) {
                val r = ranks[word[i] to word[i + 1]] ?: continue
                if (r < bestRank) { bestRank = r; best = word[i] to word[i + 1] }
            }
            if (best == null) break
            val merged = ArrayList<String>(word.size)
            var i = 0
            while (i < word.size) {
                if (i < word.size - 1 && word[i] == best.first && word[i + 1] == best.second) {
                    merged += best.first + best.second
                    i += 2
                } else {
                    merged += word[i]
                    i++
                }
            }
            word = merged
        }
        if (cache.size < 20_000) cache[piece] = word
        word
    }
}
