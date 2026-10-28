package com.ledger.app.data.backup

import com.ledger.app.data.db.BudgetEntity
import com.ledger.app.data.db.CategoryEntity
import com.ledger.app.data.db.OwnAccountEntity
import com.ledger.app.data.db.OwnAccountNameEntity
import com.ledger.app.data.db.RuleEntity
import com.ledger.app.data.db.TransactionEntity
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Converts the ledger to and from the export formats. It has no Android
 * dependencies.
 *  - JSON: the full backup, which can also be imported.
 *  - CSV: a flat list of transactions for spreadsheets (export only).
 */
object LedgerTransfer {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val csvStamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

    fun toJson(
        transactions: List<TransactionEntity>,
        rules: List<RuleEntity>,
        categories: List<CategoryEntity>,
        ownAccounts: List<OwnAccountEntity>,
        ownAccountNames: List<OwnAccountNameEntity>,
        budgets: List<BudgetEntity>,
    ): String {
        val namesByAccount = ownAccountNames.groupBy { it.accountId }
        return json.encodeToString(
            BackupFile.serializer(),
            BackupFile(
                exportedAt = System.currentTimeMillis(),
                transactions = transactions.map { it.toDto() },
                rules = rules.map { it.toDto() },
                categories = categories.map { it.toDto() },
                ownAccounts = ownAccounts.map { account ->
                    account.toDto(namesByAccount[account.id]?.map { it.display }.orEmpty())
                },
                budgets = budgets.map { it.toDto() },
            ),
        )
    }

    fun fromJson(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)

    private val CSV_HEADER = listOf(
        "Date", "Direction", "Amount", "Currency", "Counterparty",
        "Category", "Person", "Institution", "Reference", "Note",
    )

    fun toCsv(transactions: List<TransactionEntity>): String = buildString {
        appendLine(CSV_HEADER.joinToString(",") { csvField(it) })
        transactions.forEach { t ->
            appendLine(
                listOf(
                    csvStamp.format(Instant.ofEpochMilli(t.occurredAt)),
                    if (t.direction == "CREDIT") "Money in" else "Money out",
                    major(t.amountMinor),
                    t.currency,
                    t.counterparty.orEmpty(),
                    t.category.orEmpty(),
                    t.person.orEmpty(),
                    t.institution,
                    t.reference.orEmpty(),
                    t.notes.orEmpty(),
                ).joinToString(",") { csvField(it) },
            )
        }
    }

    private fun major(minor: Long): String = String.format(Locale.US, "%.2f", minor / 100.0)

    /**
     * Quotes a CSV value when it contains a comma, quote or newline.
     *
     * Values a spreadsheet would treat as a formula get a leading apostrophe
     * first. The counterparty and narration come from SMS text, which anyone can
     * send, so a sender named "=HYPERLINK(...)" would otherwise become a live
     * formula in Excel or Sheets. Amounts and dates never start with these
     * characters, so they are unchanged.
     */
    private fun csvField(value: String): String {
        val safe = if (value.firstOrNull() in FORMULA_LEADS) "'$value" else value
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }

    private val FORMULA_LEADS = setOf('=', '+', '-', '@', '\t', '\r')
}
