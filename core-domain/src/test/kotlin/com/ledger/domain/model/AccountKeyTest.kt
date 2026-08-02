package com.ledger.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AccountKeyTest {

    @Test
    fun `every spelling of one Ghanaian number collapses to the same key`() {
        val expected = AccountKey.of("0244123456")
        assertEquals(expected, AccountKey.of("+233244123456"))
        assertEquals(expected, AccountKey.of("233 244 123 456"))
        assertEquals(expected, AccountKey.of("024-412-3456"))
        assertEquals(expected, AccountKey.of("MoMo 0244123456"))
    }

    @Test
    fun `merchant names and short numbers have no key`() {
        assertNull(AccountKey.of("MTN Airtime"))
        assertNull(AccountKey.of(null))
        assertNull(AccountKey.of(""))
        assertNull(AccountKey.of("GHS 5.00"))
    }

    @Test
    fun `distinct numbers do not collide`() {
        assertTrue(AccountKey.of("0244123456") != AccountKey.of("0209876543"))
    }

    @Test
    fun `own-account matching is format-insensitive`() {
        val own = setOfNotNull(AccountKey.of("0244123456"))
        assertTrue(AccountKey.isOwnAccount("+233244123456", own))
        assertFalse(AccountKey.isOwnAccount("0209876543", own))
        // A merchant can never be a self-transfer, however it's written.
        assertFalse(AccountKey.isOwnAccount("MTN Airtime", own))
    }

    @Test
    fun `no own accounts means nothing is a self-transfer`() {
        assertFalse(AccountKey.isOwnAccount("0244123456", emptySet()))
    }
}
