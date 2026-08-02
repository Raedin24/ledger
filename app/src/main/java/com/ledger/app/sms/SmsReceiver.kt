package com.ledger.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ledger.app.data.repository.LedgerRepository
import com.ledger.app.notifications.CaptureNotifier
import com.ledger.domain.parser.IncomingSms
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Receives SMS_RECEIVED (a protected, OS-only broadcast) and hands each message
 * to the repository for the full capture pipeline.
 *
 * Privacy discipline in this class:
 *  - The message body is read into a local String, passed straight to the
 *    repository, and never logged, cached, or stored anywhere.
 *  - Multi-part messages are reassembled in-memory only (single-message alerts
 *    are the norm for the in-scope providers; this just handles the exception).
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: LedgerRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Reassemble multi-part bodies per originating address, in memory only.
        val sender = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
        if (body.isBlank()) return

        val sms = IncomingSms(senderId = sender, body = body, receivedAt = Instant.now())

        // goAsync keeps the receiver alive for the short DB write. The domain
        // pipeline is fast (regex + one insert); well within the ~10s budget.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = repository.ingest(sms)
                CaptureNotifier.notify(appContext, outcome)
            } finally {
                pending.finish()
            }
        }
    }
}
