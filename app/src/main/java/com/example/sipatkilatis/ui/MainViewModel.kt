package com.example.sipatkilatis.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sipatkilatis.data.AppPreferences
import com.example.sipatkilatis.data.FakeScanRepository
import com.example.sipatkilatis.data.ScanRepository
import com.example.sipatkilatis.detection.OnDeviceScamDetector
import com.example.sipatkilatis.model.MessageSource
import com.example.sipatkilatis.model.ScanResult
import com.example.sipatkilatis.model.Sensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** App-wide state shared by all screens (single activity, one ViewModel keeps it simple). */
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPreferences(app)
    private val repo: ScanRepository = FakeScanRepository()      // phase 7: Room

    val history = repo.history
    val trustedContacts = repo.trustedContacts

    val onboardingDone = MutableStateFlow(prefs.onboardingDone)
    val protectionOn = MutableStateFlow(prefs.protectionOn)
    val sensitivity = MutableStateFlow(prefs.sensitivity)

    // Real on-device engine; reads the current sensitivity and trusted contacts on every scan
    private val detector = OnDeviceScamDetector(app, { sensitivity.value }, { trustedContacts.value })

    init {
        // Load the ONNX models in the background so the first scan doesn't wait for them
        viewModelScope.launch { detector.warmUp() }
    }

    /** Text in the "Check a message" box (also filled by the share sheet). */
    val draftText = MutableStateFlow("")

    /** Set when another app shares text into Sipat Kilatis; the nav host opens the Check screen. */
    private val _pendingShare = MutableStateFlow(false)
    val pendingShare = _pendingShare.asStateFlow()

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

    /** Runs the detector, saves the scan to history, then calls [onDone] to show the result. */
    fun scan(onDone: () -> Unit) {
        val text = draftText.value.trim()
        if (text.isEmpty() || _scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            val result = detector.detect(text)
            repo.addScan(result, MessageSource.MANUAL)
            _lastResult.value = result
            _scanning.value = false
            onDone()
        }
    }

    fun addTrustedContact(sender: String) = repo.addTrustedContact(sender)
    fun removeTrustedContact(sender: String) = repo.removeTrustedContact(sender)
}
