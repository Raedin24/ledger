package com.ledger.domain.parser

import com.ledger.domain.model.Money
import java.util.concurrent.ConcurrentHashMap

/**
 * Field extractors shared by every provider.
 *
 * Each field is found by its own label ("Balance:", "Fee:") with a small
 * regex, instead of one large pattern per provider. Provider wording changes
 * often (field order, punctuation, "Current Balance" or "Avail Bal"), and
 * matching each label separately copes with that. A strict match still needs
 * every required label to be present.
 */
internal object FieldExtractors {

    /** GHS 1,250.00, GH¢1250, ₵12.50, GHc 12.5. The currency token is required. */
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

    // The provider's transaction id: the reliable, unique key for a message.
    // Not the "Ref:" narration, which is free text written by the sender and
    // is never used as a key.
    private val TXN_ID_LABELED = Regex(
        "(?:financial\\s+transaction\\s+id|transaction\\s+id|txn\\s*id|trans\\.?\\s*id)[:.\\s#]*([A-Za-z0-9\\-]{4,})",
        RegexOption.IGNORE_CASE,
    )

    // Telecel Cash has no labelled id. Its unique key is the confirmation code
    // at the start of the message: "0000013581370834 Confirmed."
    private val TXN_ID_LEADING = Regex("^\\s*(\\d{10,})\\s+confirmed", RegexOption.IGNORE_CASE)

    // The free-text narration ("Ref: uber", "Message:Interest for July...").
    // Kept only as a hint for review, never as a key. It stops at the next
    // sentence or label, so it never takes in the balance or the id.
    //
    // The ":" or "-" after the label is required. Without it, the label could
    // match in the middle of a sentence ("Message from refunder: .") and pick
    // up ordinary words as the note.
    private val NARRATION = Regex(
        "\\b(?:reference|ref|message|narration|reason)\\b\\s*[:\\-]\\s*([^.\\n]{2,80}?)\\s*(?=\\.|\\n|$|(?:financial\\s+)?transaction\\s+id\\b|available\\s+bal|current\\s+bal|your\\s+new\\s+bal)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A bank paying into a wallet gives the masked account first and the bank's
     * name second: "from: 1441****123 , Ecobank." The masked number isn't a usable
     * name, so the bank's name is taken instead.
     */
    private val MASKED_SOURCE = Regex(
        "\\bfrom:?\\s*(?:[0-9A-Za-z]*\\*{2,}[0-9A-Za-z]*|\\d{6,})\\s*,\\s*" +
            "([A-Za-z][A-Za-z0-9 .&'\\-]{1,30}?)\\s*(?=[.,;:]|$)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A refund names who issued it before the verb, not after a preposition:
     * "MTN BUNDLE (CISNG.sp) has successfully refunded GHS 3.00 to your ...".
     * The bracketed service code is dropped, because it changes per campaign and
     * would split one merchant into many.
     */
    private val REFUND_SOURCE = Regex(
        "^(?:hello|hi|dear\\s+customer)?[,\\s]*(.{2,60}?)\\s+has\\s+(?:successfully\\s+)?refunded\\b",
        RegexOption.IGNORE_CASE,
    )

    private val TRAILING_BRACKET = Regex("\\s*\\([^)]*\\)\\s*$")

    /**
     * Cross-network transfers are addressed to a gateway, not a person. MTN shows
     * a send to Telecel as "to TELECEL PUSH" and one to AirtelTigo as "to TIGO
     * PUSH". Taken as written, every such payment would share one of two names.
     */
    private val GATEWAY_LABEL = Regex(
        "^(?:telecel|tigo|airteltigo|at|mtn|vodafone|ghanapay)\\s+push$",
        RegexOption.IGNORE_CASE,
    )

    /** The real recipient behind a gateway, given in the narration as "NAME,MSISDN,note". */
    private val NARRATED_TRIPLE = Regex(
        "^([A-Za-z][A-Za-z .'\\-]{1,40}?)\\s*,\\s*((?:\\+?233|0)\\d{9})\\s*(?:,\\s*(.{1,60}))?$",
    )

    /** A Ghana phone number in local (0XXXXXXXXX) or international (233XXXXXXXXX) form. */
    private val PHONE = Regex("\\b(?:233|\\+233|0)\\d{9}\\b")

    data class Anchored(val value: Money, val range: IntRange)

    fun balance(body: String): Anchored? = BALANCE.find(body)?.let { m ->
        Money.parse(m.groupValues[1])?.let { Anchored(it, m.range) }
    }

    fun fee(body: String): Anchored? = FEE.find(body)?.let { m ->
        Money.parse(m.groupValues[1])?.let { Anchored(it, m.range) }
    }

    /**
     * The labelled transaction id, or Telecel's leading confirmation code, or null.
     * Some messages have no id (interest credits, for example); dedup then uses a
     * key built from the other fields.
     */
    fun transactionId(body: String): String? =
        (TXN_ID_LABELED.find(body)?.groupValues?.get(1)
            ?: TXN_ID_LEADING.find(body.trim())?.groupValues?.get(1))
            ?.takeIf { it.isNotBlank() }

    /**
     * The free-text narration, shown during review. Null when missing, or when
     * the label is there with nothing after it.
     */
    fun referenceHint(body: String): String? =
        NARRATION.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ' ')
            ?.takeIf { it.isNotBlank() && it.length >= 2 }

    /**
     * The transaction amount: the first currency amount that isn't part of the
     * balance or fee matches. This stops the balance being read as the amount.
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
     * Words that can follow a preposition but are never a party. Without this,
     * "received at 14:32:19" would give "14", and "wait for confirmation" would
     * give "confirmation".
     */
    private val NON_PARTY_WORDS = setOf(
        "confirmation", "confirmations", "details", "processing", "approval",
        "verification", "further", "more", "you", "your", "it", "us", "me", "this",
    )

    /**
     * A match only counts as a party if it has a letter, or looks like a phone
     * number ("bought airtime for 0244000222"). Any other bare number is part of
     * something else.
     */
    private fun isPlausibleParty(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.lowercase() in NON_PARTY_WORDS) return false
        val hasLetter = trimmed.any { it.isLetter() }
        if (!hasLetter) return trimmed.count { it.isDigit() } >= 9
        return true
    }

