package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.Instant

/**
 * Test cases built from the shapes of real SMS alerts. Each one was seen on a
 * real device. Only add to this list, so the parser can't quietly stop
 * handling a known message.
 *
 * Names, phone numbers and account numbers are replaced with made-up ones. The
 * parser only cares about shape (field order, punctuation, digit counts), and
 * tests are no place for real people's numbers. Keep replacements the same
 * length and format as the originals, or the cases stop testing what they
 * claim.
 */
class RealCorpusTest {

    private val parser = SmsParser()
    private val now = Instant.parse("2026-07-21T10:00:00Z")

    private data class Case(
        val name: String, val sender: String, val body: String,
        val direction: Direction?, val id: String?,
        /** Only checked when set. */
        val counterparty: String? = null,
    )

    private val parsedCases = listOf(
        Case("GP transfer to MTN", "GhanaPay",
            "You have transferred GHS 20.00 to 0545000003 MTN MOBILE MONEY. Ref: uber. Transaction ID: 620008130548. Balance: GHS 13.58.",
            Direction.DEBIT, "620008130548"),
        // A bank crediting the wallet: the account is masked and the bank names
        // itself only afterwards, so the counterparty is the bank, not the mask.
        Case("GP received from bank", "GhanaPay",
            "You have received GHS 400.00 from: 2255****789 , Ecobank. Ref: REF: 705321835172116 . Transaction ID: 618920387156. Balance: GHS 506.58.",
            Direction.CREDIT, "618920387156", counterparty = "Ecobank"),
        Case("MTN bundle debit", "MobileMoney",
            "Payment for GHS3.00 to MTN BUNDLE .Current Balance: GHS 0.38. Transaction Id: 85762523456. Fee charged: GHS0.00,Tax Charged 0.",
            Direction.DEBIT, "85762523456"),
        Case("MTN payment received", "MobileMoney",
            "Payment received for GHS 15.00 from AKUA MENSAH  Current Balance: GHS 15.38 . Available Balance: GHS 15.38. Reference: Akua ,0551000001. Transaction ID: 85695601778. TRANSACTION FEE: 0.00",
            Direction.CREDIT, "85695601778"),
        Case("MTN your payment of", "MobileMoney",
            "Your payment of GHS 27.00 to RAYNAS KITCHEN has been completed at 2026-07-17 09:33:49. Reference: . Your new balance: GHS 7.38. Fee was GHS 0.50 Financial Transaction Id: 85544973531. External Transaction Id: -.",
            Direction.DEBIT, "85544973531"),
        Case("MTN cash in", "MobileMoney",
            "Cash In received for GHS 200.00 from ULTIMATE SUNSHINE ENTERPRISE. Current Balance GHS 402.71 Available Balance GHS 402.71. Transaction ID: 85031913596. Fee charged: GHS 0.",
            Direction.CREDIT, "85031913596"),
        Case("MTN cash out", "MobileMoney",
            "Cash Out made for GHS20.00 to ADOM VENTURES. Current Balance: GHS607.90 Financial Transaction Id: 84351116312. Fee charged: GHS0.50.",
            Direction.DEBIT, "84351116312"),
        // A refund is money coming back, even though "refunded ... to your
        // account" can read like money going out.
        Case("MTN refund", "MobileMoney",
            "Hello, MTN BUNDLE  (CISNG.sp) has successfully refunded GHS 3.00 to your mobile money account at 2026-07-11 23:09:24. Message from refunder: . Your new balance:295.83 GHS. Financial Transaction Id: 85211519729. External Transaction Id: -. TRANSACTION FEE IS 0",
            Direction.CREDIT, "85211519729", counterparty = "MTN BUNDLE"),
        // Cross-network sends are addressed to a gateway, and the real recipient
        // is in the narration. Without that, every one would be named
        // "TELECEL PUSH" or "TIGO PUSH".
        Case("MTN to Telecel via gateway", "MobileMoney",
            "Your payment of GHS 24.00 to TELECEL PUSH has been completed at 2026-05-22 08:15:05. Your new balance: GHS 648.90. Fee was GHS 0.38 Tax was GHS -. Reference: ABENA OWUSU,233500000004,Waakye. Financial Transaction Id: 81780850399. External Transaction Id: 81780850399.Download the MoMo App for a Faster & Easier Experience Click here: https://mtnmymomo.onelink.me/XJOt/MoMo",
            Direction.DEBIT, "81780850399", counterparty = "ABENA OWUSU"),
        Case("MTN to AirtelTigo via gateway", "MobileMoney",
            "Your payment of GHS 30.00 to TIGO PUSH has been completed at 2026-05-15 19:14:43. Your new balance: GHS 1853.03. Fee was GHS 0.38 Tax was GHS -. Reference: KOJO ANTWI,233260000005,Indomie. Financial Transaction Id: 81366132620. External Transaction Id: 81366132620.Download the MoMo App for a Faster & Easier Experience Click here: https://mtnmymomo.onelink.me/XJOt/MoMo",
            Direction.DEBIT, "81366132620", counterparty = "KOJO ANTWI"),
        Case("TC paid to bank", "T-CASH",
            "0000013581370834 Confirmed. GHS15.23 paid to D086000 - ECOBANK PAY on 2026-07-02 at 22:40:48. Your new Telecel Cash balance is GHS17.32. Your E-levy charge is GHS0.00. Reference:  .",
            Direction.DEBIT, "0000013581370834"),
        Case("TC received from GhanaPay", "T-CASH",
            "0000013581347897 Confirmed. You have received GHS30.00 from GHANAPAY with transaction reference: Transfer From: 0551000001-Akua  on 2026-07-02 at 22:38:07. Your Telecel Cash balance is GHS32.55.",
            Direction.CREDIT, "0000013581347897"),
        Case("TC interest (no reference)", "T-CASH",
            "Dear customer, you have received GHS0.04 from Telecel Cash as interest earned on your mobile money wallet for the period January 2026 to March 2026. Your new balance is GHS6.01.",
            Direction.CREDIT, null),
        Case("TC bought airtime", "T-CASH",
            "0000007467907164 Confirmed. You bought GHS3.00 of airtime for 0500000002 on 2024-08-08 at 17:21:55. Your Telecel Cash balance is GHS0.52.",
            Direction.DEBIT, "0000007467907164"),
        Case("TC withdrawn from agent", "T-CASH",
            "0000007212968267 Confirmed. You have withdrawn GHS20.00 from G62667 - ASAKYREBRETUO FARMS on 2024-06-29 at 12:15:32. Your Telecel Cash balance is GHS54.19.",
            Direction.DEBIT, "0000007212968267"),
        // Worded as a pending request, but it reports a new balance, so the money
        // has already left. The only money-out marker is the purchase phrase;
        // "bought" doesn't appear.
        Case("TC bundle purchase", "T-CASH",
            "0000019876543210 confirmed. Your bundle purchase request of GHS5.00 on 2026-04-11 has been received at 14:32:19. Kindly wait for confirmation. Your new Telecel Cash balance is GHS12.34.",
            Direction.DEBIT, "0000019876543210"),
        // The reversal of that purchase. It mentions the purchase before the word
        // that reverses it, so reading the earliest marker would wrongly count a
        // refund as spending.
        Case("TC bundle purchase reversed", "T-CASH",
            "0000019876543211 confirmed. Your bundle purchase of GHS5.00 on 2026-04-11 has been reversed. Your new Telecel Cash balance is GHS17.34.",
            Direction.CREDIT, "0000019876543211"),
    )

    @TestFactory
    fun `every real transaction sample parses correctly`(): List<DynamicTest> =
        parsedCases.map { c ->
            DynamicTest.dynamicTest(c.name) {
                val r = parser.parse(IncomingSms(c.sender, c.body, now))
                val t = assertInstanceOf(ParseResult.Parsed::class.java, r).transaction
                assertEquals(c.direction, t.direction, "direction for ${c.name}")
                assertEquals(c.id, t.reference, "transaction id for ${c.name}")
                c.counterparty?.let {
                    assertEquals(it, t.counterparty, "counterparty for ${c.name}")
                }
            }
        }

    /**
     * Only agent withdrawals and deposits are offered to the cash ledger, never a
     * transfer or a payment.
     */
    private val cashMovements = setOf("MTN cash out", "TC withdrawn from agent", "MTN cash in")

    @TestFactory
    fun `only agent cash movements are flagged`(): List<DynamicTest> =
        parsedCases.map { c ->
            DynamicTest.dynamicTest(c.name) {
                val t = (parser.parse(IncomingSms(c.sender, c.body, now)) as ParseResult.Parsed).transaction
                assertEquals(c.name in cashMovements, t.cashMovement, "cash movement for ${c.name}")
            }
        }
}
