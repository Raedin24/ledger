package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution
import com.ledger.domain.model.Transaction
import com.ledger.domain.validation.TransactionValidator
import com.ledger.domain.validation.ValidationResult

/**
 * Turns a transient inbound SMS into a [ParseResult].
 *
 * Pipeline: sender scoping -> field extraction -> strict validation. The parser
 * itself makes no privacy-relevant decisions beyond never retaining the body;
 * the OTP guarantee lives entirely in [TransactionValidator], which the parser
 * consults to classify the extracted fields.
 */
class SmsParser(
    private val validator: TransactionValidator = TransactionValidator(),
    private val defaultCurrency: String = "GHS",
) {

    /**
     * @param allowList institutions enabled for capture. Passed per call rather
     *  than held on the parser because the set changes while the app runs and
     *  the parser is a singleton — a constructor argument would freeze whatever
     *  was enabled at process start.
     *
     *  An empty set means "all recognised institutions". Onboarding can be
     *  skipped, so treating empty as "none" would leave capture silently dead
     *  for anyone who skipped it.
     */
    fun parse(sms: IncomingSms, allowList: Set<Institution> = emptySet()): ParseResult {
        val institution = Institution.fromSenderId(sms.senderId)
            ?: return ParseResult.Ignored(sms.senderId, "sender not a recognised institution")

        if (allowList.isNotEmpty() && institution !in allowList) {
            return ParseResult.Ignored(sms.senderId, "institution not enabled by user")
        }

        val template = SenderTemplate.forInstitution(institution)
            ?: return ParseResult.Ignored(sms.senderId, "no template for institution")

        val body = sms.body
        val balance = FieldExtractors.balance(body)
        val fee = FieldExtractors.fee(body)
        val amount = FieldExtractors.transactionAmount(body, balance, fee)
        val reference = FieldExtractors.transactionId(body)
        val referenceHint = FieldExtractors.referenceHint(body)
        val direction = template.detectDirection(body)
        val counterparty = resolveCounterparty(body, template, direction, referenceHint)

        val partial = PartialFields(
            direction = direction,
            amount = amount,
            balanceAfter = balance?.value,
            reference = reference,
            counterparty = counterparty,
            referenceHint = referenceHint,
        )

        return when (val v = validator.validate(partial, hasCurrencyAmount = FieldExtractors.hasCurrencyAmount(body))) {
            is ValidationResult.NotTransaction ->
                ParseResult.Discarded(institution, v.reason)

            is ValidationResult.Incomplete ->
                ParseResult.NeedsAttention(institution, sms.receivedAt, partial, v.reason)

            is ValidationResult.Valid -> ParseResult.Parsed(
                Transaction(
                    reference = partial.reference,
                    institution = institution,
                    direction = partial.direction!!,
                    amount = partial.amount!!,
                    balanceAfter = partial.balanceAfter,
                    fee = fee?.value,
                    currency = defaultCurrency,
                    counterparty = partial.counterparty,
                    referenceHint = partial.referenceHint,
                    occurredAt = sms.receivedAt, // TODO(phase0): parse in-body timestamp when present
                    capturedAt = sms.receivedAt,
                )
            )
        }
    }

    /**
     * Who the money actually moved to or from.
     *
     * The preposition capture is the general case, but three real shapes defeat
     * it, and all three are common enough that leaving them produces a ledger
     * full of "Unknown sender" and duplicate gateway names:
     *
     *  - **A refund names its source before the verb**, not after a preposition.
     *    This one is checked *first*, because the prepositions don't merely miss
     *    it, they actively mislead: MTN's refund alert ends "Message from
     *    refunder: .", so scanning for "from" on a credit yields the word
     *    "refunder" as the counterparty.
     *  - **A gateway stands in for the person.** MTN addresses a cross-network
     *    send to "TELECEL PUSH" / "TIGO PUSH" and prints the real recipient in
     *    the narration. The narration wins, or every off-network payment the user
     *    ever makes shares one of two counterparties.
     *  - **A bank hides behind a masked account.** "from: 1441****123 , Ecobank"
     *    — the mask is not name-shaped, so the ordinary capture returns nothing.
     *
     * The narration is only consulted to *replace a gateway*, never to override a
     * real counterparty, so an ordinary transfer that happens to carry a name in
     * its reference is untouched.
     */
    private fun resolveCounterparty(
        body: String,
        template: SenderTemplate,
        direction: Direction?,
        referenceHint: String?,
    ): String? {
        FieldExtractors.refundSource(body)?.let { return it }

        val direct = direction?.let {
            FieldExtractors.counterparty(body, template.prepositionsFor(it))
        }
        if (FieldExtractors.isGatewayLabel(direct)) {
            FieldExtractors.narratedParty(referenceHint)?.let { return it }
        }
        return direct
            ?: FieldExtractors.maskedSourceName(body)
            ?: FieldExtractors.phone(body)
    }
}
