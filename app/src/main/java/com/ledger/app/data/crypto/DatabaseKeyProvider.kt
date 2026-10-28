package com.ledger.app.data.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Supplies the passphrase that encrypts the Room database (SQLCipher).
 *
 * The passphrase is only as safe as the place it is kept, so:
 *
 *  1. A random 32-byte passphrase is generated once, on first launch.
 *  2. It is encrypted with AES-256-GCM using a key held in the Android Keystore.
 *     That key never leaves the device's secure hardware (StrongBox when the
 *     device has it).
 *  3. Only the encrypted passphrase and its IV are stored, in private
 *     SharedPreferences.
 *
 * The Keystore key does not require the user to authenticate. The SMS receiver
 * has to save transactions while the phone is locked, which a key behind a
 * fingerprint prompt would prevent. The app lock is a separate screen lock
 * (see security/BiometricGate). This protects data at rest, from other apps and
 * from offline extraction. It does not protect against a rooted, compromised
 * phone while it is running.
 */
class DatabaseKeyProvider(context: Context) {

    private val databaseFile = context.getDatabasePath(com.ledger.app.data.db.LedgerDatabase.NAME)

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The stored passphrase exists but can no longer be decrypted, because the
     * Keystore key is gone or was invalidated (for example, preferences restored
     * without the hardware key, or a lock-screen change on some phones).
     *
     * This is thrown instead of creating a new key, because a new key can't open
     * the existing database.
     */
    class KeyUnavailableException(cause: Throwable?) : IllegalStateException(
        "The database key can no longer be unwrapped by this device's keystore. " +
            "The encrypted ledger cannot be opened; restore from a .ledger backup " +
            "after clearing the app's data.",
        cause,
    )

    /** Returns the SQLCipher passphrase, creating it on first use. */
    fun passphrase(): ByteArray {
        val stored = prefs.getString(KEY_CIPHERTEXT, null)
        val storedIv = prefs.getString(KEY_IV, null)
        return if (stored != null && storedIv != null) {
            try {
                decrypt(Base64.decode(stored, Base64.NO_WRAP), Base64.decode(storedIv, Base64.NO_WRAP))
            } catch (e: KeyUnavailableException) {
                throw e
            } catch (e: Exception) {
                throw KeyUnavailableException(e)
            }
        } else {
            // A database file with no stored passphrase means the passphrase was lost.
            // A new one couldn't open it, so report the loss instead.
            if (databaseFile.exists()) throw KeyUnavailableException(null)
            val fresh = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val (ct, iv) = encrypt(fresh)
            // commit, not apply: the database is created with this passphrase as soon
            // as it is returned, so it must be saved to disk first.
            val saved = prefs.edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ct, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .commit()
            if (!saved) throw KeyUnavailableException(IllegalStateException("could not save the database key"))
            fresh
        }
    }

    /**
     * Whether the database can be unlocked on this device. On a first run this
     * creates the passphrase, just as opening the database would.
     */
    fun canUnlock(): Boolean = try {
        passphrase().fill(0)
        true
    } catch (_: KeyUnavailableException) {
        false
    }

    private fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey(createIfMissing = true))
        return cipher.doFinal(plain) to cipher.iv
    }

    private fun decrypt(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(createIfMissing = false), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    /**
     * @param createIfMissing true only on first run, when there is no stored
     *  passphrase yet. Otherwise a missing key is an error, because a new key
     *  couldn't decrypt the stored passphrase.
     */
    private fun keystoreKey(createIfMissing: Boolean): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        if (!createIfMissing) throw KeyUnavailableException(null)
        return generateKey(strongBox = true)
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            // StrongBox needs API 28. Calling the setter on 26 or 27 would crash
            // before the fallback below could run.
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        return try {
            gen.init(spec)
            gen.generateKey()
        } catch (e: Exception) {
            // No StrongBox on this device, so fall back to the regular secure
            // hardware. Keep the cause if that fails too, so the error is useful.
            if (strongBox) generateKey(strongBox = false) else throw KeyUnavailableException(e)
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ledger_db_wrapping_key"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val PREFS = "ledger_secure_prefs"
        const val KEY_CIPHERTEXT = "db_pass_ct"
        const val KEY_IV = "db_pass_iv"
    }
}
