package com.example.sipatkilatis.detection

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sipatkilatis.graph
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Measures the local LLM explainer on a real phone (skipped if the model file is not installed).
 * Logs load time, time to first token, total time, and the text, WITHOUT the app's 8 s limit,
 * so we learn the real speed. Run with adb (not Gradle, which uninstalls the app and deletes the model):
 *   adb shell am instrument -w -e class com.example.sipatkilatis.detection.LlmOnDeviceTest \
 *       com.example.sipatkilatis.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class LlmOnDeviceTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun explainOnDevice() = runBlocking {
        val graph = app.graph
        val llm = graph.llmExplainer
        assumeTrue("model not installed", llm.isInstalled)

        val t0 = System.currentTimeMillis()
        check(llm.load()) { "LLM failed to load" }
        Log.d("SipatKilatis", "TEST llm load ${System.currentTimeMillis() - t0} ms")

        val cases = listOf(
            "GCash: Naka-lock ang account mo. I-verify agad sa gcash-verify.xyz/login" to true,
            "Shopee: Claim your voucher at sh0pee-rewards.com" to false,
            "GCash: Naka-lock ang account mo. I-reply ang 6-digit OTP na natanggap mo para ma-unlock agad." to true,
        )
        for ((text, filipino) in cases) {
            val result = graph.detector.detect(text)
            val start = System.currentTimeMillis()
            val parts = withTimeout(90_000) { llm.stream(result, filipino).toList() }
            val ms = System.currentTimeMillis() - start
            val answer = parts.lastOrNull().orEmpty()
            Log.d("SipatKilatis", "TEST ${result.verdict} filipino=$filipino total=$ms ms words=${answer.split(Regex("\\s+")).size} safe=${ExplanationSafety.isSafe(answer)}")
            answer.lines().forEach { Log.d("SipatKilatis", "TEST   | $it") }
        }
    }
}
