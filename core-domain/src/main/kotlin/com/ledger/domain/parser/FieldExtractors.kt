package com.ledger.domain.parser

import com.ledger.domain.model.Money

/**
 * Shared, keyword-anchored field extractors.
 *
 * Deliberately built from small labelled regexes rather than one giant
 * positional pattern per sender, because Ghanaian telco/bank SMS wording drifts
 * frequently (field order, punctuation, "Current Balance" vs "Avail Bal").
 * Anchoring each field to its own label keeps the parser robust to reordering
 * while still demanding that every required label is present for a strict match.
 */
internal object FieldExtractors {

    /** GHS 1,250.00 / GH¢1250 / ₵12.50 / GHc 12.5 — currency token is required. */
    private const val CURRENCY = "(?:GHS|GH¢|GHc|GH\\?|₵|GH\\s?cedis?)"
    private const val NUMBER = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"

    private val AMOUNT_TOKEN = Regex("$CURRENCY\\s?$NUMBER", RegexOption.IGNORE_CASE)

    // Handles "Balance: GHS x", "balance is GHS x" (Telecel), "new balance of GHS x".
    private val BALANCE = Regex(
        "(?:current|available|avail\\.?|new|remaining)?\\s*bal(?:ance)?(?:\\s+(?:is|was|of))?[:.\\s]*$CURRENCY?\\s?$NUMBER",
        RegexOption.IGNORE_CASE,
    )

    // Handles "Fee: GHS x", "Fee charged: GHS x", "Fee was GHS x".
    private val FEE = Regex(
        "(?:fees?|charges?|e-?levy|e\\.?levy|commission)(?:\\s+(?:charged|was|of))?[:.\\s]*$CURRENCY?\\s?$NUMBER",
        RegexOption.IGNORE_CASE,
    )

    // The provider TRANSACTION ID — the reliable, unique idempotency key.
    // Deliberately NOT the "Ref:"/"Reference:" narration, which is unreliable,
    // user/terminal-authored, and per the design must never be persisted.
    private val TXN_ID_LABELED = Regex(
        "(?:financial\\s+transaction\\s+id|transaction\\s+id|txn\\s*id|trans\\.?\\s*id)[:.\\s#]*([A-Za-z0-9\\-]{4,})",
        RegexOption.IGNORE_CASE,
    )

    // Telecel Cash carries no labelled id; its unique key is the 16-digit
    // confirmation code that opens the message: "0000013581370834 Confirmed."
    private val TXN_ID_LEADING = Regex("^\\s*(\\d{10,})\\s+confirmed", RegexOption.IGNORE_CASE)

