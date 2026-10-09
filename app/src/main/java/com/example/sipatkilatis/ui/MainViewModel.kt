package com.example.sipatkilatis.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sipatkilatis.detection.ExplanationSafety
import com.example.sipatkilatis.detection.LlmExplainer
import com.example.sipatkilatis.graph
import com.example.sipatkilatis.model.MessageSource
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Sensitivity
import com.example.sipatkilatis.model.Verdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * State for the screens. The detector, history, and settings live in the app-wide [com.example.sipatkilatis.AppGraph]
 * so background SMS / notification screening and the UI see the same data.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = app.graph
    private val prefs = graph.prefs
    private val repo = graph.repo

    val history = repo.history
    val trustedContacts = repo.trustedContacts
    val protectionOn = graph.protectionOn
    val sensitivity = graph.sensitivity
    val onboardingDone = MutableStateFlow(prefs.onboardingDone)

    /** Text in the "Check a message" box (also filled by the share sheet). */
    val draftText = MutableStateFlow("")

    /** Set when another app shares text into Sipat Kilatis; the nav host opens the Check screen. */
    private val _pendingShare = MutableStateFlow(false)
    val pendingShare = _pendingShare.asStateFlow()

    /** Set when the user taps a scam alert; the nav host opens the Result screen. */
    private val _pendingResult = MutableStateFlow(false)
    val pendingResult = _pendingResult.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()

    private val _lastResult = MutableStateFlow<ScanResult?>(null)
    val lastResult = _lastResult.asStateFlow()

    /** Explanation on the Result screen: template first, then (maybe) the local LLM's text, streamed. */
    private val _explanation = MutableStateFlow(ExplanationUi(""))
    val explanation = _explanation.asStateFlow()
    private var explainJob: Job? = null

    val aiExplanations = graph.aiExplanations
    val llmInstalled get() = graph.llmExplainer.isInstalled
    val llmSizeMb get() = (graph.llmExplainer.modelFile.length() / 1_000_000).toInt()

    fun setAiExplanations(on: Boolean) {
        prefs.aiExplanations = on
        aiExplanations.value = on
    }

    /** Show a result and start its explanation. */
    private fun showResult(result: ScanResult) {
        _lastResult.value = result
        explain(result)
    }

    /**
     * 1. Template explanation immediately (always works).
     * 2. For SUSPICIOUS / SCAM, if enabled and installed: load the LLM (once) and let it write an explanation.
     *    Shown only when complete AND it passes [ExplanationSafety]; otherwise the template stays.
     *    Hard time limit [LlmExplainer.TIMEOUT_MS]; a timeout also resets the model (it can stall on some phones).
     */
    private fun explain(result: ScanResult) {
        explainJob?.cancel()
        val filipino = AppLanguage.current() == AppLanguage.FILIPINO
        val template = graph.templateExplainer.build(result, filipino)
        _explanation.value = ExplanationUi(template)
        val llm = graph.llmExplainer
        if (result.verdict == Verdict.SAFE || !aiExplanations.value || !llm.isInstalled) return

        explainJob = viewModelScope.launch {
            _explanation.value = ExplanationUi(template, generating = true)
            val loaded = withTimeoutOrNull(60_000) { llm.load() } ?: false
            if (!loaded) {
                _explanation.value = ExplanationUi(template)
                return@launch
            }
            var text = ""
            val finished = try {
                withTimeoutOrNull(LlmExplainer.TIMEOUT_MS) {
                    llm.stream(result, filipino).collect { text = it }   // collected, but not shown until done
                    true
                }
            } catch (e: CancellationException) {
                throw e   // a newer result replaced this one
            } catch (e: Exception) {
                // Any LLM problem must never crash the app: keep the template
                Log.e("SipatKilatis", "LLM explanation failed; using template", e)
                _explanation.value = ExplanationUi(template)
                return@launch
            }
            _explanation.value = when {
                finished != true -> {
                    Log.d("SipatKilatis", "LLM explanation timed out after ${LlmExplainer.TIMEOUT_MS} ms; using template")
                    launch { llm.reset() }
                    ExplanationUi(template)
                }
                text.isBlank() -> ExplanationUi(template)
                !ExplanationSafety.isSafe(text) -> {
                    Log.d("SipatKilatis", "LLM explanation rejected by safety check; using template")
                    ExplanationUi(template)
                }
                else -> ExplanationUi(text, fromAi = true)
            }
        }
    }

    fun finishOnboarding() {
        prefs.onboardingDone = true
        onboardingDone.value = true
    }

    fun setProtection(on: Boolean) {
        prefs.protectionOn = on
        protectionOn.value = on
    }

    fun setSensitivity(value: Sensitivity) {
        prefs.sensitivity = value
        sensitivity.value = value
    }

    fun onSharedText(text: String) {
        draftText.value = text
        _pendingShare.value = true
    }

    fun shareHandled() {
        _pendingShare.value = false
    }

    /** Show a saved scan (from an alert or the History list). Returns false if it is no longer in memory. */
    fun openScan(id: Long, fromAlert: Boolean = false): Boolean {
        val result = repo.find(id)?.result ?: return false
        showResult(result)
        if (fromAlert) _pendingResult.value = true
        return true
    }

    fun resultHandled() {
        _pendingResult.value = false
    }

    /** Runs the detector, saves the scan to history, then calls [onDone] to show the result. */
    fun scan(onDone: () -> Unit) {
        val text = draftText.value.trim()
        if (text.isEmpty() || _scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val result = graph.detector.detect(text)
            repo.addScan(result, MessageSource.MANUAL)
            showResult(result)
            _scanning.value = false
            onDone()
        }
    }

    fun addTrustedContact(sender: String) = repo.addTrustedContact(sender)
    fun removeTrustedContact(sender: String) = repo.removeTrustedContact(sender)
}

/** What the Result screen's explanation card shows. */
data class ExplanationUi(val text: String, val fromAi: Boolean = false, val generating: Boolean = false)
