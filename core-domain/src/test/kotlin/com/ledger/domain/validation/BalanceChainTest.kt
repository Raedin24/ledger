package com.ledger.domain.validation

import com.ledger.domain.model.Direction
import com.ledger.domain.model.Money
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BalanceChainTest {

    /**
     * Builds the amount from text so it is exact. Going through Double loses
     * pesewas: 128.64 * 100 is 12863.999..., which truncates to 12863.
     */
    private fun ghs(major: String) =
        Money(java.math.BigDecimal(major).movePointRight(2).longValueExact())

    @Test
    fun `credit adds the amount`() {
        assertTrue(
            BalanceChain.follows(
                previousBalance = ghs("100.00"),
                direction = Direction.CREDIT,
                amount = ghs("50.00"),
                fee = null,
                balanceAfter = ghs("150.00"),
            )
        )
    }

    @Test
    fun `debit removes the amount`() {
        assertTrue(
            BalanceChain.follows(
                previousBalance = ghs("100.00"),
                direction = Direction.DEBIT,
                amount = ghs("30.00"),
                fee = null,
                balanceAfter = ghs("70.00"),
            )
        )
    }

    @Test
    fun `debit removes the fee as well as the amount`() {
        assertTrue(
            BalanceChain.follows(
                previousBalance = ghs("100.00"),
                direction = Direction.DEBIT,
                amount = ghs("30.00"),
                fee = ghs("1.50"),
                balanceAfter = ghs("68.50"),
            )
        )
    }

    @Test
    fun `a debit that ignores its own fee does not follow`() {
        assertFalse(
            BalanceChain.follows(
                previousBalance = ghs("100.00"),
                direction = Direction.DEBIT,
                amount = ghs("30.00"),
                fee = ghs("1.50"),
                balanceAfter = ghs("70.00"),
            )
        )
    }

    /**
     * A fake message has to guess the balance it lands on. Anything but the exact
     * figure breaks the chain.
     */
    @Test
    fun `an injected transaction guessing the balance does not follow`() {
        assertFalse(
            BalanceChain.follows(
                previousBalance = ghs("128.64"),
                direction = Direction.CREDIT,
                amount = ghs("500.00"),
                fee = null,
                balanceAfter = ghs("628.00"),   // guessed; the truth is 628.64
            )
        )
    }

    /** A guess that is exactly right can't be told apart, and that is expected. */
    @Test
    fun `an injected transaction that guesses the balance exactly does follow`() {
        assertTrue(
            BalanceChain.follows(
                previousBalance = ghs("128.64"),
                direction = Direction.CREDIT,
                amount = ghs("500.00"),
                fee = null,
                balanceAfter = ghs("628.64"),
            )
        )
    }

    /** One pesewa off is still a break. There is no tolerance; these are integers. */
    @Test
    fun `a single pesewa of drift does not follow`() {
        assertFalse(
            BalanceChain.follows(
                previousBalance = ghs("100.00"),
                direction = Direction.DEBIT,
                amount = ghs("30.00"),
                fee = null,
                balanceAfter = Money(6999),
            )
        )
    }

    /** A missed message looks the same as a fake one. */
    @Test
    fun `a gap in the chain does not follow`() {
        assertFalse(
            BalanceChain.follows(
                previousBalance = ghs("100.00"),
                direction = Direction.DEBIT,
                amount = ghs("10.00"),
                fee = null,
                balanceAfter = ghs("45.00"),    // something spent in between
            )
        )
    }

    @Test
    fun `an overdrawn balance still follows when the arithmetic holds`() {
        assertTrue(
            BalanceChain.follows(
                previousBalance = ghs("10.00"),
                direction = Direction.DEBIT,
                amount = ghs("15.00"),
                fee = null,
                balanceAfter = Money(-500),
            )
        )
    }
}
