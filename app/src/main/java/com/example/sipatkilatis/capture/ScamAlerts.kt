package com.example.sipatkilatis.capture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.sipatkilatis.MainActivity
import com.example.sipatkilatis.R
import com.example.sipatkilatis.data.AppPreferences
import com.example.sipatkilatis.graph
import com.example.sipatkilatis.model.Feedback
import com.example.sipatkilatis.model.ScanRecord
import com.example.sipatkilatis.model.Verdict
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Warning notifications:
 *  - SCAM: high-importance channel -> heads-up pop-up, with "View details" and "Mark as safe"
 *  - SUSPICIOUS: normal-importance channel
 *  - SAFE: no notification (only saved to history)
 */
class ScamAlerts(private val context: Context, private val prefs: AppPreferences) {

    companion object {
        const val CHANNEL_SCAM = "scam_alerts"
        const val CHANNEL_SUSPICIOUS = "suspicious_alerts"
        const val EXTRA_SCAN_ID = "scan_id"
        const val ACTION_MARK_SAFE = "com.example.sipatkilatis.MARK_SAFE"
    }

    /** Context whose strings follow the language chosen in the app (background code has no Activity). */
    fun localized(): Context {
        val lang = prefs.language ?: return context
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }
        return context.createConfigurationContext(config)
    }

    fun createChannels() {
        val ctx = localized()
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SCAM, ctx.getString(R.string.channel_scam), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = ctx.getString(R.string.channel_scam_desc) })
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUSPICIOUS, ctx.getString(R.string.channel_suspicious), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = ctx.getString(R.string.channel_suspicious_desc) })
    }

    fun show(record: ScanRecord) {
        if (record.verdict == Verdict.SAFE) return
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return   // POST_NOTIFICATIONS not granted: result is still in History

        val ctx = localized()
        val filipino = (prefs.language ?: Locale.getDefault().language).let { it == "fil" || it == "tl" }
        val scam = record.verdict == Verdict.SCAM
        val reason = record.result?.flags?.firstOrNull()?.let { if (filipino) it.reasonFil else it.reasonEn }
            ?: ctx.getString(R.string.alert_generic_reason)
        val from = ctx.getString(R.string.alert_from, record.sender)
        val id = record.id.toInt()

        val viewIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_SCAN_ID, record.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markSafeIntent = PendingIntent.getBroadcast(
            context, id,
            Intent(context, AlertActionReceiver::class.java).setAction(ACTION_MARK_SAFE).putExtra(EXTRA_SCAN_ID, record.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, if (scam) CHANNEL_SCAM else CHANNEL_SUSPICIOUS)
            .setSmallIcon(R.drawable.ic_stat_shield)
            .setContentTitle(ctx.getString(if (scam) R.string.alert_scam_title else R.string.alert_suspicious_title))
            .setContentText("$from · $reason")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$from\n$reason\n\n“${record.text.take(160)}”"))
            .setPriority(if (scam) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(viewIntent)
            .setAutoCancel(true)
            // IMPORTANT: stop Android adding its automatic "Open link" button, which would open the scam link
            .setAllowSystemGeneratedContextualActions(false)
            .addAction(0, ctx.getString(R.string.alert_view), viewIntent)
            .addAction(0, ctx.getString(R.string.alert_mark_safe), markSafeIntent)
            .build()
        try {
            nm.notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do
        }
    }
}

/** "Mark as safe" on an alert: trust the sender, record the correction, dismiss the alert. */
class AlertActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScamAlerts.ACTION_MARK_SAFE) return
        val id = intent.getLongExtra(ScamAlerts.EXTRA_SCAN_ID, -1)
        NotificationManagerCompat.from(context).cancel(id.toInt())
        val graph = context.graph
        Toast.makeText(graph.alerts.localized(), R.string.alert_marked_safe, Toast.LENGTH_SHORT).show()
        val pending = goAsync()   // database work happens off the main thread
        graph.appScope.launch {
            try {
                val record = graph.repo.find(id)
                if (record != null) {
                    graph.repo.addTrustedContact(record.sender)
                    graph.repo.setFeedback(id, Feedback.MARKED_SAFE)
                }
            } finally {
                pending.finish()
            }
        }
    }
}