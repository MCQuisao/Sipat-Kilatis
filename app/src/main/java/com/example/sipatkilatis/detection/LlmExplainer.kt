// MediaPipe's LLM Inference API is deprecated in favour of LiteRT-LM (.litertlm models). It still works and is
// what the project plan specifies; switching runtimes is a later, optional change.
@file:Suppress("DEPRECATION")

package com.example.sipatkilatis.detection

import android.content.Context
import android.util.Log
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Verdict
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Short explanation written by a small local LLM (Gemma 3 1B, int4) with the MediaPipe LLM Inference API.
 * Runs only for SUSPICIOUS / SCAM. Loads lazily on first use, off the main thread, and stays loaded.
 * The caller applies the hard time limit and the safety check ([ExplanationSafety]) and falls back to
 * [TemplateExplainer] (see MainViewModel). The answer is shown only when complete (not streamed), so an unsafe
 * half-sentence can never flash on screen.
 *
 * Model file (too big for the APK, pushed with adb):
 *   adb push gemma3-1b-it-int4.task /sdcard/Android/data/com.example.sipatkilatis/files/llm/model.task
 */
class LlmExplainer(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var llm: LlmInference? = null
    private val busy = AtomicInteger(0)   // generations currently running
    private val generationLock = Mutex()  // MediaPipe can only run one generation at a time

    companion object {
        private const val TAG = "SipatKilatis"
        // Hard limit for writing the explanation (after the model is loaded). Measured on a Snapdragon 732G phone:
        // ~13 s per explanation on GPU, so 8 s (the original plan) would never show it. The template is on screen
        // meanwhile, so nobody waits for this.
        const val TIMEOUT_MS = 20_000L
        private const val MAX_TOKENS = 768 // prompt + answer
        private const val MAX_CHUNKS = 200 // output pieces; a normal ~50-word answer needs far fewer
        private const val MAX_WORDS = 70  // stop runaway answers
    }

    val modelFile: File get() = File(appContext.getExternalFilesDir(null), "llm/model.task")
    val isInstalled: Boolean get() = modelFile.exists() && modelFile.length() > 100_000_000

    /** Loads the model once (several seconds the first time). Returns false if it is missing or fails. */
    suspend fun load(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (llm != null) return@withLock true
            if (!isInstalled) return@withLock false
            // GPU reads the prompt much faster than the CPU on phones; fall back to CPU if the GPU path fails
            for (backend in listOf(LlmInference.Backend.GPU, LlmInference.Backend.CPU)) {
                val t0 = System.currentTimeMillis()
                llm = runCatching {
                    LlmInference.createFromOptions(appContext,
                        LlmInference.LlmInferenceOptions.builder()
                            .setModelPath(modelFile.path)
                            .setMaxTokens(MAX_TOKENS)
                            .setMaxTopK(40)
                            .setPreferredBackend(backend)
                            .build())
                }.onFailure { Log.e(TAG, "LLM failed to load on $backend", it) }.getOrNull()
                Log.d(TAG, "LLM loaded=${llm != null} on $backend in ${System.currentTimeMillis() - t0} ms")
                if (llm != null) break
            }
            llm != null
        }
    }

    /**
     * Streams the explanation: each emission is the full text so far (cleaned). Completes when the model is done.
     * Cancelling the collector stops generation.
     */
    fun stream(result: ScanResult, filipino: Boolean): Flow<String> = callbackFlow {
        // MediaPipe rules (breaking them crashes the app, often from native code):
        //  - ONE generation at a time per engine -> [generationLock] is held until the previous one fully stopped
        //  - no other LLM calls (e.g. sizeInTokens) while a generation is running -> count tokens BEFORE starting
        //  - never let an exception escape the progress callback
        //  - don't close the session until it reported done=true (also after a cancel)
        generationLock.lock()
        val engine: LlmInference
        val session: LlmInferenceSession
        val prompt = buildPrompt(result, filipino)
        val promptTokens: Int
        val generationDone = CompletableDeferred<Unit>()
        try {
            engine = llm ?: throw IllegalStateException("LLM not loaded")
            promptTokens = runCatching { engine.sizeInTokens(prompt) }.getOrDefault(-1)
            session = LlmInferenceSession.createFromOptions(engine,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTemperature(0.3f)    // low: stick to the facts we give it
                    .setRandomSeed(7)
                    .build())
        } catch (t: Throwable) {
            generationLock.unlock()
            throw t   // the caller falls back to the template
        }
        busy.incrementAndGet()
        val start = System.currentTimeMillis()
        var firstTokenMs = -1L
        var chunks = 0
        var stopped = false
        var started = false
        val text = StringBuilder()
        try {
            session.addQueryChunk(prompt)
            session.generateResponseAsync { partial, done ->
                try {
                    if (done) generationDone.complete(Unit)
                    if (stopped) return@generateResponseAsync
                    if (firstTokenMs < 0) firstTokenMs = System.currentTimeMillis() - start
                    chunks++
                    text.append(partial ?: "")
                    val cleaned = clean(text.toString())
                    trySend(cleaned)
                    val words = cleaned.split(Regex("\\s+")).size
                    // Stop on: model finished, answer too long, or endless near-empty output (seen on some phones)
                    if (done || words > MAX_WORDS || chunks > MAX_CHUNKS) {
                        stopped = true
                        Log.d(TAG, "LLM explanation: first token $firstTokenMs ms, total ${System.currentTimeMillis() - start} ms, " +
                            "$words words, prompt $promptTokens tokens")
                        channel.close()   // ends the flow; awaitClose below cancels (if needed) and cleans up
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "LLM callback error", t)
                    channel.close(t)
                }
            }
            started = true
        } catch (t: Throwable) {
            Log.e(TAG, "LLM could not start generating", t)
            channel.close(t)
        }
        awaitClose {
            if (started && !generationDone.isCompleted) runCatching { session.cancelGenerateResponseAsync() }
            // Wait for MediaPipe to confirm it stopped before closing; if it never does, leave the session open
            // (a small leak) rather than crash the app.
            val stoppedCleanly = !started || runBlocking { withTimeoutOrNull(5_000) { generationDone.await() } } != null
            if (stoppedCleanly) runCatching { session.close() }
            else Log.w(TAG, "LLM session did not stop after cancel; not closing it")
            busy.decrementAndGet()
            generationLock.unlock()   // next explanation may start
        }
    }.flowOn(Dispatchers.Default)

    /**
     * The prompt. Gives the model ONLY the message, the verdict, and the detector's warning signs, and tells it
     * not to add anything else. Wrapped in Gemma's chat format.
     */
    fun buildPrompt(result: ScanResult, filipino: Boolean): String {
        // Kept short on purpose: on a mid-range phone, reading the prompt is the slowest part.
        val verdict = if (result.verdict == Verdict.SCAM) "a scam" else "suspicious"
        val signs = result.flags.take(3).joinToString("; ") { (if (filipino) it.reasonFil else it.reasonEn).lowercase() }
        val message = result.text.replace(Regex("\\s+"), " ").take(200)
        // A 1B model follows the language of the instruction better than "answer in X", so the Filipino
        // prompt is itself written in Tagalog.
        val user = if (filipino) {
            val hatol = if (result.verdict == Verdict.SCAM) "scam" else "kahina-hinala"
            "Sa Tagalog lang sumagot. Ipaliwanag sa 2 maikling pangungusap kung bakit $hatol ang text na ito. " +
                "Gamitin lang ang mga babalang ito: $signs. Pagkatapos, magbigay ng isang payo na nagsisimula sa \"Payo:\". " +
                "Simpleng salita, walang markdown, huwag mag-imbento.\nText: \"$message\""
        } else {
            "In simple English, explain in 2 short sentences why this text is $verdict. " +
                "Use only these warning signs: $signs. Then add one safety tip starting with \"Tip:\". " +
                "Plain words, no markdown, do not invent details.\nText: \"$message\""
        }
        return "<start_of_turn>user\n$user<end_of_turn>\n<start_of_turn>model\n"
    }

    /** Remove markdown and model artifacts so it reads as plain text in the app. */
    private fun clean(s: String): String = s
        .replace("<end_of_turn>", "")
        .replace(Regex("\\*\\*|__|#+\\s*"), "")
        .replace(Regex("(?m)^\\s*[*â€¢]\\s+"), "- ")
        .trim()

    /**
     * Drop the loaded model, e.g. after it stalled; the next explanation reloads it.
     * Skipped while a generation is still running: closing the engine under it would crash the app.
     */
    suspend fun reset() = mutex.withLock {
        if (busy.get() > 0) {
            Log.w(TAG, "LLM reset skipped: a generation is still running")
            return@withLock
        }
        runCatching { llm?.close() }
        llm = null
    }
}
