package com.ledger.app.di

import android.content.Context
import com.ledger.app.data.crypto.DatabaseKeyProvider
import com.ledger.app.data.db.CategoryDao
import com.ledger.app.data.db.LedgerDatabase
import com.ledger.app.data.db.OwnAccountDao
import com.ledger.app.data.db.OwnAccountNameDao
import com.ledger.app.data.db.RuleDao
import com.ledger.app.data.db.SenderDao
import com.ledger.app.data.db.TransactionDao
import androidx.room.Room
import com.ledger.domain.categorization.CategorizationEngine
import com.ledger.domain.dedup.DuplicateDetector
import com.ledger.domain.parser.SmsParser
import com.ledger.domain.validation.TransactionValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

/**
 * Wires the framework-free domain services and the encrypted Room database.
 * All singletons; the domain services are pure and stateless.
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

    // ---- Encrypted persistence ----

    @Provides @Singleton
    fun keyProvider(@ApplicationContext ctx: Context): DatabaseKeyProvider = DatabaseKeyProvider(ctx)

    @Provides @Singleton
    fun database(
        @ApplicationContext ctx: Context,
        keyProvider: DatabaseKeyProvider,
    ): LedgerDatabase {
        // SupportOpenHelperFactory takes the raw passphrase bytes directly (the 32
        // random bytes unwrapped from the Keystore). No plaintext passphrase file.
        val factory = SupportOpenHelperFactory(keyProvider.passphrase())
        return Room.databaseBuilder(ctx, LedgerDatabase::class.java, LedgerDatabase.NAME)
            .openHelperFactory(factory)   // <-- SQLCipher-backed, Keystore-wrapped key
            // Both of these move data as well as changing shape, so they can't be
            // generated; everything else still is.
            .addMigrations(LedgerDatabase.MIGRATION_4_5, LedgerDatabase.MIGRATION_5_6)
            .build()
    }

    @Provides fun transactionDao(db: LedgerDatabase): TransactionDao = db.transactionDao()
    @Provides fun ruleDao(db: LedgerDatabase): RuleDao = db.ruleDao()
    @Provides fun senderDao(db: LedgerDatabase): SenderDao = db.senderDao()
    @Provides fun categoryDao(db: LedgerDatabase): CategoryDao = db.categoryDao()
    @Provides fun ownAccountDao(db: LedgerDatabase): OwnAccountDao = db.ownAccountDao()
    @Provides fun ownAccountNameDao(db: LedgerDatabase): OwnAccountNameDao = db.ownAccountNameDao()
}
