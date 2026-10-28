package com.ledger.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the three ways a provider can describe the same transfer between two
 * wallets the user owns.
 */
class OwnAccountMatcherTest {

    private val myMomo = AccountKey.of("0244123456")!!
    private val myGhanaPay = AccountKey.of("0209876543")!!

    private fun matcher(
        keys: Set<String> = setOf(myMomo, myGhanaPay),
        names: Set<String> = setOf("ama owusu"),
    ) = OwnAccountMatcher(keys, names)

    // ---- Check 1: the counterparty is the number (GhanaPay) ----

    @Test
    fun `a number in the counterparty field is recognised in any spelling`() {
        assertTrue(matcher().isOwn("+233244123456", null))
        assertTrue(matcher().isOwn("024-412-3456", null))
    }

    @Test
    fun `someone else's number is not a self-transfer`() {
        assertFalse(matcher().isOwn("0271112222", null))
    }

    // ---- Check 2: the counterparty is a name (MTN) ----

    @Test
    fun `MTN's received-from name is recognised as the user's own account`() {
        // Money from the user's own wallet must not count as income.
        assertTrue(matcher().isOwn("AMA OWUSU", null))
        assertTrue(matcher().isOwn("Ama Owusu", null))
        assertTrue(matcher().isOwn("  ama   owusu  ", null))
    }

    @Test
    fun `a name match does not depend on which provider sent the alert`() {
        // A name saved against the GhanaPay account is exactly what appears in
        // an MTN alert, so matching must not be limited by provider.
        val onlyNames = OwnAccountMatcher(emptySet(), setOf("ama owusu"))
        assertTrue(onlyNames.isOwn("AMA OWUSU", null))
    }

    @Test
    fun `an unrelated name is not a self-transfer`() {
        assertFalse(matcher().isOwn("KWESI BOATENG", null))
        assertFalse(matcher().isOwn("MTN Airtime", null))
    }

    // ---- Check 3: the number is in the narration (cross-network) ----

    @Test
    fun `Telecel's transfer-from narration carries the number`() {
        assertTrue(
            matcher().isOwn(
                counterparty = "MTN MOBILE MONEY",
                referenceHint = "Transfer from: 0244123456-AMA OWUSU Ref: 8891",
            ),
        )
    }

    @Test
    fun `MTN's trailing name-number-reference triple carries the number`() {
        assertTrue(
            matcher().isOwn(
                counterparty = "KWESI B",
                referenceHint = "AMA OWUSU, 0209876543, rent share",
            ),
        )
    }

    @Test
    fun `a stranger's number in the narration is not a self-transfer`() {
        assertFalse(
            matcher().isOwn("MTN MOBILE MONEY", "Transfer from: 0271112222-ADWOA Ref: 12"),
        )
    }

    // ---- Guards ----

    @Test
    fun `nothing registered means nothing is ever a self-transfer`() {
        val empty = OwnAccountMatcher(emptySet(), emptySet())
        assertFalse(empty.isOwn("0244123456", "Transfer from: 0244123456-AMA"))
        assertFalse(empty.isOwn("AMA OWUSU", null))
    }

    @Test
    fun `an amount next to a number never fuses into a bogus key`() {
        // "GHS 2.38 0244..." must not produce a key from the end of the amount,
        // which could match an account the user never added.
        val keys = AccountKey.allIn("Payment of GHS 2.38 completed")
        assertTrue(keys.isEmpty(), "amounts must not look like account numbers: $keys")
    }

    @Test
    fun `narration scanning finds every distinct number once`() {
        val keys = AccountKey.allIn("from 0244123456 to 0209876543 and again 0244123456")
        assertEquals(listOf(myMomo, myGhanaPay), keys)
    }

    @Test
    fun `name normalisation rejects fragments too short to match on`() {
        assertNull(OwnAccountMatcher.normaliseName("K"))
        assertNull(OwnAccountMatcher.normaliseName("  "))
        assertNull(OwnAccountMatcher.normaliseName(null))
        assertEquals("ama", OwnAccountMatcher.normaliseName(" AMA "))
    }
}
