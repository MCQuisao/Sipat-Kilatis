package com.example.sipatkilatis.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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

    companion object {
        private const val MAX_AGE_MS = 2 * 60 * 1000L
        private const val TAG = "SipatKilatis"

        /** True while Android has this listener connected (it can be ON in Settings yet not connected). */
        @Volatile var connected = false
            private set

        /**
         * Notification access can be ON in Settings while the listener is NOT connected: Android disconnects it
         * when the app is updated, and some phones (seen on Xiaomi / MIUI) never reconnect it, so chat apps stop
         * being screened. Ask Android to reconnect; if that is ignored, briefly disable + re-enable the component,
         * which makes the system bind it again. Call on app start and whenever the app comes to the foreground.
         */
        @Volatile private var checking = false   // one reconnect attempt at a time

        fun ensureConnected(context: Context) {
            if (checking || connected) return
            checking = true
            val app = context.applicationContext
            val handler = Handler(Looper.getMainLooper())
            // Give a normal bind (e.g. right after the app process starts) a moment before stepping in
            handler.postDelayed({ checkAndRebind(app, handler) }, 2_000)
        }

        private fun checkAndRebind(context: Context, handler: Handler) {
            if (connected || !Permissions.hasChatAccess(context)) { checking = false; return }
            val component = ComponentName(context, MessageNotificationListener::class.java)
            Log.d(TAG, "notification access is on but the listener is not connected: requesting rebind")
            runCatching { requestRebind(component) }
            handler.postDelayed({
                checking = false
                if (connected) return@postDelayed
                Log.d(TAG, "rebind ignored: toggling the listener component")
                val pm = context.packageManager
                runCatching {
                    pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP)
                    pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP)
                    requestRebind(component)
                }.onFailure { Log.e(TAG, "could not reconnect the notification listener", it) }
            }, 3_000)
        }
    }

    private val watchedApps = setOf(
        "com.facebook.orca",            // Messenger
        "com.facebook.mlite",           // Messenger Lite
        "com.viber.voip",               // Viber
        "org.telegram.messenger",       // Telegram
        "com.whatsapp", "com.whatsapp.w4b",
        "com.globe.gcash.android",      // GCash
        "com.paymaya",                  // Maya
    )

    /**
     * Placeholder text a chat app shows instead of a message that contains a link (the real text is hidden).
     * Whole-notification match only, so a real message that merely mentions a link is still screened.
     */
    private val hiddenLinkText = Regex(
        // Leading symbols are skipped: Messenger writes "🔗 A link was sent to you"
        """^[^\p{L}\p{N}]*(a link was sent to you|[^:\n]{1,40} (sent|shared) a link|nagpadala ng link|may ipinadalang link)\.?\s*$""",
        RegexOption.IGNORE_CASE)

    /** Xiaomi's SMS app puts "4 messages | " in front of the newest message; it is not part of the message. */
    private val messageCountPrefix = Regex("""^\s*\d+\s+(new\s+)?(messages?|mensahe)\s*\|\s*""", RegexOption.IGNORE_CASE)

    private val appNames = mapOf(
        "com.facebook.orca" to "Messenger", "com.facebook.mlite" to "Messenger Lite", "com.viber.voip" to "Viber",
        "org.telegram.messenger" to "Telegram", "com.whatsapp" to "WhatsApp", "com.whatsapp.w4b" to "WhatsApp",
    )

    // Notification key + text already screened, so a chat app re-posting the same notification is ignored
    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>) = size > 300
    }

    override fun onListenerConnected() {
        connected = true
        Log.d(TAG, "notification listener connected")
    }

    override fun onListenerDisconnected() {
        connected = false
        Log.d(TAG, "notification listener DISCONNECTED")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return                                      // skip our own alerts
        val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
        if (pkg !in watchedApps && pkg != defaultSms) return
        val n = sbn.notification
        // Diagnostics: app name + decision only, never the message text
        fun skip(reason: String) { Log.d(TAG, "notification from $pkg: skipped ($reason)") }
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return skip("group summary")
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return skip("ongoing")
        // Status notices such as Messenger's "Chat heads active" come from a background service, not a message
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0 || n.category == Notification.CATEGORY_SERVICE)
            return skip("service notice")
        // Old messages re-posted by the app (e.g. after a reboot) are not new: they were screened already
        val ageMs = System.currentTimeMillis() - n.`when`
        if (n.`when` > 0 && ageMs > MAX_AGE_MS) return skip("old message, ${ageMs / 1000} s")

        val (sender, text) = extract(n) ?: return skip("no message text")
        synchronized(seen) {
            val key = "${sbn.key}|${text.hashCode()}"
            if (seen.containsKey(key)) return skip("already screened")
            seen[key] = true
        }
        if (hiddenLinkText.matches(text)) {
            Log.d(TAG, "notification from $pkg: link hidden by the app, asking the user to check it")
            applicationContext.graph.alerts.showHiddenLink(appNames[pkg] ?: "this app", sbn.key)
            return
        }
        Log.d(TAG, "notification from $pkg: screening")
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
            ?.toString()?.replace(messageCountPrefix, "")?.takeIf { it.isNotBlank() } ?: return null
        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() to text
    }
}
