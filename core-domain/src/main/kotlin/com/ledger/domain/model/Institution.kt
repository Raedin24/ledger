package com.ledger.domain.model

/**
 * A financial institution / wallet provider whose SMS alerts are in scope.
 *
 * `senderIds` are the raw SMS sender addresses seen on real messages. They are
 * used ONLY to scope which incoming messages are worth parsing — they are
 * explicitly NOT the OTP-safety mechanism, because some providers send OTPs
 * from the same sender ID as transaction alerts. The strict template match (see
 * TransactionValidator) is the sole OTP guard.
 *
 * Exactly one address per provider, deliberately. Alphanumeric sender IDs are
 * unauthenticated and Android offers no way to verify them, so this set is the
 * only thing narrowing what reaches the parser at all; every extra alias is
 * another string an attacker can send as. A provider that changes its sender ID
 * breaks capture and needs a release either way, so carrying historical aliases
 * buys nothing and widens the surface.
 */
// Scope is the providers with a confirmed transaction-SMS template. A provider
// that notifies in-app rather than by SMS has nothing to capture and does not
// belong here, even though its name may still appear as a *counterparty* inside
// another provider's message — that is message text, not a sender.
//
// AirtelTigo Cash is not yet supported and awaits real sample messages.
//
// Adding a provider is an entry here plus a matching SenderTemplate, built from
// real sample messages.
enum class Institution(val displayName: String, val senderIds: Set<String>) {
    MTN_MOMO("MTN MoMo", setOf("MobileMoney")),
    GHANAPAY("GhanaPay", setOf("GhanaPay")),
    TELECEL_CASH("Telecel Cash", setOf("T-CASH"));

    companion object {
        /** Case-insensitive lookup by exact sender id. Null = out of scope. */
        fun fromSenderId(senderId: String): Institution? {
            val needle = senderId.trim()
            return entries.firstOrNull { inst ->
                inst.senderIds.any { it.equals(needle, ignoreCase = true) }
            }
        }
    }
}
