package com.example.sipatkilatis.detection

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sipatkilatis.graph
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the local LLM explainer on a real phone the same way the app does (20 s limit, reset after a timeout,
 * safety check), twice over messages that include one that makes Gemma stall. Passing = the app survives.
 * Skipped if the model file is not installed. Run with adb (not Gradle, which uninstalls the app and deletes
 * the pushed model):
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

        val cases = listOf(
            "GCash: Naka-lock ang account mo. I-verify agad sa gcash-verify.xyz/login" to true,
            "Shopee: Claim your voucher at sh0pee-rewards.com" to false,
            "GCash: Naka-lock ang account mo. I-reply ang 6-digit OTP na natanggap mo para ma-unlock agad." to true,
        )
        repeat(2) { round ->
            for ((text, filipino) in cases) {
                val t0 = System.currentTimeMillis()
                check(llm.load()) { "LLM failed to load" }
                val loadMs = System.currentTimeMillis() - t0
                val result = graph.detector.detect(text)
                val start = System.currentTimeMillis()
                var answer = ""
                val finished = withTimeoutOrNull(LlmExplainer.TIMEOUT_MS) {
                    llm.stream(result, filipino).collect { answer = it }
                    true
                }
                val ms = System.currentTimeMillis() - start
                if (finished != true) llm.reset()   // same as the app after a timeout
                Log.d("SipatKilatis", "TEST round=$round filipino=$filipino load=$loadMs ms gen=$ms ms " +
                    "finished=${finished == true} safe=${ExplanationSafety.isSafe(answer)} words=${answer.split(Regex("\\s+")).size}")
                answer.lines().forEach { Log.d("SipatKilatis", "TEST   | $it") }
            }
        }
    }

    /**
     * What crashed the app: a new explanation starting while the previous one is still running
     * (user scans again or opens another result before Gemma finishes). Each one is cancelled part-way and the
     * next starts immediately; the last one must still finish normally.
     */
    @Test
    fun overlappingExplanationsDoNotCrash() = runBlocking {
        val graph = app.graph
        val llm = graph.llmExplainer
        assumeTrue("model not installed", llm.isInstalled)
        check(llm.load()) { "LLM failed to load" }
        val result = graph.detector.detect("Shopee: Claim your voucher at sh0pee-rewards.com")

        repeat(4) { i ->
            val job = launch(kotlinx.coroutines.Dispatchers.Default) {
                runCatching { llm.stream(result, filipino = false).collect { } }
            }
            kotlinx.coroutines.delay(listOf(300L, 1_500L, 4_000L, 50L)[i])   // cancel at different moments
            job.cancel()                                                    // do NOT wait: start the next at once
            Log.d("SipatKilatis", "TEST overlap: cancelled run $i")
        }
        var answer = ""
        val finished = withTimeoutOrNull(60_000) {
            llm.stream(result, filipino = false).collect { answer = it }
            true
        }
        Log.d("SipatKilatis", "TEST overlap: final finished=${finished == true} words=${answer.split(Regex("\\s+")).size}")
        check(finished == true && answer.isNotBlank()) { "final explanation did not finish" }
    }
}
