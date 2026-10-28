package com.ledger.app.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Runtime permission checks for automatic SMS capture.
 *
 * RECEIVE_SMS is a dangerous permission, so declaring it in the manifest isn't
 * enough. [com.ledger.app.sms.SmsReceiver] receives nothing until the user
 * grants it at runtime.
 */
object CapturePermission {
    const val RECEIVE_SMS: String = Manifest.permission.RECEIVE_SMS

    /** READ_SMS is needed for the one-time import of messages already in the inbox. */
    const val READ_SMS: String = Manifest.permission.READ_SMS

    /** True once the user has granted RECEIVE_SMS and live capture can run. */
    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** True once READ_SMS is granted and past messages can be imported. */
    fun canReadInbox(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, READ_SMS) == PackageManager.PERMISSION_GRANTED
}
