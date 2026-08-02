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
 * Supplies the SQLCipher passphrase for the Room database.
 *
 * Design (from the deep security review): "SQLCipher OR Keystore" is actually an
 * AND. SQLCipher needs a passphrase; where that passphrase lives is the real
 * boundary. Here:
 *
 *  1. A random 32-byte passphrase is generated once, on first launch.
 *  2. It is encrypted (AES-256-GCM) with a key that lives in the Android
 *     Keystore (StrongBox-backed when the device supports it) and NEVER leaves
 *     secure hardware.
 *  3. Only the ciphertext + IV are stored, in private SharedPreferences.
 *
 * The Keystore key is the root of trust, not the SQLCipher layer alone.
 *
 * Deliberate trade-off: this Keystore key is NOT `setUserAuthenticationRequired`.
 * The SMS BroadcastReceiver must be able to open the DB and persist a
 * transaction while the phone is locked / in the background — a biometric-gated
 * DB key would make background capture impossible. The biometric prompt is
 * therefore a separate UI-session lock (see security/BiometricGate), not the DB
 * key gate. This matches the stated threat model: protects data at rest, casual
 * snooping, other apps, and offline extraction — not a live root-compromised OS.
 */
class DatabaseKeyProvider(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Returns the raw SQLCipher passphrase bytes, creating it on first use. */
    fun passphrase(): ByteArray {
        val stored = prefs.getString(KEY_CIPHERTEXT, null)
        val storedIv = prefs.getString(KEY_IV, null)
        return if (stored != null && storedIv != null) {
            decrypt(Base64.decode(stored, Base64.NO_WRAP), Base64.decode(storedIv, Base64.NO_WRAP))
        } else {
            val fresh = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val (ct, iv) = encrypt(fresh)
            prefs.edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ct, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .apply()
            fresh
        }
    }

    private fun encrypt(plain: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        return cipher.doFinal(plain) to cipher.iv
    }

    private fun decrypt(cipherText: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(cipherText)
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
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
            // StrongBox is API 28+; calling the setter on 26/27 would crash before
            // the fallback catch below could run.
            .apply { if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setIsStrongBoxBacked(true) }
            .build()
        return try {
            gen.init(spec)
            gen.generateKey()
        } catch (_: Exception) {
            // Device without a StrongBox secure element — fall back to TEE-backed.
            if (strongBox) generateKey(strongBox = false) else throw IllegalStateException("Keystore unavailable")
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
