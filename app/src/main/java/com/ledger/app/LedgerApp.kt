package com.ledger.app

import android.app.Application
import com.ledger.app.data.crypto.DatabaseKeyProvider
import com.ledger.app.data.db.DefaultCategories
import com.ledger.app.data.prefs.SetupPrefs
import com.ledger.app.data.repository.LedgerRepository
import com.ledger.app.notifications.BudgetNotifier
import com.ledger.app.notifications.CaptureNotifier
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LedgerApp : Application() {

    @Inject lateinit var repository: LedgerRepository

    @Inject lateinit var setupPrefs: SetupPrefs

    override fun onCreate() {
        super.onCreate()
        // Load the SQLCipher native library before the database is opened. The
        // net.zetetic:sqlcipher-android library doesn't load it automatically.
        System.loadLibrary("sqlcipher")

        // Notification channels (safe to create repeatedly). Capture and budget
        // alerts each get their own, so either can be turned off separately.
        CaptureNotifier.ensureChannel(this)
        BudgetNotifier.ensureChannel(this)

        // Off the main thread: seed the default categories on a first run, give
        // an existing ledger any defaults added since it was last updated, then
        // add back any category still in use that the list is missing.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.ensureCategoriesSeeded()
                if (setupPrefs.defaultCategoriesVersion < DefaultCategories.VERSION) {
                    repository.addNewDefaultCategories(since = setupPrefs.defaultCategoriesVersion)
                    setupPrefs.defaultCategoriesVersion = DefaultCategories.VERSION
                }
                repository.repairCategories()
            } catch (_: DatabaseKeyProvider.KeyUnavailableException) {
                // MainActivity shows the recovery screen; nothing to do here.
            }
        }
    }
}
