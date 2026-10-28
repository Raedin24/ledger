package com.ledger.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The sum typed on the amount keypad: numbers joined by `+` and `−`, such as
 * `"120+45.5−10"`. Adding up a few cash purchases is common, so the keypad
 * does it instead of sending the user to a calculator.
 *
 * It is kept as text and worked out when needed, so the screen can show what
 * was typed next to the running total.
 */
object AmountExpression {

    const val PLUS = '+'
    const val MINUS = '−'

    /**
     * Total in minor units, or null while the expression can't be read yet (empty,
     * a lone operator, "1..2"). A trailing operator is ignored so the total keeps
     * showing while the next number is typed.
     */
    fun evaluate(expression: String): Long? {
        val text = expression.trimEnd(PLUS, MINUS)
        if (text.isEmpty()) return null
        var total = BigDecimal.ZERO
        var sign = 1
        val number = StringBuilder()
        fun flush(): Boolean {
            val value = number.toString().toBigDecimalOrNull() ?: return false
            total = if (sign > 0) total + value else total - value
            number.clear()
            return true
        }
        for ((i, c) in text.withIndex()) {
            when {
                c.isDigit() || c == '.' -> number.append(c)
                c == PLUS || c == MINUS || c == '-' -> {
                    // A leading sign has no number before it to close.
                    if (i > 0 && !flush()) return null
                    sign = if (c == PLUS) 1 else -1
                }
                else -> return null
            }
        }
        if (!flush()) return null
        return total.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }

    /** True when [expression] has more than one term, so a total is worth showing. */
    fun hasOperator(expression: String): Boolean =
        expression.drop(1).any { it == PLUS || it == MINUS }

    /**
     * Applies one keypad press. Keeps the text well-formed as it's typed: one
     * operator between terms (a second replaces the first), one decimal point per
     * number, at most two decimals, and no operator before the first digit.
     */
    fun press(expression: String, key: Char): String {
        val last = expression.lastOrNull()
        val current = expression.takeLastWhile { it != PLUS && it != MINUS }
        return when {
            key == PLUS || key == MINUS -> when {
                expression.isEmpty() -> expression
                last == PLUS || last == MINUS -> expression.dropLast(1) + key
                else -> expression + key
            }
            key == '.' -> when {
                '.' in current -> expression
                current.isEmpty() -> "${expression}0."
                else -> "$expression."
            }
            key.isDigit() -> when {
                current.substringAfter('.', "").length >= 2 && '.' in current -> expression
                current == "0" -> expression.dropLast(1) + key
                current.length >= MAX_DIGITS -> expression
                else -> expression + key
            }
            else -> expression
        }
    }

    /** One step of backspace. */
    fun backspace(expression: String): String = expression.dropLast(1)

    /** Replaces the expression with its total, as `=` does on a calculator. */
    fun collapse(expression: String): String {
        val minor = evaluate(expression) ?: return expression
        if (minor < 0) return expression
        return BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()
    }

    /** Stops a stuck key making a huge number. Ten digits is ₵9,999,999,999. */
    private const val MAX_DIGITS = 10
}
