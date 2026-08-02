package com.ledger.app.data.backup

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
 * Pure (framework-free) serialisation between the ledger's entities and the
 * portable export formats:
 *  - **JSON** — the full, round-trippable backup document (also the import format).
 *  - **CSV** — a flat, spreadsheet-friendly view of transactions (export only).
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

    /** RFC-4180-ish quoting: wrap when the value has a comma, quote, or newline. */
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
