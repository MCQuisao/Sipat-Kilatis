package com.example.sipatkilatis.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sipatkilatis.graph
import com.example.sipatkilatis.model.MessageSource
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Sensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
        _lastResult.value = result
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
            _lastResult.value = result
            _scanning.value = false
            onDone()
        }
    }

    fun addTrustedContact(sender: String) = repo.addTrustedContact(sender)
    fun removeTrustedContact(sender: String) = repo.removeTrustedContact(sender)
}
