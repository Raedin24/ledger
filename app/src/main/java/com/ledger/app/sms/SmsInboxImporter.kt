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
 * Imports past transactions from the SMS inbox.
 *
 * It reads `content://sms/inbox` and keeps only messages from a supported
 * provider ([Institution]); everything else is skipped before its text is
 * read. Each message goes through [LedgerRepository.ingest], the same path as
 * live capture. Saving is dedup-keyed, so the import is safe to run more than
 * once and alongside live capture. Message text is never stored, and the
 * inbox's timestamp becomes the transaction date.
 *
 * Needs READ_SMS (see [com.ledger.app.security.CapturePermission]).
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

            val batch = ArrayList<IncomingSms>(BATCH)
            suspend fun flush() {
                if (batch.isEmpty()) return
                repository.ingestBatch(batch).forEach { outcome ->
                    when (outcome) {
                        is LedgerRepository.Ingested.Saved, is LedgerRepository.Ingested.Review -> imported++
                        is LedgerRepository.Ingested.Duplicate -> duplicates++
                        is LedgerRepository.Ingested.Ignored -> ignored++
                    }
                }
                batch.clear()
            }
            while (cursor.moveToNext()) {
                // Lets the user cancel a long import.
                coroutineContext.ensureActive()
                val address = cursor.getString(addressIdx) ?: continue
                // Skip unsupported senders before reading their text.
                if (Institution.fromSenderId(address) == null) continue
                val body = cursor.getString(bodyIdx) ?: continue
                val date = cursor.getLong(dateIdx)
                scanned++

                batch += IncomingSms(senderId = address, body = body, receivedAt = Instant.ofEpochMilli(date))
                if (batch.size == BATCH) flush()
            }
            flush()
        }
        Report(scanned = scanned, imported = imported, duplicates = duplicates, ignored = ignored)
    }

    private companion object {
        /**
         * Messages per transaction (see [LedgerRepository.ingestBatch]). Small enough
         * that cancelling loses little, large enough to keep commits cheap.
         */
        const val BATCH = 200
    }
}
