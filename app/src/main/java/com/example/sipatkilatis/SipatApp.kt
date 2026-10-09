package com.example.sipatkilatis

import android.app.Application
import android.content.Context
import com.example.sipatkilatis.capture.MessageScreener
import com.example.sipatkilatis.capture.ScamAlerts
import com.example.sipatkilatis.data.AppPreferences
import com.example.sipatkilatis.data.FakeScanRepository
import com.example.sipatkilatis.data.ScanRepository
import com.example.sipatkilatis.detection.OnDeviceScamDetector
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
    val repo: ScanRepository = FakeScanRepository()      // phase 7: Room

    val protectionOn = MutableStateFlow(prefs.protectionOn)
    val sensitivity = MutableStateFlow(prefs.sensitivity)

    /** Real on-device engine; reads the current sensitivity and trusted contacts on every scan. */
    val detector = OnDeviceScamDetector(context, { sensitivity.value }, { repo.trustedContacts.value })
    val alerts = ScamAlerts(context, prefs)
    val screener = MessageScreener(this)
}

class SipatApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.alerts.createChannels()
        // Load the ONNX models in the background so the first scan (manual or incoming SMS) is fast
        graph.appScope.launch { graph.detector.warmUp() }
    }
}

/** Shortcut: context.graph from any Activity / Receiver / Service. */
val Context.graph: AppGraph get() = (applicationContext as SipatApp).graph
