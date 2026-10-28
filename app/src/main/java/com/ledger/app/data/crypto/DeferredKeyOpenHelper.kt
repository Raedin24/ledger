package com.ledger.app.data.crypto

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * A SQLCipher open helper that reads the database key on the first query,
 * not when Room builds the database.
 *
 * Room builds its open helper as soon as anything holding the database is
 * injected, including the Application itself. Reading the key that early
 * would crash the app before any screen could explain a lost key. Here the
 * key is read at the first query instead, and [com.ledger.app.MainActivity]
 * checks [DatabaseKeyProvider.canUnlock] first and shows a recovery screen.
 */
class DeferredKeyOpenHelperFactory(private val keyProvider: DatabaseKeyProvider) : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        DeferredKeyOpenHelper(configuration, keyProvider)
}

private class DeferredKeyOpenHelper(
    private val configuration: SupportSQLiteOpenHelper.Configuration,
    private val keyProvider: DatabaseKeyProvider,
) : SupportSQLiteOpenHelper {

    /** Room sets this before the first query; it is passed on to the real helper once that exists. */
    @Volatile private var writeAheadLogging: Boolean? = null

    private val delegate: Lazy<SupportSQLiteOpenHelper> = lazy {
        SupportOpenHelperFactory(keyProvider.passphrase()).create(configuration).also { helper ->
            writeAheadLogging?.let { helper.setWriteAheadLoggingEnabled(it) }
        }
    }

    override val databaseName: String? get() = configuration.name

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        writeAheadLogging = enabled
        if (delegate.isInitialized()) delegate.value.setWriteAheadLoggingEnabled(enabled)
    }

    override val writableDatabase: SupportSQLiteDatabase get() = delegate.value.writableDatabase

    override val readableDatabase: SupportSQLiteDatabase get() = delegate.value.readableDatabase

    override fun close() {
        if (delegate.isInitialized()) delegate.value.close()
    }
}
