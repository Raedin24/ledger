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
 * Posts a lightweight notification when live capture records or flags a
 * transaction. Best-effort and entirely local: it silently no-ops when
 * POST_NOTIFICATIONS hasn't been granted (Android 13+), and never posts for
 * duplicates or ignored (OTP/marketing) messages.
 */
object CaptureNotifier {

    private const val CHANNEL_ID = "capture"
    private val nextId = AtomicInteger(1000)

    /** Which tab the tap should land on. Read by `MainActivity` and matched
     *  against the bottom-tab routes in `LedgerApp`; an unrecognised value is
     *  ignored there rather than throwing. */
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
            // "Open Ledger to categorise it" is an instruction the tap should
            // carry out, so this one lands on the queue rather than the dashboard.
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
            // A silhouette, not the launcher icon: the system treats this as an
            // alpha mask, so a full-bleed adaptive icon flattens to a blob.
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context, id, dest))
            // Only does anything once there is a content intent to tap — before
            // one existed this line was inert, along with the rest of the tap.
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /**
     * The tap target.
     *
     * `CLEAR_TOP or SINGLE_TOP` against a `singleTop` activity means a tap
     * resumes the task that is already there and delivers the new destination
     * through `onNewIntent`, rather than stacking a second copy of the app over
     * the first — which, with the lock screen in play, would have meant entering
     * the PIN again to reach a screen already open underneath.
     *
     * [id] doubles as the request code so that two notifications waiting in the
     * shade keep their own destinations; sharing a request code would have the
     * second overwrite the first's extras.
     */
    private fun openApp(context: Context, id: Int, dest: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_DEST, dest)
        return PendingIntent.getActivity(
            context,
            id,
            intent,
            // IMMUTABLE is mandatory from Android 12; the extras are ours and
            // nothing outside needs to fill anything in.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
