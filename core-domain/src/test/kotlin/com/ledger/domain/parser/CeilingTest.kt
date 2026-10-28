package com.ledger.domain.parser

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

/** Amounts beyond Money.CEILING (GHS 10,000,000) are never recorded. */
class CeilingTest {

    private val parser = SmsParser()
    private val now = Instant.parse("2026-10-06T10:00:00Z")
    private fun parse(body: String) = parser.parse(IncomingSms("MobileMoney", body, now))

    @Test
    fun `a forged quadrillion is discarded`() {
        val r = parse("Payment for GHS92,233,720,368,547,758.07 to X .Current Balance: GHS 1.00. Transaction Id: 85762523456. Fee charged: GHS0.00,Tax Charged 0.")
        assertInstanceOf(ParseResult.Discarded::class.java, r)
    }

    @Test
    fun `a balance beyond the ceiling is discarded`() {
        val r = parse("Payment for GHS3.00 to X .Current Balance: GHS 10,000,000.01. Transaction Id: 85762523457. Fee charged: GHS0.00,Tax Charged 0.")
        assertInstanceOf(ParseResult.Discarded::class.java, r)
    }

    @Test
    fun `an amount at the ceiling is still a transaction`() {
        val r = parse("Payment for GHS10,000,000.00 to X .Current Balance: GHS 1.00. Transaction Id: 85762523458. Fee charged: GHS0.00,Tax Charged 0.")
        assertInstanceOf(ParseResult.Parsed::class.java, r)
    }
}
