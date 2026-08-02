package com.ledger.domain

import com.ledger.domain.categorization.CategorizationEngine
import com.ledger.domain.categorization.RecurringChargeDetector
import com.ledger.domain.categorization.Rule
import com.ledger.domain.dedup.DuplicateDetector
import com.ledger.domain.model.Direction
import com.ledger.domain.model.Institution
import com.ledger.domain.model.Money
import com.ledger.domain.model.Transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class DomainLogicTest {

    private val t0 = Instant.parse("2026-07-21T10:00:00Z")

    private fun txn(
        ref: String? = "R1",
        dir: Direction = Direction.DEBIT,
        amount: Long = 5000,
        cp: String? = "MELCOM",
        at: Instant = t0,
        bal: Long? = 100000,
    ) = Transaction(
        reference = ref, institution = Institution.MTN_MOMO, direction = dir,
        amount = Money(amount), balanceAfter = bal?.let { Money(it) }, fee = null,
        currency = "GHS", counterparty = cp, occurredAt = at, capturedAt = at,
    )

    // ---------- Money ----------

    @Test fun `money parses without float error`() {
        assertEquals(1250L, Money.parse("12.50")!!.minor)
        assertEquals(125000L, Money.parse("1,250.00")!!.minor)
        assertEquals(1000L, Money.parse("10")!!.minor)
    }

    // ---------- Duplicate detection ----------

    @Test fun `duplicate reference is detected`() {
        val d = DuplicateDetector()
        val a = txn(ref = "TXN999")
        val b = txn(ref = "txn999") // case-insensitive same reference
        assertTrue(d.isDuplicate(b, setOf(d.keyFor(a))))
    }

    @Test fun `missing reference falls back to composite key`() {
        val d = DuplicateDetector()
        val a = txn(ref = null)
        val b = txn(ref = null) // same institution/dir/amount/balance/time bucket
        val deduped = d.deduplicate(listOf(a, b))
        assertEquals(1, deduped.size)
    }

    @Test fun `different references are not duplicates`() {
        val d = DuplicateDetector()
        val a = txn(ref = "A")
        val b = txn(ref = "B")
        assertFalse(d.isDuplicate(b, setOf(d.keyFor(a))))
    }

    // ---------- Categorization ----------

    @Test fun `first-seen counterparty goes to review`() {
        val out = CategorizationEngine().categorize(txn(), emptyList(), t0.toEpochMilli())
        assertInstanceOf(CategorizationEngine.Outcome.Review::class.java, out)
    }

    @Test fun `matching rule categorizes and respects direction`() {
        val rule = Rule(1, "MELCOM", Direction.DEBIT, "Shopping", "Melcom")
        val out = CategorizationEngine().categorize(txn(dir = Direction.DEBIT), listOf(rule), t0.toEpochMilli())
        val c = assertInstanceOf(CategorizationEngine.Outcome.Categorized::class.java, out)
        assertEquals("Shopping", c.transaction.category)
        // Same counterparty, opposite direction, should NOT match this rule.
        val credit = CategorizationEngine().categorize(txn(dir = Direction.CREDIT), listOf(rule), t0.toEpochMilli())
        assertInstanceOf(CategorizationEngine.Outcome.Review::class.java, credit)
    }

    @Test fun `substring false-positive is avoided`() {
        // A bare "MOM" rule must not match "MOMOUSER" (the known gotcha).
        val rule = Rule(1, "MOM", Direction.DEBIT, "Family", "Mom", matchType = Rule.MatchType.CONTAINS)
        assertFalse(rule.matches("MOMOUSER", Direction.DEBIT))
        assertTrue(rule.matches("PAYMENT TO MOM", Direction.DEBIT))
    }

    @Test fun `stale rule triggers reconfirmation`() {
        val old = t0.minus(Duration.ofDays(200)).toEpochMilli()
        val rule = Rule(1, "MELCOM", Direction.DEBIT, "Shopping", "Melcom", lastMatchedAtMillis = old)
        val out = CategorizationEngine().categorize(txn(), listOf(rule), t0.toEpochMilli())
        val c = assertInstanceOf(CategorizationEngine.Outcome.Categorized::class.java, out)
        assertTrue(c.needsReconfirm)
    }

    // ---------- Recurrence ----------

    @Test fun `regular same-amount charges are flagged as recurring`() {
        val history = (0..3).map { i ->
            txn(ref = "R$i", amount = 3000, cp = "NETFLIX", at = t0.plus(Duration.ofDays(30L * i)))
        }
        val candidates = RecurringChargeDetector().detect(history)
        assertEquals(1, candidates.size)
        assertEquals("NETFLIX", candidates.first().counterparty)
    }
}
