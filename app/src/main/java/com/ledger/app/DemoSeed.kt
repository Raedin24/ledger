package com.ledger.app

import com.ledger.app.data.backup.BackupFile
import com.ledger.app.data.backup.RuleDto
import com.ledger.app.data.backup.TxnDto
import com.ledger.app.data.repository.LedgerRepository
import java.util.concurrent.TimeUnit

/**
 * Invented transactions for store/README screenshots.
 *
 * Screenshots must never be of the real ledger — publishing them would publish
 * the user's counterparties, phone numbers and balances — so the `screenshot`
 * build type installs under its own applicationId with its own database, and
 * this fills it with people who do not exist.
 *
 * Every caller is behind `BuildConfig.ALLOW_SCREENSHOTS`, which is a compile-time
 * `false` outside that build type, so R8 drops this class from release entirely.
 *
 * Seeding goes through [LedgerRepository.importBackup] rather than writing rows
 * directly: it is the same path a real restore takes, so the demo data is
 * subject to the same dedup and category resolution as anything else, and this
 * file cannot drift into constructing states the app can't actually reach.
 *
 * Triggered by an intent extra rather than on first launch, so the empty states
 * can be photographed first:
 *
 *     adb shell am start -n com.ledger.app.screenshot/com.ledger.app.MainActivity \
 *         --ez seed_demo true
 */
object DemoSeed {

    const val EXTRA = "seed_demo"

    private val now = System.currentTimeMillis()

    private fun at(daysAgo: Long, hour: Int, minute: Int = 0): Long =
        now - TimeUnit.DAYS.toMillis(daysAgo) -
            TimeUnit.HOURS.toMillis(hour.toLong()) -
            TimeUnit.MINUTES.toMillis(minute.toLong())

    private var seq = 0

    /**
     * @param balance the wallet balance *after* this transaction — the field the
     *   parser treats as proof a message is a real movement, so leaving it null
     *   would render rows that live capture could never produce.
     */
    private fun txn(
        daysAgo: Long,
        hour: Int,
        direction: String,
        amount: Long,
        counterparty: String,
        category: String?,
        balance: Long,
        institution: String = "MTN_MOMO",
        fee: Long? = null,
        hint: String? = null,
        review: Boolean = false,
    ) = TxnDto(
        dedupKey = "demo-${seq++}",
        reference = "%010d".format(1_700_000_000L + seq * 7919L),
        institution = institution,
        direction = direction,
        amountMinor = amount,
        balanceMinor = balance,
        feeMinor = fee,
        currency = "GHS",
        counterparty = counterparty,
        referenceHint = hint,
        occurredAt = at(daysAgo, hour),
        capturedAt = at(daysAgo, hour),
        category = category,
        person = null,
        notes = null,
        needsReview = review,
    )

