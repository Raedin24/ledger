package com.ledger.app.sms

import android.content.Context
import android.provider.Telephony
import com.ledger.app.data.repository.LedgerRepository
import com.ledger.domain.model.Institution
import com.ledger.domain.parser.IncomingSms
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.coroutines.coroutineContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time backfill of past transactions from the device SMS inbox.
 *
 * Reads `content://sms/inbox`, keeps ONLY messages whose sender maps to a known
 * [Institution] (everything else is skipped before its body is ever touched by
 * the pipeline), and runs each through the same [LedgerRepository.ingest] path
 * as live capture. Because ingest is dedup-keyed, this is idempotent — safe to
 * run more than once, and safe to run alongside live capture. Raw bodies are
 * never stored; the inbox's original timestamp becomes the transaction date.
 *
 * Requires READ_SMS (see [com.ledger.app.security.CapturePermission]).
 */
@Singleton
class SmsInboxImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LedgerRepository,
) {
    data class Report(val scanned: Int, val imported: Int, val duplicates: Int, val ignored: Int)

    suspend fun importAll(): Report = withContext(Dispatchers.IO) {
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        var scanned = 0
        var imported = 0
        var duplicates = 0
        var ignored = 0

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                // Cooperative cancellation — lets the user Quit a long backfill.
                coroutineContext.ensureActive()
                val address = cursor.getString(addressIdx) ?: continue
                // Skip non-trusted senders before their body is read into the pipeline.
                if (Institution.fromSenderId(address) == null) continue
                val body = cursor.getString(bodyIdx) ?: continue
                val date = cursor.getLong(dateIdx)
                scanned++

                val sms = IncomingSms(senderId = address, body = body, receivedAt = Instant.ofEpochMilli(date))
                when (repository.ingest(sms)) {
                    is LedgerRepository.Ingested.Saved, is LedgerRepository.Ingested.Review -> imported++
                    is LedgerRepository.Ingested.Duplicate -> duplicates++
                    is LedgerRepository.Ingested.Ignored -> ignored++
                }
            }
        }
        Report(scanned = scanned, imported = imported, duplicates = duplicates, ignored = ignored)
    }
}
