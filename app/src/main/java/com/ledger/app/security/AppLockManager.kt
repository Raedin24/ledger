package com.ledger.app.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** How the lock behaves when the app opens, based on the user's settings. */
enum class LockMode {
    /** No lock. The app opens straight to the dashboard. */
    NONE,
    /** A PIN is required to open the app. */
    PIN,
    /** Biometric unlock, with the PIN as the fallback. */
    BIOMETRIC,
}

/**
 * Stores the app lock settings. This lock is a screen lock, separate from the
 * database key, which has to stay available so the SMS receiver can save
 * messages while the phone is locked (see
 * [com.ledger.app.data.crypto.DatabaseKeyProvider]).
 *
 * The lock is optional and off on a fresh install. Turning it on means setting
 * a PIN. Biometric unlock can then be added on top, with the PIN as fallback.
 *
 * The PIN itself is never stored. Only a PBKDF2-HMAC-SHA256 hash with a random
 * salt is kept, and it is compared in constant time.
 *
 * Wrong guesses are slowed down. After [FREE_ATTEMPTS] failures, each further
 * one adds a wait that doubles every time. The count is saved, so closing the
 * app doesn't reset it.
 */
@Singleton
class AppLockManager @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(): LockMode = when {
        !hasPin() -> LockMode.NONE
        prefs.getBoolean(KEY_BIOMETRIC, false) -> LockMode.BIOMETRIC
        else -> LockMode.PIN
    }

    fun hasPin(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun isBiometricEnabled(): Boolean = hasPin() && prefs.getBoolean(KEY_BIOMETRIC, false)

    /** How many digits the PIN has, for the lock screen's dots. 0 when no PIN is set. */
    fun pinLength(): Int = prefs.getInt(KEY_LEN, 0)

    /** Sets or changes the PIN (4 to 8 digits, checked by the caller). This turns the lock on. */
    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_LEN, pin.length)
            // Setting a PIN means the user is already inside the app, so earlier
            // failures no longer matter.
            .remove(KEY_FAILURES)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    /**
     * How long the PIN pad must stay disabled, in milliseconds, or 0 if a guess is
     * allowed.
     *
     * Based on the wall clock, so the wait survives the app being closed. Moving
     * the clock forward ends a wait early, but the failure count still grows, so
     * the next wrong guess brings a longer wait. A ceiling stops a clock moved
     * backwards from locking the user out for years.
     */
    fun lockoutRemainingMillis(): Long {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        val remaining = until - System.currentTimeMillis()
        return remaining.coerceIn(0L, MAX_LOCKOUT_MILLIS)
    }

    /** Always false during a lockout, without checking the hash at all. */
    fun verifyPin(pin: String): Boolean {
        if (lockoutRemainingMillis() > 0) return false
        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val expected = prefs.getString(KEY_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val correct = MessageDigest.isEqual(expected, derive(pin, salt))
        if (correct) clearFailures() else recordFailure()
        return correct
    }

    private fun recordFailure() {
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILURES, failures)
        if (failures >= FREE_ATTEMPTS) {
            val step = (failures - FREE_ATTEMPTS).coerceIn(0, MAX_BACKOFF_STEPS)
            val wait = (FIRST_LOCKOUT_MILLIS shl step).coerceAtMost(MAX_LOCKOUT_MILLIS)
            edit.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + wait)
        }
        edit.apply()
    }

    private fun clearFailures() {
        prefs.edit().remove(KEY_FAILURES).remove(KEY_LOCKED_UNTIL).apply()
    }

    /** Biometrics sit on top of the PIN, so they can only be turned on once a PIN exists. */
    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !hasPin()) return
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    /** Turns the lock off completely ([LockMode.NONE]). */
    fun disableLock() {
        prefs.edit()
            .remove(KEY_HASH).remove(KEY_SALT).remove(KEY_BIOMETRIC).remove(KEY_LEN)
            .remove(KEY_FAILURES).remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private companion object {
        const val PREFS = "ledger_lock_prefs"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
        const val KEY_LEN = "pin_len"
        const val KEY_BIOMETRIC = "biometric_enabled"
        const val KEY_FAILURES = "pin_failures"
        const val KEY_LOCKED_UNTIL = "pin_locked_until"
        const val SALT_BYTES = 16
        const val ITERATIONS = 120_000
        const val KEY_BITS = 256

        /** Wrong guesses allowed before the waits start, enough for ordinary mistakes. */
        const val FREE_ATTEMPTS = 5
        const val FIRST_LOCKOUT_MILLIS = 30_000L
        const val MAX_LOCKOUT_MILLIS = 30 * 60 * 1000L
        /** 30 seconds doubled six times is over half an hour, which the ceiling caps. */
        const val MAX_BACKOFF_STEPS = 6
    }
}
