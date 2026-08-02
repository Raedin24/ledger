package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The parser test corpus is a HARD exit criterion (roadmap): positive matches
 * for every sender/type in scope, plus deliberate near-misses — including
 * OTP-shaped strings from the same sender IDs as real transactions — confirming
 * they are correctly rejected.
 */
class SmsParserTest {

    private val parser = SmsParser()
    private val now = Instant.parse("2026-07-21T10:00:00Z")

    private fun parse(sender: String, body: String) =
        parser.parse(IncomingSms(sender, body, now))

    // ---------- Positive matches ----------

    @Test fun `MTN MoMo debit to merchant parses`() {
        val r = parse("MobileMoney",
            "Payment made to MELCOM LTD for GHS250.00. Fee: GHS1.00. Current Balance: GHS1,340.20. Reference: 987654321. Financial Transaction Id: 5566778899.")
        val t = assertInstanceOf(ParseResult.Parsed::class.java, r).transaction
        assertEquals(Direction.DEBIT, t.direction)
        assertEquals(25000, t.amount.minor)
        assertEquals(134020, t.balanceAfter!!.minor)
        assertEquals(100, t.fee!!.minor)
        // reference is the provider TRANSACTION ID, not the "Reference:" narration.
        assertEquals("5566778899", t.reference)
        assertTrue(t.counterparty!!.contains("MELCOM"))
    }

    @Test fun `MTN MoMo credit from person parses`() {
        val r = parse("MobileMoney",
            "You have received GHS500.00 from KWAME MENSAH (0244123456). Current Balance: GHS1,840.20. Reference: gift4u. Financial Transaction Id: 111222333.")
        val t = assertInstanceOf(ParseResult.Parsed::class.java, r).transaction
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals(50000, t.amount.minor)
    }

    @Test fun `MTN interest credit with no counterparty parses and keeps hint`() {
        val r = parse("MobileMoney",
            "An amount of GHS 1.15 has been credited to your mobile money account. Message:Interest for July to September 2025. Your new balance: GHS 4.78. Financial Transaction Id: 69174987794.")
        val t = assertInstanceOf(ParseResult.Parsed::class.java, r).transaction
        assertEquals(Direction.CREDIT, t.direction)
        assertEquals(115, t.amount.minor)
        assertEquals("69174987794", t.reference)
        // The "Message:" narration is kept as a review hint, not as a key.
        assertEquals("Interest for July to September 2025", t.referenceHint)
    }

    @Test fun `reference narration is stored as a hint`() {
        val t = (parse("GhanaPay",
            "You have transferred GHS 20.00 to 0545000003 MTN MOBILE MONEY. Ref: uber. Transaction ID: 620008130548. Balance: GHS 13.58.")
            as ParseResult.Parsed).transaction
        assertEquals("uber", t.referenceHint)
        assertEquals("620008130548", t.reference) // hint is NOT the dedup key
    }

    @Test fun `GhanaPay credit parses`() {
        val r = parse("GhanaPay",
            "Your account has been credited with GHS1200.00 from ZENITH SALARY. New Balance GHS6,400.00. Transaction Id: GP778812.")
        assertInstanceOf(ParseResult.Parsed::class.java, r)
    }

    @Test fun `Telecel Cash debit parses`() {
        val r = parse("T-CASH",
            "Payment made to AGENT 0208889999. Amount GHS200.00. Fees GHS2.00. Balance: GHS345.10. Ref: TC4433221.")
        assertInstanceOf(ParseResult.Parsed::class.java, r)
    }

    // ---------- OTP / non-transaction near-misses (MUST be discarded) ----------

    @Test fun `plain OTP from MoMo sender is discarded`() {
        assertInstanceOf(ParseResult.Discarded::class.java,
            parse("MobileMoney", "Your one-time password is 483920. Do not share it with anyone."))
    }

    @Test fun `OTP containing an amount but no balance or reference is discarded`() {
        // The hard case: same sender ID as real alerts, and an amount is present.
        assertInstanceOf(ParseResult.Discarded::class.java,
            parse("MobileMoney",
                "Your approval code for payment of GHS50.00 to MELCOM is 456123. Do not share this code with anyone."))
    }

    @Test fun `verification code is discarded`() {
        assertInstanceOf(ParseResult.Discarded::class.java,
            parse("MobileMoney", "123456 is your MTN MoMo verification code. It expires in 5 minutes."))
    }

    @Test fun `marketing message is discarded`() {
        assertInstanceOf(ParseResult.Discarded::class.java,
            parse("MobileMoney", "Buy 5GB data for GHS10 now! Dial *138# to enjoy. T&Cs apply."))
    }

    @Test fun `balance enquiry reply is discarded`() {
        assertInstanceOf(ParseResult.Discarded::class.java,
            parse("MobileMoney", "Your current balance is GHS1,340.20. Thank you for using MTN MoMo."))
    }

    // ---------- Reference optional / ambiguous -> review ----------

    @Test fun `amount and balance without a reference still parses`() {
        // Reference is corroborating, not required; balance is the OTP guard.
        assertInstanceOf(ParseResult.Parsed::class.java,
            parse("MobileMoney", "Payment made to NEWVENDOR for GHS30.00. Current Balance: GHS900.00."))
    }

    @Test fun `amount and id but no balance is ambiguous and goes to review`() {
        assertInstanceOf(ParseResult.NeedsAttention::class.java,
            parse("MobileMoney", "You have received GHS80.00 from 0201112222. Transaction Id: 999888777."))
    }

    @Test fun `pending request with balance but no direction goes to review`() {
        assertInstanceOf(ParseResult.NeedsAttention::class.java,
            parse("T-CASH",
                "0000013497138849 confirmed. Your bundle purchase request of GHS3.00 on 2026-06-25 has been received. Your new Telecel Cash balance is GHS0.01."))
    }

    // ---------- Scoping ----------

    @Test fun `unknown sender is ignored`() {
        val r = parse("Jumia", "Payment made for GHS20.00. Balance: GHS5.00. Ref: 123456.")
        assertInstanceOf(ParseResult.Ignored::class.java, r)
    }

    @Test fun `raw body is never leaked into the parsed transaction`() {
        val body = "Payment made to MELCOM for GHS250.00. Current Balance: GHS1,340.20. Reference: 987654321."
        val t = (parse("MobileMoney", body) as ParseResult.Parsed).transaction
        // No field should contain the full raw SMS text.
        val fields = listOfNotNull(t.reference, t.counterparty, t.category, t.person, t.notes)
        assertTrue(fields.none { it.contains("Payment made") })
        assertNull(t.notes)
    }
}
