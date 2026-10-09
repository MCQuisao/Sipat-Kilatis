package com.example.sipatkilatis.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.sipatkilatis.graph
import com.example.sipatkilatis.model.MessageSource
import kotlinx.coroutines.launch

/**
 * Receives every incoming SMS (works in the background and after reboot: it is declared in the manifest).
 * Long SMS arrive in several parts; they are joined back into one message per sender.
 * goAsync() lets detection finish on a background thread without blocking the system.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Multipart SMS: same sender, several PDUs -> one message
        val messages = parts.filterNotNull()
            .groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "" }
            .map { (sender, pdus) -> sender to pdus.joinToString("") { it.displayMessageBody ?: "" } }

        val pending = goAsync()
        val graph = context.graph
        graph.appScope.launch {
            try {
                for ((sender, body) in messages) {
                    graph.screener.screen(sender.ifEmpty { null }, body, MessageSource.SMS)
                }
            } catch (e: Exception) {
                Log.e("SipatKilatis", "SMS screening failed", e)
            } finally {
                pending.finish()   // must be called within ~10 s; detection takes well under 1 s
            }
        }
    }
}
