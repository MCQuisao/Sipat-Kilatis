package com.example.sipatkilatis.detection

import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The Kotlin preprocessing must produce exactly what Python produced when the models were exported,
 * otherwise the phone gets different scores. Test vectors come from ml/train_baseline.py and ml/export_onnx.py.
 */
class ParityTest {

    private fun vectors(name: String): JSONArray =
        JSONArray(javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader(Charsets.UTF_8).readText())

    @Test
    fun normalizerMatchesPython() {
        for (file in listOf("baseline_test_vectors.json", "transformer_test_vectors.json")) {
            val v = vectors(file)
            for (i in 0 until v.length()) {
                val item = v.getJSONObject(i)
                assertEquals("[$file] ${item.getString("text")}",
                    item.getString("normalized"), TextNormalizer.normalize(item.getString("text")))
            }
        }
    }

    @Test
    fun tokenizerMatchesPython() {
        // Unit tests run with the app module as working directory
        val tokenizer = File("src/main/assets/tokenizer/vocab.json").inputStream().use { v ->
            File("src/main/assets/tokenizer/merges.txt").inputStream().use { m -> RobertaTokenizer.load(v, m) }
        }
        val v = vectors("transformer_test_vectors.json")
        for (i in 0 until v.length()) {
            val item = v.getJSONObject(i)
            val expected = item.getJSONArray("input_ids").let { ids -> LongArray(ids.length()) { ids.getLong(it) } }
            assertArrayEquals(item.getString("text"), expected, tokenizer.encode(item.getString("normalized")))
        }
    }

    @Test
    fun tokenizerTruncatesTo128() {
        val tokenizer = File("src/main/assets/tokenizer/vocab.json").inputStream().use { v ->
            File("src/main/assets/tokenizer/merges.txt").inputStream().use { m -> RobertaTokenizer.load(v, m) }
        }
        val ids = tokenizer.encode("salamat ".repeat(300))
        assertEquals(RobertaTokenizer.MAX_LEN, ids.size)
        assertEquals(RobertaTokenizer.BOS.toLong(), ids.first())
        assertEquals(RobertaTokenizer.EOS.toLong(), ids.last())
    }
}
