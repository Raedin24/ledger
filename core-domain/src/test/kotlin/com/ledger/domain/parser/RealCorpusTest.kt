package com.ledger.domain.parser

import com.ledger.domain.model.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.time.Instant

/**
 * Regression corpus of real captured SMS shapes. Every entry here was observed
 * on-device; keep this list append-only as new formats appear so the parser can
 * never silently regress on a known message.
 *
 * Names, MSISDNs and account numbers are replaced with invented stand-ins — the
 * parser only cares about shape (field order, punctuation, digit counts), and a
 * test corpus is no place for real people's numbers. Keep substitutions the same
 * length and format as the originals or the cases stop testing what they claim.
 */
class RealCorpusTest {

    private val parser = SmsParser()
    private val now = Instant.parse("2026-07-21T10:00:00Z")

    private data class Case(
        val name: String, val sender: String, val body: String,
        val direction: Direction?, val id: String?,
        /** Asserted only when set, so older cases stay as they were written. */
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
        // A refund is money coming back, however much "refunded ... to your
        // account" reads like an outflow.
        Case("MTN refund", "MobileMoney",
            "Hello, MTN BUNDLE  (CISNG.sp) has successfully refunded GHS 3.00 to your mobile money account at 2026-07-11 23:09:24. Message from refunder: . Your new balance:295.83 GHS. Financial Transaction Id: 85211519729. External Transaction Id: -. TRANSACTION FEE IS 0",
            Direction.CREDIT, "85211519729", counterparty = "MTN BUNDLE"),
        // Cross-network sends are addressed to a gateway; the person is in the
        // narration. Without the override every one of these collapses onto
        // "TELECEL PUSH" / "TIGO PUSH".
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
}
