package com.ledger.app.di

import android.content.Context
import com.ledger.app.data.crypto.DatabaseKeyProvider
import com.ledger.app.data.db.BudgetDao
import com.ledger.app.data.db.CategoryDao
import com.ledger.app.data.db.LedgerDatabase
import com.ledger.app.data.db.OwnAccountDao
import com.ledger.app.data.db.OwnAccountNameDao
import com.ledger.app.data.db.RuleDao
import com.ledger.app.data.db.TransactionDao
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ledger.domain.categorization.CategorizationEngine
import com.ledger.domain.dedup.DuplicateDetector
import com.ledger.domain.parser.SmsParser
import com.ledger.domain.validation.TransactionValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.ledger.app.data.crypto.DeferredKeyOpenHelperFactory
import javax.inject.Singleton

/**
 * Provides the domain services and the encrypted Room database. Everything is
 * a singleton; the domain services hold no state.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ---- Domain services (from :core-domain) ----

    @Provides @Singleton
    fun validator(): TransactionValidator = TransactionValidator()

    @Provides @Singleton
    fun parser(validator: TransactionValidator): SmsParser = SmsParser(validator = validator)

    @Provides @Singleton
    fun duplicateDetector(): DuplicateDetector = DuplicateDetector()

    @Provides @Singleton
    fun categorizer(): CategorizationEngine = CategorizationEngine()

    // ---- Encrypted database ----

    @Provides @Singleton
    fun keyProvider(@ApplicationContext ctx: Context): DatabaseKeyProvider = DatabaseKeyProvider(ctx)

    @Provides @Singleton
    fun database(
        @ApplicationContext ctx: Context,
        keyProvider: DatabaseKeyProvider,
    ): LedgerDatabase {
        // The factory passes the raw 32-byte passphrase from the Keystore to
        // SQLCipher. No passphrase is ever written to a file in plain text.
        //
        // The key is read at the first query, not when this is built (see
        // DeferredKeyOpenHelperFactory), so a lost key can't crash the app before
        // it can explain what happened.
        val factory = DeferredKeyOpenHelperFactory(keyProvider)
        return Room.databaseBuilder(ctx, LedgerDatabase::class.java, LedgerDatabase.NAME)
            .openHelperFactory(factory)  // SQLCipher, with the Keystore-protected key
            // One connection instead of a pool. SQLCipher runs its slow key
            // derivation for every connection it opens, so extra connections would
            // each add a delay of about a second. The app's queries are quick, so
            // sharing one connection costs nothing noticeable.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            // These move data, so they are written by hand. The rest are generated.
            .addMigrations(
                LedgerDatabase.MIGRATION_4_5,
                LedgerDatabase.MIGRATION_5_6,
                LedgerDatabase.MIGRATION_6_7,
                LedgerDatabase.MIGRATION_7_8,
                LedgerDatabase.MIGRATION_8_9,
            )
            .build()
    }

    @Provides fun transactionDao(db: LedgerDatabase): TransactionDao = db.transactionDao()
    @Provides fun ruleDao(db: LedgerDatabase): RuleDao = db.ruleDao()
    @Provides fun categoryDao(db: LedgerDatabase): CategoryDao = db.categoryDao()
    @Provides fun ownAccountDao(db: LedgerDatabase): OwnAccountDao = db.ownAccountDao()
    @Provides fun ownAccountNameDao(db: LedgerDatabase): OwnAccountNameDao = db.ownAccountNameDao()
    @Provides fun budgetDao(db: LedgerDatabase): BudgetDao = db.budgetDao()
}
