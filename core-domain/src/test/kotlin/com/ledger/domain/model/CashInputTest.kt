package com.ledger.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FiscalMonthTest {

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun `calendar month is the default`() {
        val m = FiscalMonth.CALENDAR
        assertEquals(d("2026-09-01"), m.startOf(d("2026-09-15")))
        assertEquals(d("2026-09-30"), m.endOf(d("2026-09-01")))
    }

    @Test
    fun `a period starting on the 28th runs to the 27th`() {
        val m = FiscalMonth(28)
        assertEquals(d("2026-09-28"), m.startOf(d("2026-09-28")))
        assertEquals(d("2026-09-28"), m.startOf(d("2026-10-27")))
        assertEquals(d("2026-08-28"), m.startOf(d("2026-09-27")))
        assertEquals(d("2026-10-27"), m.endOf(d("2026-09-28")))
    }

    @Test
    fun `january into february keeps the start day`() {
        val m = FiscalMonth(28)
        assertEquals(d("2027-01-28"), m.startOf(d("2027-02-10")))
        assertEquals(d("2027-02-27"), m.endOf(d("2027-01-28")))
        assertEquals(31, m.lengthOf(d("2027-01-28")))
        assertEquals(28, m.lengthOf(d("2027-02-28")))
    }

    @Test
    fun `a day a month lacks falls back to its last day`() {
        val m = FiscalMonth(31)
        assertEquals(d("2027-01-31"), m.startOf(d("2027-02-15")))
        assertEquals(d("2027-02-28"), m.startOf(d("2027-02-28")))
        assertEquals(d("2027-02-28"), m.startOf(d("2027-03-30")))
        assertEquals(d("2027-03-31"), m.startOf(d("2027-03-31")))
        assertEquals(d("2027-02-27"), m.endOf(d("2027-01-31")))
        assertEquals(d("2027-03-30"), m.endOf(d("2027-02-28")))
        // Stepping keeps the chosen day rather than February's.
        assertEquals(d("2027-03-31"), m.shift(d("2027-02-28"), 1))
        assertEquals(d("2027-01-31"), m.shift(d("2027-02-28"), -1))
        assertEquals(d("2028-02-29"), FiscalMonth(30).startOf(d("2028-03-01")))
    }

    @Test
    fun `start day stays within a month`() {
        assertEquals(31, FiscalMonth(40).startDay)
        assertEquals(1, FiscalMonth(0).startDay)
    }
}

class AmountExpressionTest {

    private val p = AmountExpression.PLUS
    private val m = AmountExpression.MINUS

    @Test
    fun `adds and subtracts in minor units`() {
        assertEquals(15_500L, AmountExpression.evaluate("120${p}45.5${m}10.5"))
        assertEquals(1_000L, AmountExpression.evaluate("10"))
        assertEquals(5L, AmountExpression.evaluate("0.05"))
    }

    @Test
    fun `a trailing operator is ignored and junk is unreadable`() {
        assertEquals(12_000L, AmountExpression.evaluate("120$p"))
        assertNull(AmountExpression.evaluate(""))
        assertNull(AmountExpression.evaluate("1..2"))
        assertNull(AmountExpression.evaluate("12a"))
    }

    @Test
    fun `typing keeps the expression well formed`() {
        fun type(keys: String) = keys.fold("") { acc, k -> AmountExpression.press(acc, k) }
        assertEquals("", type("$p"))                         // no leading operator
        assertEquals("12$m", type("12$p$m"))                 // second operator replaces
        assertEquals("0.5", type(".5"))                      // bare point gets a zero
        assertEquals("1.25", type("1.257"))                  // two decimals max
        assertEquals("1.2", type("1.2."))                    // one point per number
        assertEquals("7", type("07"))                        // no leading zero
        assertEquals("3${p}0.5", type("3$p.5"))
    }

    @Test
    fun `equals collapses to the total`() {
        assertEquals("155.5", AmountExpression.collapse("120${p}45.5${m}10"))
        assertEquals("5", AmountExpression.collapse("5"))
    }
}
