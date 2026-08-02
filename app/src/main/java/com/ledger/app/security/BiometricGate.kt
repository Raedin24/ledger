package com.ledger.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric unlock for the app-open gate. This is a UI-session gate, deliberately
 * separate from the database key (which must stay available so the background SMS
 * receiver can write while the phone is locked — see DatabaseKeyProvider).
 *
 * Biometric here is an optional convenience over the app PIN (see
 * [AppLockManager]): the prompt offers a "Use PIN" negative action, and any error
 * falls back to the PIN screen rather than to the device credential.
 */
object BiometricGate {

    /** Whether the device has a biometric the user could enrol this app against. */
    fun canUseBiometric(context: Context): Boolean {
        val strongOrWeak = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        return BiometricManager.from(context).canAuthenticate(strongOrWeak) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Prompts for biometric unlock. [onUsePin] fires when the user chooses the
     * PIN fallback or biometric auth errors out, so the caller can show the PIN
     * pad instead.
     */
    fun promptBiometric(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onUsePin: () -> Unit = {},
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Negative button, lockout, cancellation → fall back to PIN.
                    onUsePin()
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Ledger")
            .setSubtitle("Use your fingerprint or face")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .build()
        prompt.authenticate(info)
    }
}
