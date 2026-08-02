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

/** How the app gate behaves on open. Derived from what the user has configured. */
enum class LockMode {
    /** No app lock — the app opens straight to the dashboard. */
    NONE,
    /** A PIN is required to open the app. */
    PIN,
    /** Biometric unlock, with the PIN as the fallback when it fails/unavailable. */
    BIOMETRIC,
}

/**
 * App-open lock preference store. This is a **UI-session gate**, deliberately
 * separate from the SQLCipher database key (which must stay available so the
 * background SMS receiver can write while the phone is locked — see
 * [com.ledger.app.data.crypto.DatabaseKeyProvider]).
 *
 * The lock is **opt-in**: a fresh install has [LockMode.NONE] and the user is
 * never forced to authenticate. Enabling it starts with setting a PIN; biometric
 * unlock is then an optional convenience layered on top of that PIN fallback.
 *
 * The PIN itself is never stored — only a PBKDF2-HMAC-SHA256 hash with a random
 * per-device salt, verified in constant time.
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

    /** The configured PIN's digit count — drives the lock screen's dot indicator
     *  and auto-submit. 0 when no PIN is set. */
    fun pinLength(): Int = prefs.getInt(KEY_LEN, 0)

    /** Sets or changes the app PIN (4–8 digits, validated by the caller). This
     *  is what turns the lock on. */
    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = derive(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_LEN, pin.length)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        val expected = prefs.getString(KEY_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return false
        return MessageDigest.isEqual(expected, derive(pin, salt))
    }

    /** Biometric is only a convenience layer over the PIN fallback, so it can
     *  only be enabled once a PIN exists. */
    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !hasPin()) return
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled).apply()
    }

    /** Removes all app-lock protection (back to [LockMode.NONE]). */
    fun disableLock() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_BIOMETRIC).remove(KEY_LEN).apply()
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
        const val SALT_BYTES = 16
        const val ITERATIONS = 120_000
        const val KEY_BITS = 256
    }
}