    // The free-text narration ("Ref: uber", "Message:Interest for July...").
    // Captured as a REVIEW HINT only — never a matching/dedup key. Stops at the
    // next sentence or a structural label so it never swallows the balance/id.
    //
    // The `:`/`-` separator is REQUIRED. Without it the label could sit anywhere
    // in a sentence and drag prose in behind it — MTN's refund alert says
    // "Message from refunder: ." and the optional form captured the words
    // "from refunder" as though they were the note.
    private val NARRATION = Regex(
        "\\b(?:reference|ref|message|narration|reason)\\b\\s*[:\\-]\\s*([^.\\n]{2,80}?)\\s*(?=\\.|\\n|$|(?:financial\\s+)?transaction\\s+id\\b|available\\s+bal|current\\s+bal|your\\s+new\\s+bal)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A bank crediting a wallet names the *account* first and itself second:
     * "from: 1441****123 , Ecobank." The masked digits defeat the ordinary
     * counterparty capture (`*` is not a name character, so it matches nothing
     * and the message lands as "Unknown sender"), and they are useless as a label
     * anyway — the bank's name is the part that identifies where the money came
     * from.
     */
    private val MASKED_SOURCE = Regex(
        "\\bfrom:?\\s*(?:[0-9A-Za-z]*\\*{2,}[0-9A-Za-z]*|\\d{6,})\\s*,\\s*" +
            "([A-Za-z][A-Za-z0-9 .&'\\-]{1,30}?)\\s*(?=[.,;:]|$)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A refund names its source before the verb rather than after a preposition:
     * "MTN BUNDLE (CISNG.sp) has successfully refunded GHS 3.00 to your ...".
     * The trailing service code in brackets is dropped — it varies per campaign
     * and would split one merchant into many counterparties.
     */
    private val REFUND_SOURCE = Regex(
        "^(?:hello|hi|dear\\s+customer)?[,\\s]*(.{2,60}?)\\s+has\\s+(?:successfully\\s+)?refunded\\b",
        RegexOption.IGNORE_CASE,
    )

    private val TRAILING_BRACKET = Regex("\\s*\\([^)]*\\)\\s*$")

    /**
     * Cross-network transfers are addressed to a gateway, never to a person: MTN
     * bills a send to Telecel as "to TELECEL PUSH" and one to AirtelTigo as "to
     * TIGO PUSH". Taken at face value every such payment collapses onto one of
     * two counterparties, which is worse than useless for rules and reporting.
     */
    private val GATEWAY_LABEL = Regex(
        "^(?:telecel|tigo|airteltigo|at|mtn|vodafone|ghanapay)\\s+push$",
        RegexOption.IGNORE_CASE,
    )

    /** What the gateway hides, spelled out in the narration: "NAME,MSISDN,note". */
    private val NARRATED_TRIPLE = Regex(
        "^([A-Za-z][A-Za-z .'\\-]{1,40}?)\\s*,\\s*((?:\\+?233|0)\\d{9})\\s*(?:,\\s*(.{1,60}))?$",
    )

    /** Ghana MSISDN in local (0XXXXXXXXX) or international (233XXXXXXXXX) form. */
    private val PHONE = Regex("\\b(?:233|\\+233|0)\\d{9}\\b")

    data class Anchored(val value: Money, val range: IntRange)

    fun balance(body: String): Anchored? = BALANCE.find(body)?.let { m ->
        Money.parse(m.groupValues[1])?.let { Anchored(it, m.range) }
    }

    fun fee(body: String): Anchored? = FEE.find(body)?.let { m ->
        Money.parse(m.groupValues[1])?.let { Anchored(it, m.range) }
    }

    /** Labelled transaction id, else Telecel's leading confirmation code, else null
     *  (ref-less types such as interest credits — dedup falls back to a composite key). */
    fun transactionId(body: String): String? =
        (TXN_ID_LABELED.find(body)?.groupValues?.get(1)
            ?: TXN_ID_LEADING.find(body.trim())?.groupValues?.get(1))
            ?.takeIf { it.isNotBlank() }

    /** Free-text narration to show at review time. Null when absent/empty. A
     *  value that is only the reference label with no content is dropped. */
    fun referenceHint(body: String): String? =
        NARRATION.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ' ')
            ?.takeIf { it.isNotBlank() && it.length >= 2 }

    /**
     * The transaction amount is the first currency amount in the body whose
     * span does not overlap the balance or fee matches. This avoids the classic
     * bug of reading the balance as the amount.
     */
    fun transactionAmount(body: String, balance: Anchored?, fee: Anchored?): Money? {
        val excluded = listOfNotNull(balance?.range, fee?.range)
        for (m in AMOUNT_TOKEN.findAll(body)) {
            if (excluded.any { it.overlaps(m.range) }) continue
            Money.parse(m.groupValues[1])?.let { return it }
        }
        return null
    }

    /** True if the body contains any currency-tagged amount at all. */
    fun hasCurrencyAmount(body: String): Boolean = AMOUNT_TOKEN.containsMatchIn(body)

    fun phone(body: String): String? = PHONE.find(body)?.value

    /** The bank named after a masked account number in a credit, or null. */
    fun maskedSourceName(body: String): String? =
        MASKED_SOURCE.find(body)?.groupValues?.get(1)?.cleanName()

    /** Who issued a refund, or null when this isn't a refund. */
    fun refundSource(body: String): String? =
        REFUND_SOURCE.find(body)?.groupValues?.get(1)
            ?.replace(TRAILING_BRACKET, "")?.cleanName()

    /** True when [counterparty] is a cross-network gateway rather than a party. */
    fun isGatewayLabel(counterparty: String?): Boolean =
        counterparty != null && GATEWAY_LABEL.matches(counterparty.trim())

    /** The party a gateway stands in for, recovered from the narration. */
    fun narratedParty(referenceHint: String?): String? =
        referenceHint?.trim()?.let { NARRATED_TRIPLE.find(it) }?.groupValues?.get(1)?.cleanName()

    private fun String.cleanName(): String? =
        trim().trimEnd('.', ',', ' ').trim().takeIf { it.isNotBlank() }

    /**
     * Extracts a counterparty name/number following a preposition such as
     * "to" (debit) or "from" (credit). Falls back to a phone number anywhere in
     * the body. Names are captured up to the next sentence/clause boundary and
     * trimmed of trailing punctuation.
     */
    fun counterparty(body: String, prepositions: List<String>): String? {
        for (prep in prepositions) {
            val r = Regex(
                "\\b$prep:?\\s+([A-Za-z0-9][A-Za-z0-9 .&'/\\-]{1,40}?)(?=[.,;:]|\\s+(?:for|on|with|has|current|avail|bal|ref|fee|transaction|txn|new|dated)\\b|\\s*\\(|$)",
                RegexOption.IGNORE_CASE,
            )
            r.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ' ')
                ?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return phone(body)
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last
}
