package com.ledger.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ledger.app.MainActivity
import com.ledger.app.R
import com.ledger.app.data.repository.LedgerRepository
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts a small notification when live capture saves or flags a transaction.
 * Local only. It does nothing without the POST_NOTIFICATIONS permission
 * (Android 13+), and never posts for duplicates or ignored messages (OTPs,
 * marketing).
 */
object CaptureNotifier {

    private const val CHANNEL_ID = "capture"
    private val nextId = AtomicInteger(1000)

    /**
     * Which tab the tap should open. Read by `MainActivity` and checked against
     * the bottom-tab routes in `LedgerApp`; an unknown value is ignored.
     */
    const val EXTRA_DEST = "com.ledger.app.extra.DEST"
    const val DEST_OVERVIEW = "overview"
    const val DEST_REVIEW = "review"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Transaction capture",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Alerts when a transaction is captured or needs review." }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun notify(context: Context, outcome: LedgerRepository.Ingested) {
        val (title, text, dest) = when (outcome) {
            // The notification says to categorise it, so the tap opens the review
            // queue rather than the dashboard.
            is LedgerRepository.Ingested.Review ->
                Triple("New transaction to review", "Open Ledger to categorise it.", DEST_REVIEW)
            is LedgerRepository.Ingested.Saved ->
                Triple("Transaction recorded", "Captured and sorted automatically.", DEST_OVERVIEW)
            else -> return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val id = nextId.getAndIncrement()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            // A single-colour silhouette, not the launcher icon. Android uses it as a
            // mask, so a full-colour icon would show as a solid blob.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context, id, dest))
            // Dismiss the notification once it is tapped.
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /**
     * What happens when the notification is tapped.
     *
     * `CLEAR_TOP or SINGLE_TOP` on a `singleTop` activity resumes the app that is
     * already open and delivers the new destination through `onNewIntent`. That
     * avoids opening a second copy of the app, which would mean entering the PIN
     * again.
     *
     * [id] is also the request code, so two notifications keep their own
     * destinations instead of one overwriting the other's extras.
     */
    private fun openApp(context: Context, id: Int, dest: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_DEST, dest)
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            // IMMUTABLE is required from Android 12. Nothing outside the app needs to
            // fill in the extras.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
