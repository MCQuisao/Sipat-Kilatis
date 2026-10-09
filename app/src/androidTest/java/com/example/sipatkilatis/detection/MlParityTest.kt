package com.example.sipatkilatis.detection

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sipatkilatis.model.Sensitivity
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a phone / emulator with the real ONNX Runtime for Android.
 * Both models must give the same P(scam) as Python (test vectors) within 0.02.
 */
@RunWith(AndroidJUnit4::class)
class MlParityTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext      // app assets (models)
    private val testCtx = InstrumentationRegistry.getInstrumentation().context        // test assets (vectors)
    private val env = OrtEnvironment.getEnvironment()

    private fun vectors(name: String) = JSONArray(testCtx.assets.open(name).bufferedReader().readText())

    @Test
    fun transformerMatchesPython() {
        val tokenizer = app.assets.open("tokenizer/vocab.json").use { v ->
            app.assets.open("tokenizer/merges.txt").use { m -> RobertaTokenizer.load(v, m) }
        }
        val session = env.createSession(modelFile(app, "scam_classifier_int8.onnx").path, OrtSession.SessionOptions())
        val model = TransformerClassifier(session, tokenizer)
        val v = vectors("transformer_test_vectors.json")
        for (i in 0 until v.length()) {
            val item = v.getJSONObject(i)
            val t = System.nanoTime()
            val p = model.scamProbability(TextNormalizer.normalize(item.getString("text")))
            Log.d("SipatKilatis", "roberta %.4f (python %.4f) %d ms | %s".format(p, item.getDouble("scam_probability"),
                (System.nanoTime() - t) / 1_000_000, item.getString("text")))
            assertEquals(item.getString("text"), item.getDouble("scam_probability").toFloat(), p, 0.02f)
        }
    }

    @Test
    fun baselineMatchesPython() {
        val session = env.createSession(modelFile(app, "baseline.onnx").path, OrtSession.SessionOptions())
        val model = BaselineClassifier(session)
        val v = vectors("baseline_test_vectors.json")
        for (i in 0 until v.length()) {
            val item = v.getJSONObject(i)
            val p = model.scamProbability(TextNormalizer.normalize(item.getString("text")))
            Log.d("SipatKilatis", "baseline %.4f (python %.4f) | %s".format(p, item.getDouble("scam_probability"), item.getString("text")))
            assertEquals(item.getString("text"), item.getDouble("scam_probability").toFloat(), p, 0.02f)
        }
    }

    /** Full engine end to end: verdicts make sense and each scan is under 1 second once models are loaded. */
    @Test
    fun detectorEndToEnd() = runBlocking {
        val detector = OnDeviceScamDetector(app, { Sensitivity.NORMAL }, { listOf("Mama") })
        val loadStart = System.currentTimeMillis()
        detector.warmUp()
        Log.d("SipatKilatis", "warm-up ${System.currentTimeMillis() - loadStart} ms")

        val scam = "GCash: Naka-lock ang account mo. I-verify agad sa gcash-verify.xyz/login para hindi ma-block"
        val ham = "Hi anak, uuwi ako mamaya mga 7pm. May ulam pa ba tayo?"
        val times = mutableListOf<Long>()
        repeat(5) {
            for (text in listOf(scam, ham)) {
                val t = System.currentTimeMillis()
                detector.detect(text)
                times += System.currentTimeMillis() - t
            }
        }
        Log.d("SipatKilatis", "detect times ms: $times (avg ${times.average()})")
        assertTrue("average detect time ${times.average()} ms", times.average() < 1000)

        assertEquals(com.example.sipatkilatis.model.Verdict.SCAM, detector.detect(scam).verdict)
        assertEquals(com.example.sipatkilatis.model.Verdict.SAFE, detector.detect(ham).verdict)
        // Trusted sender is never flagged, even with scam text
        assertEquals(com.example.sipatkilatis.model.Verdict.SAFE, detector.detect(scam, sender = "Mama").verdict)
    }
}