    private fun transactions(): List<TxnDto> = listOf(
        // — this week —
        txn(0, 3, "DEBIT", 4_500, "GLOVO GHANA", "Food & Drink", 128_640),
        txn(0, 9, "DEBIT", 2_000, "TROTRO — MADINA", "Transport", 133_140),
        txn(1, 5, "DEBIT", 12_000, "MELCOM PLUS", "Shopping", 135_140),
        // Credits sit in the last ~24h on purpose: Overview totals "this month",
        // and on the 1st or 2nd of a month anything dated in days lands in the
        // previous one, leaving MONEY IN reading ₵0.00 in every screenshot.
        txn(0, 15, "CREDIT", 50_000, "AKOSUA BOATENG", "Family Support", 147_140),
        txn(0, 22, "CREDIT", 320_000, "ACME LTD PAYROLL", "Salary", 229_290, hint = "August salary"),
        txn(2, 7, "DEBIT", 3_500, "MTN DATA BUNDLE", "Data & Airtime", 97_140),
        txn(2, 19, "DEBIT", 8_900, "SHELL — OSU", "Fuel", 100_640, fee = 100),
        txn(3, 6, "DEBIT", 25_000, "ECG PREPAID", "Bills & Utilities", 109_640),
        txn(3, 13, "DEBIT", 6_400, "KFC — ACCRA MALL", "Food & Drink", 134_640),
        txn(4, 8, "DEBIT", 15_000, "KWAME MENSAH", "Transfers", 141_040, fee = 150, hint = "rent share"),
        txn(5, 10, "DEBIT", 4_200, "MAXMART", "Groceries", 156_190),
        txn(6, 9, "CREDIT", 30_000, "YAA ASANTEWAA", "Gift Received", 160_390, institution = "GHANAPAY"),

        // — last week —
        txn(8, 12, "DEBIT", 9_800, "UBER TRIP", "Transport", 130_390),
        txn(9, 7, "DEBIT", 55_000, "DR. OWUSU CLINIC", "Health", 140_190),
        txn(10, 15, "DEBIT", 3_000, "TELECEL AIRTIME", "Data & Airtime", 195_190, institution = "TELECEL_CASH"),
        txn(11, 9, "DEBIT", 7_500, "PALACE MALL", "Groceries", 198_190),
        txn(12, 18, "DEBIT", 18_000, "DSTV SUBSCRIPTION", "Entertainment", 205_690, hint = "monthly"),
        txn(13, 8, "DEBIT", 5_600, "PAPAYE — OSU", "Food & Drink", 223_690),
        txn(32, 6, "CREDIT", 320_000, "ACME LTD PAYROLL", "Salary", 229_290, hint = "July salary"),

        // — earlier —
        txn(17, 10, "DEBIT", 120_000, "MR ADJEI — LANDLORD", "Rent", 91_290, fee = 200),
        txn(18, 14, "DEBIT", 4_800, "SHOPRITE", "Groceries", 211_490),
        txn(20, 11, "DEBIT", 2_500, "TROTRO — CIRCLE", "Transport", 216_290),
        txn(21, 16, "DEBIT", 35_000, "KOFI GARAGE", "Car", 218_790, hint = "brake pads"),
        txn(23, 9, "DEBIT", 6_000, "MTN DATA BUNDLE", "Data & Airtime", 253_790),
        txn(25, 13, "CREDIT", 45_000, "FREELANCE — DESIGN", "Freelance", 259_790, institution = "GHANAPAY"),
        txn(27, 8, "DEBIT", 11_500, "GOIL — SPINTEX", "Fuel", 214_790, fee = 100),
        txn(30, 12, "DEBIT", 20_000, "AMA SERWAA", "Family", 226_390, hint = "school fees"),

        // — waiting in the review queue, so that screen has something to show.
        //   Category deliberately null: an unreviewed row has not been sorted yet,
        //   and giving it a catch-all would be a state review never produces.
        txn(0, 1, "DEBIT", 7_300, "QUICKMART EXPRESS", null, 121_340, review = true),
        txn(1, 20, "CREDIT", 18_000, "0244 *** 8821", null, 124_640, review = true),
        txn(4, 17, "DEBIT", 2_800, "UNKNOWN MERCHANT", null, 138_240, review = true, institution = "TELECEL_CASH"),
    )

    /** A couple of learned rules, so the Rules screen isn't blank either. */
    private fun rules(): List<RuleDto> = listOf(
        RuleDto("MTN DATA BUNDLE", "DEBIT", "Data & Airtime", null, "EXACT", 0, true),
        RuleDto("ECG PREPAID", "DEBIT", "Bills & Utilities", null, "EXACT", 0, true),
        RuleDto("ACME LTD PAYROLL", "CREDIT", "Salary", null, "EXACT", 0, true),
        RuleDto("GLOVO GHANA", "DEBIT", "Food & Drink", null, "EXACT", 0, true),
    )

    /** No-op if the database already holds anything, so it cannot double-seed. */
    suspend fun seed(repo: LedgerRepository) {
        if (repo.transactionCount() > 0) return
        repo.importBackup(
            BackupFile(
                exportedAt = now,
                transactions = transactions(),
                rules = rules(),
            ),
            replace = false,
        )
    }
}
