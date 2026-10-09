package com.example.sipatkilatis.capture

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.sipatkilatis.graph
import com.example.sipatkilatis.model.MessageSource
import kotlinx.coroutines.launch

/**
 * Reads incoming notifications from messaging and e-wallet apps and screens the message text.
 * Only the apps below are read; every other notification is ignored. Nothing is stored or sent anywhere
 * except the on-device scan history.
 *
 * The default SMS app is included on purpose: if an SMS slips past SmsReceiver, its notification still gets
 * checked. MessageScreener drops the duplicate when both see the same text within 10 seconds.
 */
class MessageNotificationListener : NotificationListenerService() {

    private val watchedApps = setOf(
        "com.facebook.orca",            // Messenger
        "com.facebook.mlite",           // Messenger Lite
        "com.viber.voip",               // Viber
        "org.telegram.messenger",       // Telegram
        "com.whatsapp", "com.whatsapp.w4b",
        "com.globe.gcash.android",      // GCash
        "com.paymaya",                  // Maya
    )

    // Notification key + text already screened, so a chat app re-posting the same notification is ignored
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > 300
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return                                      // skip our own alerts
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        if (pkg !in watchedApps && pkg != defaultSms) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return       // "3 new messages" summaries
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return        // calls, uploads, etc.

        val (sender, text) = extract(n) ?: return
        synchronized(seen) {
            val key = "${sbn.key}|${text.hashCode()}"
            if (seen.containsKey(key)) return
            seen[key] = true
        }
        val graph = applicationContext.graph
        graph.appScope.launch {
            runCatching { graph.screener.screen(sender, text, MessageSource.NOTIFICATION) }
                .onFailure { Log.e("SipatKilatis", "Notification screening failed ($pkg)", it) }
        }
    }

    /** Sender + text of the newest message. Chat apps use MessagingStyle; others put it in title / text. */
    private fun extract(n: Notification): Pair<String?, String>? {
        val extras = n.extras
        // NotificationCompat reads MessagingStyle on every Android version we support (8+)
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
        val last = style?.messages?.lastOrNull()
        val styleText = last?.text?.toString()
        if (!styleText.isNullOrBlank()) {
            val sender = last.person?.name?.toString()
                ?: style.conversationTitle?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            return sender to styleText
        }
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.takeIf { it.isNotBlank() } ?: return null
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() to text
    }
}
