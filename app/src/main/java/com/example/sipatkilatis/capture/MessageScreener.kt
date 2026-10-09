package com.example.sipatkilatis.capture

import android.util.Log
import com.example.sipatkilatis.AppGraph
import com.example.sipatkilatis.model.MessageSource

/**
 * Common path for every incoming message (SMS or chat notification):
 * protection on? -> not a duplicate? -> detect -> save to history -> alert if SUSPICIOUS / SCAM.
 */
class MessageScreener(private val graph: AppGraph) {

    private val recent = LinkedHashMap<String, Long>()   // message text -> when it was last screened

    /** Returns false if the message was skipped (protection off, empty, or a duplicate). */
    suspend fun screen(sender: String?, text: String, source: MessageSource): Boolean {
        val body = text.trim()
        if (!graph.protectionOn.value || body.isEmpty()) return false
        if (isDuplicate(body)) {
            Log.d(TAG, "skip duplicate from $source")
            return false
        }
        val result = graph.detector.detect(body, sender)
        val record = graph.repo.addScan(result, source)
        graph.alerts.show(record)
        return true
    }

    /**
     * The same text arriving twice within [WINDOW_MS] is one message: e.g. the SMS receiver AND the
     * SMS app's notification, or a chat app re-posting its notification.
     */
    @Synchronized
    private fun isDuplicate(text: String): Boolean {
        val now = System.currentTimeMillis()
        recent.entries.removeAll { now - it.value > WINDOW_MS }
        val key = text.lowercase().replace(Regex("\\s+"), " ")
        if (recent.containsKey(key)) return true
        recent[key] = now
        return false
    }

    companion object {
        private const val TAG = "SipatKilatis"
        const val WINDOW_MS = 10_000L
    }
}
