package com.example.sipatkilatis.detection

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import kotlin.math.exp

private const val TAG = "SipatKilatis"

/**
 * Copies a model from assets to app storage once (ONNX Runtime loads from a file path, which avoids
 * holding a second 100 MB copy in memory). Re-copies if the APK ships a different-sized model.
 */
internal fun modelFile(context: Context, assetName: String): File {
    val out = File(context.filesDir, "models/$assetName")
    val assetSize = context.assets.openFd(assetName).use { it.length }
    if (!out.exists() || out.length() != assetSize) {
        out.parentFile?.mkdirs()
        context.assets.open(assetName).use { input -> out.outputStream().use { input.copyTo(it, 1 shl 16) } }
    }
    return out
}

/** Fine-tuned RoBERTa-tagalog (int8). Input: token ids of the normalized text. */
class TransformerClassifier(private val session: OrtSession, private val tokenizer: RobertaTokenizer) {
    private val env = OrtEnvironment.getEnvironment()

    fun scamProbability(normalized: String): Float {
        val ids = tokenizer.encode(normalized)
        val shape = longArrayOf(1, ids.size.toLong())
        OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape).use { idsTensor ->
            OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(ids.size) { 1L }), shape).use { mask ->
                session.run(mapOf("input_ids" to idsTensor, "attention_mask" to mask)).use { out ->
                    @Suppress("UNCHECKED_CAST")
                    val logits = (out[0].value as Array<FloatArray>)[0]
                    // softmax over [ham, scam]; index 1 = scam
                    val max = maxOf(logits[0], logits[1])
                    val e0 = exp(logits[0] - max)
                    val e1 = exp(logits[1] - max)
                    return e1 / (e0 + e1)
                }
            }
        }
    }
}

/** TF-IDF + logistic regression (skl2onnx). Input: the normalized text as a [1, 1] string tensor. */
class BaselineClassifier(private val session: OrtSession) {
    private val env = OrtEnvironment.getEnvironment()

    fun scamProbability(normalized: String): Float {
        OnnxTensor.createTensor(env, arrayOf(normalized), longArrayOf(1, 1)).use { input ->
            session.run(mapOf("text" to input)).use { out ->
                @Suppress("UNCHECKED_CAST")
                val probs = out.get("probabilities").get().value as Array<FloatArray>
                return probs[0][1]
            }
        }
    }
}

/**
 * ML score = average of RoBERTa and the baseline (see CLAUDE.md). If one model fails to load, the other
 * is used alone; if both fail, returns null and the risk scorer uses URL + rules only.
 * Models load lazily, once, off the main thread.
 */
class EnsembleClassifier(private val context: Context) : MlClassifier {
    private val mutex = Mutex()
    private var loaded = false
    private var transformer: TransformerClassifier? = null
    private var baseline: BaselineClassifier? = null

    /** Last individual scores, for logging and the result screen. */
    @Volatile var lastParts: Pair<Float?, Float?> = null to null
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (loaded) return@withLock
            val env = OrtEnvironment.getEnvironment()
            val t0 = System.currentTimeMillis()
            transformer = runCatching {
                val tokenizer = context.assets.open("tokenizer/vocab.json").use { v ->
                    context.assets.open("tokenizer/merges.txt").use { m -> RobertaTokenizer.load(v, m) }
                }
                val session = env.createSession(modelFile(context, "scam_classifier_int8.onnx").path, OrtSession.SessionOptions())
                TransformerClassifier(session, tokenizer)
            }.onFailure { Log.e(TAG, "RoBERTa model failed to load; using baseline only", it) }.getOrNull()
            baseline = runCatching {
                BaselineClassifier(env.createSession(modelFile(context, "baseline.onnx").path, OrtSession.SessionOptions()))
            }.onFailure { Log.e(TAG, "Baseline model failed to load", it) }.getOrNull()
            loaded = true
            Log.d(TAG, "Models loaded in ${System.currentTimeMillis() - t0} ms " +
                "(roberta=${transformer != null}, baseline=${baseline != null})")
        }
    }

    override suspend fun scamProbability(text: String): Float? {
        load()
        val normalized = TextNormalizer.normalize(text)
        return withContext(Dispatchers.Default) {
            val t = runCatching { transformer?.scamProbability(normalized) }
                .onFailure { Log.e(TAG, "RoBERTa inference failed", it) }.getOrNull()
            val b = runCatching { baseline?.scamProbability(normalized) }
                .onFailure { Log.e(TAG, "Baseline inference failed", it) }.getOrNull()
            lastParts = t to b
            listOfNotNull(t, b).takeIf { it.isNotEmpty() }?.average()?.toFloat()
        }
    }
}
