package com.ledger.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A currency amount stored as minor units (pesewas for GHS) to avoid all
 * floating-point rounding error. Never use Double for money.
 *
 * `minor` is the amount in the smallest unit (e.g. GHS 12.50 -> 1250).
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
         * Parses a human amount string such as "1,250.00", "1250", "12.5".
         * Thousands separators are stripped. Returns null on any malformed
         * input so callers can reject rather than coerce.
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
