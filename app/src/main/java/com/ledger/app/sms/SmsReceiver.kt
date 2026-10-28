package com.ledger.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ledger.app.data.budget.BudgetAlerter
import com.ledger.app.data.crypto.DatabaseKeyProvider
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
 * Receives SMS_RECEIVED (a broadcast only Android can send) and passes each
 * message to the repository to be captured.
 *
 * Privacy rules in this class:
 *  - The message text is read into a local String, passed straight to the
 *    repository, and never logged, cached or stored.
 *  - Messages that arrive in several parts are joined in memory only.
 */
@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: LedgerRepository

    @Inject lateinit var budgetAlerter: BudgetAlerter

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Join multi-part messages per sender, in memory only.
        val sender = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString(separator = "") { it.displayMessageBody ?: "" }
        if (body.isBlank()) return

        val sms = IncomingSms(senderId = sender, body = body, receivedAt = Instant.now())

        // goAsync keeps the receiver alive for the short database write, which
        // finishes well within Android's time limit.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val outcome = repository.ingest(sms)
                CaptureNotifier.notify(appContext, outcome)
                // Only check budgets when something was saved. A duplicate or an OTP
                // changes nothing.
                if (outcome is LedgerRepository.Ingested.Saved ||
                    outcome is LedgerRepository.Ingested.Review
                ) {
                    budgetAlerter.checkAndNotify()
                }
            } catch (_: DatabaseKeyProvider.KeyUnavailableException) {
                // The ledger can't be opened on this device (MainActivity shows a
                // recovery screen). Crashing here would repeat on every SMS.
            } finally {
                pending.finish()
            }
        }
    }
}
