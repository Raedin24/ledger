package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution
import com.ledger.domain.model.Money
import com.ledger.domain.model.Transaction
import com.ledger.domain.validation.TransactionValidator
import com.ledger.domain.validation.ValidationResult

/**
 * Turns an incoming SMS into a [ParseResult].
 *
 * The steps are: check the sender, extract the fields, then validate them
 * strictly. The parser never keeps the message text. The rule that keeps OTPs
 * out lives in [TransactionValidator], which the parser uses to classify the
 * fields.
 */
class SmsParser(
    private val validator: TransactionValidator = TransactionValidator(),
    private val defaultCurrency: String = "GHS",
) {

    /**
     * Every supported provider is captured. There is no per-user list: a provider
     * is supported once its template has been tested against real messages.
     */
    fun parse(sms: IncomingSms): ParseResult {
        val institution = Institution.fromSenderId(sms.senderId)
            ?: return ParseResult.Ignored(sms.senderId, "sender not a recognised institution")

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

        // The validator doesn't see the fee, so apply the same ceiling here.
        if (!Money.withinCeiling(fee?.value)) {
            return ParseResult.Discarded(institution, "fee beyond any wallet limit")
        }

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
                    occurredAt = sms.receivedAt,  // TODO: use the time in the message body when there is one
                    capturedAt = sms.receivedAt,
                    cashMovement = template.isCashMovement(body, partial.direction!!),
                )
            )
        }
    }

    /**
     * Who the money moved to or from.
     *
     * Usually this is the name after a preposition ("to", "from"). Three common
     * message shapes need special handling:
     *
     *  - A refund names its source before the verb. This is checked first,
     *    because MTN's refund alert ends "Message from refunder: .", and looking
     *    for "from" would give "refunder".
     *  - A gateway stands in for the person. MTN addresses a cross-network send to
     *    "TELECEL PUSH" or "TIGO PUSH" and puts the real recipient in the
     *    narration, so the narration is used instead.
     *  - A bank sits behind a masked account ("from: 1441****123 , Ecobank"), so
     *    the bank's name is used.
     *
     * The narration only ever replaces a gateway name. It never overrides a real
     * counterparty.
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
            // Buying data or airtime from the provider has no third party, so the
            // provider is named as the merchant. That keeps these out of the review
            // queue and gives a rule something stable to match. It comes last, so an
            // airtime top-up for a number keeps that number.
            ?: template.institution.displayName.takeIf {
                FieldExtractors.isSelfServicePurchase(body)
            }
    }
}
