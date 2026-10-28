package com.ledger.domain.validation

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Money

/**
 * Checks that a transaction's balance follows from the last balance the same
 * provider reported.
 *
 * Android has no way to verify an SMS sender name, so a message that parses
 * cleanly isn't proof it came from the provider. The running balance is
 * harder to fake, because a fake message would have to know the user's
 * current balance.
 *
 * This lives in core-domain, free of Android, so it can be tested thoroughly
 * without a database.
 */
object BalanceChain {

    /**
     * Whether [balanceAfter] is what [previousBalance] becomes after this
     * transaction.
     *
     * A debit removes the amount and the fee, because providers report the
     * balance after both. A credit adds the amount.
     *
     * False doesn't mean fraud. A missed message (phone off, deleted SMS, gaps in
     * an inbox import) looks exactly the same. Callers should send a break to
     * review, not reject it.
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
