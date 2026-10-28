package com.ledger.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount of money in minor units (pesewas for GHS), so there are no
 * floating-point rounding errors. Never use Double for money.
 *
 * `minor` is the amount in the smallest unit (GHS 12.50 is 1250).
 */
@JvmInline
value class Money(val minor: Long) : Comparable<Money> {

    val major: BigDecimal
        get() = BigDecimal(minor).movePointLeft(2)

    operator fun plus(other: Money) = Money(minor + other.minor)
    operator fun minus(other: Money) = Money(minor - other.minor)

    override fun compareTo(other: Money): Int = minor.compareTo(other.minor)

    override fun toString(): String = major.setScale(2, RoundingMode.UNNECESSARY).toPlainString()

    companion object {
        val ZERO = Money(0)

        /**
         * The largest amount, fee or balance the app accepts: GHS 10,000,000.
         *
         * Mobile-money wallets are limited by regulation to far less, so no real
         * alert comes close. Anything larger is rejected, which also keeps SQLite's
         * integer SUM() far away from overflowing.
         */
        val CEILING = Money(1_000_000_000L)

        /** True when [money] is within [CEILING] (null counts as within). */
        fun withinCeiling(money: Money?): Boolean = money == null || money.minor <= CEILING.minor

        /**
         * Parses an amount such as "1,250.00", "1250" or "12.5". Thousands separators
         * are removed. Returns null for anything malformed, so callers reject it
         * instead of guessing.
         */
        fun parse(raw: String): Money? {
            val cleaned = raw.trim().replace(",", "")
            if (cleaned.isEmpty()) return null
            val bd = cleaned.toBigDecimalOrNull() ?: return null
            if (bd.scale() > 2) return null
            return try {
                Money(bd.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact())
            } catch (_: ArithmeticException) {
                null
            }
        }

        private fun String.toBigDecimalOrNull(): BigDecimal? =
            try { BigDecimal(this) } catch (_: NumberFormatException) { null }
    }
}
