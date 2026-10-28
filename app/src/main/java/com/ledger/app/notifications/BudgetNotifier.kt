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
import com.ledger.app.data.budget.BudgetLevel
import com.ledger.app.data.budget.BudgetStatus
import kotlin.math.roundToInt

/**
 * Posts the budget alerts.
 *
 * Budget alerts have their own notification channel, separate from capture,
 * so the user can turn either off without the other.
 *
 * Each category posts under its own notification tag. A second alert for the
 * same category replaces the first instead of stacking, and two categories
 * can never clash. Like [CaptureNotifier], it does nothing without the
 * POST_NOTIFICATIONS permission.
 */
object BudgetNotifier {

    private const val CHANNEL_ID = "budgets"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Budget alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Tells you when a category is close to, or past, its monthly limit." }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun notify(context: Context, status: BudgetStatus, level: BudgetLevel) {
        if (level == BudgetLevel.OK) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val percent = (status.fraction * 100).roundToInt()
        val title = when (level) {
            BudgetLevel.OVER -> "${status.category} is over budget"
            else -> "${status.category} is close to its limit"
        }
        // Show the amount, not just the percentage: "GHS 40 left" is more useful
        // than "92%", and once over, the overspend is what the user wants to know.
        val text = when (level) {
            BudgetLevel.OVER ->
                "${money(status.spentMinor)} of ${money(status.limitMinor)} this month · " +
                    "${money(-status.remainingMinor)} over"
            else ->
                "${money(status.spentMinor)} of ${money(status.limitMinor)} this month ($percent%) · " +
                    "${money(status.remainingMinor)} left"
        }
        // What a locked phone shows instead: that there is an alert, but not the
        // category or amount. The app's PIN protects these figures inside the
        // app, so the lock screen mustn't show them either.
        val lockScreen = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Budget alert")
            .setContentText("Unlock to see details.")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp(context, status.category))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(tag(status.category), NOTIFICATION_ID, notification)
        }
    }

    /** One id for every budget alert; the tag tells them apart. */
    private const val NOTIFICATION_ID = 1

    private fun tag(category: String) = "budget:$category"

    /** The app only uses GHS. */
    private fun money(minor: Long): String = "GHS %,.2f".format(minor / 100.0)

    /**
     * @param category also used for the request code, so each category's tap
     *  keeps its own extras instead of overwriting another's.
     */
    private fun openApp(context: Context, category: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(CaptureNotifier.EXTRA_DEST, CaptureNotifier.DEST_OVERVIEW)
        return PendingIntent.getActivity(
            context,
            REQUEST_BASE + category.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Request codes start here so they never clash with [CaptureNotifier]'s,
     * which count up from 1000.
     */
    private const val REQUEST_BASE = 200_000
}
