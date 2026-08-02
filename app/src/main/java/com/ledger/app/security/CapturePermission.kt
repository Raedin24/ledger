package com.ledger.app.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Runtime permission gate for automatic SMS capture.
 *
 * RECEIVE_SMS is a *dangerous* permission, so declaring it in the manifest is
 * not enough — [com.ledger.app.sms.SmsReceiver] stays dormant until the user
 * grants it at runtime. Everything downstream (parse/validate/dedup/persist) is
 * already wired; this is the switch that lets messages actually reach it.
 */
object CapturePermission {
    const val RECEIVE_SMS: String = Manifest.permission.RECEIVE_SMS

    /** READ_SMS backs the one-time historical-import of the existing inbox. */
    const val READ_SMS: String = Manifest.permission.READ_SMS

    /** True once the user has granted RECEIVE_SMS and live capture can run. */
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** True once READ_SMS is granted and past messages can be imported. */
    fun canReadInbox(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, READ_SMS) == PackageManager.PERMISSION_GRANTED
}
