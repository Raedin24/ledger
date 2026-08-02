package com.ledger.app

import android.app.Application
import com.ledger.app.data.repository.LedgerRepository
import com.ledger.app.notifications.CaptureNotifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LedgerApp : Application() {

    @Inject lateinit var repository: LedgerRepository

    override fun onCreate() {
        super.onCreate()
        // Load the SQLCipher native library once before the DB is opened.
        // The modern net.zetetic:sqlcipher-android AAR does not auto-load; the
        // caller loads libsqlcipher.so explicitly (replaces SQLiteDatabase.loadLibs).
        System.loadLibrary("sqlcipher")

        // Notification channel for capture alerts (safe to call repeatedly).
        CaptureNotifier.ensureChannel(this)

        // Seed default categories off the main thread. Idempotent — a no-op once
        // any category exists, so it covers both fresh installs and the migration.
        CoroutineScope(Dispatchers.IO).launch { repository.ensureCategoriesSeeded() }
    }
}