    /**
     * Compiled preposition patterns, cached by preposition, so each message
     * doesn't compile them again.
     */
    private val prepositionPatterns = ConcurrentHashMap<String, Regex>()

    private fun prepositionPattern(prep: String): Regex =
        prepositionPatterns.getOrPut(prep) {
            Regex(
                "\\b$prep:?\\s+([A-Za-z0-9][A-Za-z0-9 .&'/\\-]{1,40}?)(?=[.,;:]|\\s+(?:for|on|with|has|current|avail|bal|ref|fee|transaction|txn|new|dated)\\b|\\s*\\(|$)",
                RegexOption.IGNORE_CASE,
            )
        }

    /**
     * The name or number after a preposition such as "to" (money out) or "from"
     * (money in), or failing that a phone number anywhere in the message.
     */
    fun counterparty(body: String, prepositions: List<String>): String? {
        for (prep in prepositions) {
            prepositionPattern(prep).find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', ',', ' ')
                ?.takeIf { isPlausibleParty(it) }?.let { return it }
        }
        return phone(body)
    }

    /**
     * True when the message is about buying airtime or data from the provider
     * itself. There is no third party; the provider is the merchant.
     */
    fun isSelfServicePurchase(body: String): Boolean {
        val lower = body.lowercase()
        return SELF_SERVICE_MARKERS.any { it in lower }
    }

    private val SELF_SERVICE_MARKERS =
        listOf("bundle purchase", "purchase of bundle", "data bundle", "of airtime", "airtime purchase")

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last
}
