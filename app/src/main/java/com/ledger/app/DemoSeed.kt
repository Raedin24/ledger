package com.ledger.app

import com.ledger.app.data.backup.BackupFile
import com.ledger.app.data.backup.RuleDto
import com.ledger.app.data.backup.TxnDto
import com.ledger.app.data.db.CashMove
import android.os.SystemClock
import android.util.Log
import com.ledger.app.data.repository.LedgerRepository
import com.ledger.domain.parser.IncomingSms
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Made-up transactions for README screenshots.
 *
 * Screenshots must never show the real ledger. The `screenshot` build type
 * installs as a separate package with its own database, and this fills it
 * with people who don't exist.
 *
 * Every caller checks `BuildConfig.ALLOW_SCREENSHOTS`, which is false outside
 * that build type, so this class is removed from release builds.
 *
 * The data goes in through [LedgerRepository.importBackup], the same path a
 * real restore uses, so it can only create states the app can really reach.
 *
 * It runs when asked for with an intent extra, not on first launch, so the
 * empty states can be photographed first:
 *
 *     adb shell am start -n io.github.raedin24.ledger.screenshot/com.ledger.app.MainActivity \
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
     * @param balance the wallet balance after this transaction. Real alerts always
     *   carry one, so leaving it null would create rows live capture never makes.
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
        cashMove: String? = null,
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
        cashMove = cashMove,
    )

    /**
     * A cash ledger row, entered by hand. [opening] marks the cash the user
     * already had, which isn't income (see [CashMove.OPENING]).
     */
    private fun cash(
        daysAgo: Long,
        hour: Int,
        direction: String,
        amount: Long,
        counterparty: String,
        category: String?,
        opening: Boolean = false,
    ) = TxnDto(
        dedupKey = "demo-cash-${seq++}",
        reference = null,
        institution = "CASH",
        direction = direction,
        amountMinor = amount,
        balanceMinor = null,
        feeMinor = null,
        currency = "GHS",
        counterparty = counterparty,
        referenceHint = null,
        occurredAt = at(daysAgo, hour),
        capturedAt = at(daysAgo, hour),
        category = category,
        person = null,
        notes = null,
        needsReview = false,
        cashMove = if (opening) CashMove.OPENING else null,
    )

    private fun transactions(): List<TxnDto> = listOf(
        // This week
        txn(0, 3, "DEBIT", 4_500, "GLOVO GHANA", "Food & Drink", 128_640),
        txn(0, 9, "DEBIT", 2_000, "TROTRO · MADINA", "Transport", 133_140),
        txn(1, 5, "DEBIT", 8_000, "MELCOM PLUS", "Shopping", 135_140),
        // Credits are within the last day, so Money In isn't zero in screenshots
        // taken on the 1st or 2nd of a month.
        txn(0, 15, "CREDIT", 30_000, "AKOSUA BOATENG", "Family Support", 147_140),
        txn(0, 22, "CREDIT", 220_000, "ACME LTD PAYROLL", "Salary", 229_290, hint = "October salary"),
        txn(2, 7, "DEBIT", 3_500, "MTN DATA BUNDLE", "Data & Airtime", 97_140),
        txn(2, 19, "DEBIT", 8_900, "SHELL · OSU", "Fuel", 100_640, fee = 100),
        txn(3, 6, "DEBIT", 18_000, "ECG PREPAID", "Bills & Utilities", 109_640),
        txn(3, 13, "DEBIT", 6_400, "KFC · ACCRA MALL", "Food & Drink", 134_640),
        txn(4, 8, "DEBIT", 8_000, "KWAME MENSAH", "Transfers", 141_040, fee = 150, hint = "rent share"),
        txn(5, 10, "DEBIT", 4_200, "MAXMART", "Groceries", 156_190),
        txn(6, 9, "CREDIT", 15_000, "YAA ASANTEWAA", "Gift Received", 160_390, institution = "GHANAPAY"),

        // Last week
        txn(8, 12, "DEBIT", 9_800, "UBER TRIP", "Transport", 130_390),
        txn(9, 7, "DEBIT", 25_000, "DR. OWUSU CLINIC", "Health", 140_190),
        txn(10, 15, "DEBIT", 3_000, "TELECEL AIRTIME", "Data & Airtime", 195_190, institution = "TELECEL_CASH"),
        txn(11, 9, "DEBIT", 7_500, "PALACE MALL", "Groceries", 198_190),
        txn(12, 18, "DEBIT", 12_000, "DSTV SUBSCRIPTION", "Entertainment", 205_690, hint = "monthly"),
        txn(13, 8, "DEBIT", 5_600, "PAPAYE · OSU", "Food & Drink", 223_690),
        txn(32, 6, "CREDIT", 220_000, "ACME LTD PAYROLL", "Salary", 229_290, hint = "September salary"),

        // Earlier
        txn(17, 10, "DEBIT", 80_000, "MR ADJEI · LANDLORD", "Rent", 91_290, fee = 200),
        txn(18, 14, "DEBIT", 4_800, "SHOPRITE", "Groceries", 211_490),
        txn(20, 11, "DEBIT", 2_500, "TROTRO · CIRCLE", "Transport", 216_290),
        txn(21, 16, "DEBIT", 20_000, "KOFI GARAGE", "Car", 218_790, hint = "brake pads"),
        txn(23, 9, "DEBIT", 6_000, "MTN DATA BUNDLE", "Data & Airtime", 253_790),
        txn(25, 13, "CREDIT", 25_000, "FREELANCE · DESIGN", "Freelance", 259_790, institution = "GHANAPAY"),
        txn(27, 8, "DEBIT", 11_500, "GOIL · SPINTEX", "Fuel", 214_790, fee = 100),
        txn(30, 12, "DEBIT", 15_000, "AMA SERWAA", "Family", 226_390, hint = "school fees"),

        // Last month, at a similar level, so the dashboard's "vs last month"
        // badge compares against a real month.
        txn(33, 19, "DEBIT", 12_000, "DSTV SUBSCRIPTION", "Entertainment", 246_390, hint = "monthly"),
        txn(34, 13, "DEBIT", 5_200, "PAPAYE · OSU", "Food & Drink", 264_390),
        txn(35, 8, "DEBIT", 8_400, "UBER TRIP", "Transport", 269_590),
        txn(36, 10, "DEBIT", 6_000, "MTN DATA BUNDLE", "Data & Airtime", 277_990),
        txn(37, 15, "DEBIT", 14_000, "MELCOM · HOME", "Household", 283_990),
        txn(38, 11, "DEBIT", 6_500, "ERNEST CHEMIST", "Health", 297_990),
        txn(40, 21, "DEBIT", 18_500, "SKYBAR · ACCRA", "Dining & Outings", 304_490),
        txn(42, 9, "DEBIT", 12_000, "SHELL · OSU", "Fuel", 322_990, fee = 100),
        txn(44, 7, "DEBIT", 18_000, "ECG PREPAID", "Bills & Utilities", 335_090),
        txn(45, 17, "DEBIT", 9_600, "SHOPRITE", "Groceries", 360_090),
        txn(48, 10, "DEBIT", 80_000, "MR ADJEI · LANDLORD", "Rent", 369_690, fee = 200),
        txn(50, 12, "DEBIT", 20_000, "AMA SERWAA", "Family", 489_890, hint = "school fees"),
        txn(52, 16, "DEBIT", 15_000, "KOFI GARAGE", "Car", 519_890, hint = "service"),
        txn(54, 9, "DEBIT", 11_000, "MAXMART", "Groceries", 541_890),

        // Cash: what was already on hand, then a few small cash spends.
        cash(29, 8, "CREDIT", 25_000, "Cash on hand", null, opening = true),
        cash(6, 13, "DEBIT", 3_500, "KENKEY · MADINA", "Food & Drink"),
        cash(3, 7, "DEBIT", 1_500, "TROTRO · LAPAZ", "Transport"),
        cash(1, 18, "DEBIT", 6_000, "MAKOLA MARKET", "Groceries"),

        // Waiting in review, so that screen has something to show. No category,
        // because unreviewed rows haven't been sorted yet.
        txn(0, 1, "DEBIT", 7_300, "QUICKMART EXPRESS", null, 121_340, review = true),
        txn(1, 20, "CREDIT", 8_000, "0244 *** 8821", null, 124_640, review = true),
        txn(4, 17, "DEBIT", 2_800, "UNKNOWN MERCHANT", null, 138_240, review = true, institution = "TELECEL_CASH"),
        // An agent withdrawal, so the cash-ledger question has something to ask.
        txn(1, 2, "DEBIT", 20_000, "ADOM VENTURES", null, 115_340, fee = 50, review = true, cashMove = CashMove.OFFER),
    )

    /** A couple of learned rules, so the Rules screen isn't blank either. */
    private fun rules(): List<RuleDto> = listOf(
        RuleDto("MTN DATA BUNDLE", "DEBIT", "Data & Airtime", null, "EXACT", 0, true),
        RuleDto("ECG PREPAID", "DEBIT", "Bills & Utilities", null, "EXACT", 0, true),
        RuleDto("ACME LTD PAYROLL", "CREDIT", "Salary", null, "EXACT", 0, true),
        RuleDto("GLOVO GHANA", "DEBIT", "Food & Drink", null, "EXACT", 0, true),
    )

    /**
     * Screenshot builds only: `--ei bench_ingest N` runs N made-up alerts through
     * the capture path and logs timings under the "LedgerBench" tag.
     */
    const val EXTRA_BENCH = "bench_ingest"

    /**
     * Made-up MoMo alerts in the real formats (debits, credits, the odd cash-out),
     * spread over two years. Every value is invented.
     */
    private fun syntheticSms(n: Int): List<IncomingSms> {
        val start = now - TimeUnit.DAYS.toMillis(730)
        val step = TimeUnit.DAYS.toMillis(730) / n.coerceAtLeast(1)
        return (0 until n).map { i ->
            val id = 90_000_000_000L + i
            val amount = 5 + (i * 37) % 400
            val balance = 1_000 + (i * 53) % 9_000
            val merchant = "MERCHANT ${i % 60}"
            val body = when {
                i % 20 == 0 ->
                    "Cash Out made for GHS$amount.00 to AGENT ${i % 7}. Current Balance: GHS$balance.00 " +
                        "Financial Transaction Id: $id. Fee charged: GHS0.50."
                i % 4 == 0 ->
                    "Payment received for GHS $amount.00 from PERSON ${i % 25}  Current Balance: GHS $balance.00 . " +
                        "Available Balance: GHS $balance.00. Reference: bench. Transaction ID: $id. TRANSACTION FEE: 0.00"
                else ->
                    "Payment for GHS$amount.00 to $merchant .Current Balance: GHS $balance.00. " +
                        "Transaction Id: $id. Fee charged: GHS0.00,Tax Charged 0."
            }
            IncomingSms("MobileMoney", body, Instant.ofEpochMilli(start + i * step))
        }
    }

    suspend fun bench(repo: LedgerRepository, n: Int) {
        val messages = syntheticSms(n)
        val parser = com.ledger.domain.parser.SmsParser()
        val p0 = SystemClock.elapsedRealtime()
        repeat(3) { messages.forEach { parser.parse(it) } }
        Log.i(BENCH_TAG, "parse-only n=$n per=${"%.3f".format((SystemClock.elapsedRealtime() - p0).toDouble() / (3 * n))}")
        val t0 = SystemClock.elapsedRealtime()
        messages.chunked(200).forEach { repo.ingestBatch(it) }
        val t1 = SystemClock.elapsedRealtime()
        Log.i(BENCH_TAG, "ingest n=$n ms=${t1 - t0} per=${"%.2f".format((t1 - t0).toDouble() / n)}")
        suspend fun time(label: String, block: suspend () -> Unit) {
            val s = SystemClock.elapsedRealtime(); block()
            Log.i(BENCH_TAG, "$label ms=${SystemClock.elapsedRealtime() - s}")
        }
        repeat(2) { round ->
            time("r$round reviewQueue") { repo.observeReviewQueue().first() }
            time("r$round reviewCount") { repo.observeReviewCount().first() }
            time("r$round cashBalance") { repo.observeCashBalance().first() }
            time("r$round cashList") { repo.observeCash().first() }
            time("r$round savingsBefore") { repo.observeSavingsBefore(now).first() }
            time("r$round repairCategories") { repo.repairCategories() }
        }
        Log.i(BENCH_TAG, "done")
    }

    private const val BENCH_TAG = "LedgerBench"

    /** Does nothing if the database already has data, so it can't seed twice. */
    suspend fun seed(repo: LedgerRepository) {
        if (repo.transactionCount() > 0) return
        repo.importBackup(
            BackupFile(
                exportedAt = now,
                transactions = transactions(),
                rules = rules(),
            ),
        )
    }
}
