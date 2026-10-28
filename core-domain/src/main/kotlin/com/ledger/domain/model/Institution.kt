package com.ledger.domain.model

/**
 * A wallet provider whose SMS alerts the app reads.
 *
 * `senderIds` are the SMS sender names seen on real messages. They only decide
 * which messages are worth parsing. They are not what keeps OTPs out, because
 * some providers send OTPs from the same sender as transaction alerts. That
 * job belongs to the strict template check in TransactionValidator.
 *
 * Each provider has exactly one sender name. Android can't verify sender
 * names, so every extra name is one more name a fake message could use. A
 * provider that changes its sender name needs a new release anyway.
 */
// Only providers with a confirmed transaction SMS format are listed. A
// provider that notifies in its app rather than by SMS doesn't belong here,
// even if its name appears inside another provider's messages.
//
// AirtelTigo Cash isn't supported yet; it needs real sample messages.
//
// Adding a provider means an entry here plus a matching SenderTemplate,
// built from real sample messages.
//
// [CASH] is the exception: money on hand, entered by the user. It has no
// sender name, so [fromSenderId] never returns it and no SMS is ever read as
// cash.
enum class Institution(val displayName: String, val senderIds: Set<String>) {
    MTN_MOMO("MTN MoMo", setOf("MobileMoney")),
    GHANAPAY("GhanaPay", setOf("GhanaPay")),
    TELECEL_CASH("Telecel Cash", setOf("T-CASH")),
    CASH("Cash", emptySet());

    /** True for a wallet whose alerts are captured, false for [CASH]. */
    val isProvider: Boolean get() = senderIds.isNotEmpty()

    companion object {
        /** The providers that send alerts: everything except [CASH]. */
        val PROVIDERS: List<Institution> = entries.filter { it.isProvider }

        /** Looks up a provider by exact sender name, ignoring case. Null if not supported. */
        fun fromSenderId(senderId: String): Institution? {
            val needle = senderId.trim()
            return entries.firstOrNull { inst ->
                inst.senderIds.any { it.equals(needle, ignoreCase = true) }
            }
        }
    }
}
