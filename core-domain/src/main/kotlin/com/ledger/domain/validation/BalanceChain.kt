package com.ledger.domain.validation

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Money

/**
 * Arithmetic tying one transaction to the balance the same provider last
 * reported.
 *
 * Sender IDs are unauthenticated alphanumeric strings and Android exposes no way
 * to verify them, so a message parsing cleanly is not evidence it came from the
 * provider. The running balance is harder to forge: to inject a transaction that
 * does not contradict the chain, a sender has to already know the victim's
 * current balance.
 *
 * Kept here, framework-free, because it is the kind of rule worth testing
 * exhaustively without a database in the way.
 */
object BalanceChain {

    /**
     * Whether [balanceAfter] is what [previousBalance] becomes once this
     * transaction is applied.
     *
     * A debit is expected to remove the amount *and* the fee: providers report
     * the balance after both. A credit adds the amount, fees on inbound money
     * having already been taken by the sender.
     *
     * False does not mean fraud. A missed message — phone off, deleted SMS, an
     * inbox import with gaps — leaves exactly the same footprint, and a single
     * message carries nothing that tells the two apart. Callers are expected to
     * route a break to review rather than reject it.
     */
    fun follows(
        previousBalance: Money,
        direction: Direction,
        amount: Money,
        fee: Money?,
        balanceAfter: Money,
    ): Boolean {
        val expected = when (direction) {
            Direction.CREDIT -> previousBalance.minor + amount.minor
            Direction.DEBIT -> previousBalance.minor - amount.minor - (fee?.minor ?: 0L)
        }
        return balanceAfter.minor == expected
    }
}
