package com.ledger.app.data.backup

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase encryption for the portable `.ledger` backup.
 *
 * A backup has to survive losing the phone, so it can't use the Android
 * Keystore, whose keys never leave the device. Instead the key is derived
 * from the user's passphrase with PBKDF2, and the data is encrypted with
 * AES-GCM. AES-GCM detects a wrong passphrase or any tampering and fails,
 * rather than returning garbage. Nothing here uses the network.
 *
 * The file is a few lines of text, so it survives plain text handling:
 *
 *     LEDGERBACKUP1
 *     <base64 salt>
 *     <base64 iv>
 *     <base64 ciphertext+tag>
 */
object BackupCrypto {

    private const val MAGIC = "LEDGERBACKUP1"
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    class WrongPassphraseException : Exception("Wrong passphrase, or the file is corrupt.")

    fun looksLikeBackup(text: String): Boolean = text.trimStart().startsWith(MAGIC)

    fun encrypt(plaintext: String, passphrase: CharArray): String {
        val salt = randomBytes(SALT_BYTES)
        val iv = randomBytes(IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return listOf(MAGIC, b64(salt), b64(iv), b64(ciphertext)).joinToString("\n")
    }

    fun decrypt(container: String, passphrase: CharArray): String {
        val lines = container.trim().lines()
        require(lines.size >= 4 && lines[0] == MAGIC) { "This isn't a Ledger backup file." }
        val salt = unb64(lines[1])
        val iv = unb64(lines[2])
        val ciphertext = unb64(lines[3])
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        return try {
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPassphraseException()
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }
    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s.trim(), Base64.NO_WRAP)
}
