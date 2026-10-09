package com.example.sipatkilatis

import android.app.Application
import android.content.Context
import com.example.sipatkilatis.capture.MessageNotificationListener
import com.example.sipatkilatis.capture.MessageScreener
import com.example.sipatkilatis.capture.ScamAlerts
import com.example.sipatkilatis.data.AppPreferences
import com.example.sipatkilatis.data.RoomScanRepository
import com.example.sipatkilatis.data.db.AppDatabase
import com.example.sipatkilatis.data.ScanRepository
import com.example.sipatkilatis.detection.LlmExplainer
import com.example.sipatkilatis.detection.OnDeviceScamDetector
import com.example.sipatkilatis.detection.TemplateExplainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * One instance of everything per app process, shared by the screens AND the background SMS / notification
 * screening (which can run while no screen is open).
 */
class AppGraph(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val prefs = AppPreferences(context)
    val db = AppDatabase.create(context)                 // on-device only
    val repo: ScanRepository = RoomScanRepository(db, appScope)

    val protectionOn = MutableStateFlow(prefs.protectionOn)
    val sensitivity = MutableStateFlow(prefs.sensitivity)
    val appearance = MutableStateFlow(prefs.appearance)

    /**
     * Text shared into the app that the Check screen has not shown yet. Lives here (not in the ViewModel) so it
     * survives the screen being destroyed right after the share arrives (e.g. the system clearing the task).
     */
    val pendingShare = MutableStateFlow<String?>(null)

    /** Real on-device engine; reads the current sensitivity and trusted contacts on every scan. */
    val detector = OnDeviceScamDetector(context, { sensitivity.value }, { repo.trustedNow() })
    val alerts = ScamAlerts(context, prefs)
    val screener = MessageScreener(this)

    // Explanations: instant template, optionally replaced by the local LLM (phase 6)
    val templateExplainer = TemplateExplainer()
    val llmExplainer = LlmExplainer(context)
    val aiExplanations = MutableStateFlow(prefs.aiExplanations)
}

class SipatApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.alerts.createChannels()
        MessageNotificationListener.ensureConnected(this)   // MIUI may not reconnect it after an app update
        // Load the ONNX models in the background so the first scan (manual or incoming SMS) is fast
        graph.appScope.launch { graph.detector.warmUp() }
    }

    /**
     * App went to the background (or memory is tight): unload Gemma (~1 GB) so Android is less likely to kill
     * the process, which also runs SMS screening. The small detection models stay loaded. Gemma reloads on the
     * next explanation; reset() is skipped automatically if an explanation is still being written.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) graph.appScope.launch { graph.llmExplainer.reset() }
    }
}

/** Shortcut: context.graph from any Activity / Receiver / Service. */
val Context.graph: AppGraph get() = (applicationContext as SipatApp).graph
