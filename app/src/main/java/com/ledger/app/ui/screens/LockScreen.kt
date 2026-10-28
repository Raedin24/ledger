package com.ledger.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.theme.LedgerThemeTokens
import kotlinx.coroutines.delay

/**
 * The app's PIN lock: dots and a number pad. It submits on its own once
 * enough digits are entered. With [showBiometric], a fingerprint key opens
 * the biometric prompt, and the prompt opens once automatically.
 *
 * @param lockoutRemainingMillis how long guessing is blocked, from
 *  [com.ledger.app.security.AppLockManager]. During a lockout the pad is
 *  disabled and shows how long is left, so it doesn't look broken. Biometrics
 *  stay available, so the owner isn't locked out by someone else's guesses.
 */
@Composable
fun LockScreen(
    pinLength: Int,
    showBiometric: Boolean,
    onVerifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit,
    lockoutRemainingMillis: () -> Long = { 0L },
    onBiometric: () -> Unit = {},
) {
    val colors = LedgerThemeTokens.colors
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var waitMillis by remember { mutableLongStateOf(lockoutRemainingMillis()) }
    val shake = remember { Animatable(0f) }

    // Open the biometric prompt once when it is the main method.
    LaunchedEffect(showBiometric) { if (showBiometric) onBiometric() }

    // Counts down while a lockout is running. Keyed on the boolean, so it only
    // restarts when a lockout starts or ends.
    LaunchedEffect(waitMillis > 0) {
        while (waitMillis > 0) {
            delay(500)
            waitMillis = lockoutRemainingMillis()
        }
    }

    fun submit(pin: String) {
        if (onVerifyPin(pin)) {
            onUnlocked()
        } else {
            error = true
            entered = ""
            waitMillis = lockoutRemainingMillis()
        }
    }

    LaunchedEffect(error) {
        if (error) {
            // Quick horizontal shake to signal a wrong PIN.
            shake.snapTo(0f)
            repeat(3) {
                shake.animateTo(12f, androidx.compose.animation.core.tween(50))
                shake.animateTo(-12f, androidx.compose.animation.core.tween(50))
            }
            shake.animateTo(0f, androidx.compose.animation.core.tween(50))
        }
    }

    fun press(digit: Int) {
        if (waitMillis > 0 || entered.length >= pinLength) return
        error = false
        entered += digit.toString()
        if (entered.length == pinLength) submit(entered)
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ledger", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                waitMillis > 0 -> "Too many attempts. Try again in ${waitLabel(waitMillis)}"
                error -> "Incorrect PIN. Try again."
                else -> "Enter your PIN to continue"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = if (error || waitMillis > 0) colors.spend else colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        // Dots.
        Row(
            Modifier.offset(x = shake.value.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(pinLength.coerceAtLeast(4)) { i ->
                val filled = i < entered.length
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) colors.spend else colors.surfaceSunken),
                )
            }
        }

        Spacer(Modifier.height(36.dp))

        // Keypad, dimmed and disabled during a lockout.
        val pad = waitMillis == 0L
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { rowDigits ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowDigits.forEach { d ->
                        KeypadKey(label = d.toString(), enabled = pad, onClick = { press(d) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Biometric key, or an empty space to keep the grid aligned.
                if (showBiometric) {
                    KeypadKey(icon = Icons.Default.Fingerprint, onClick = onBiometric)
                } else {
                    Spacer(Modifier.size(72.dp))
                }
                KeypadKey(label = "0", enabled = pad, onClick = { press(0) })
                KeypadKey(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    enabled = pad,
                    onClick = { if (entered.isNotEmpty()) { error = false; entered = entered.dropLast(1) } },
                )
            }
        }
    }
}

/** "45s" under a minute, whole minutes above it ("5 min"). */
private fun waitLabel(millis: Long): String {
    val seconds = (millis + 999) / 1000
    return if (seconds < 60) "${seconds}s" else "${(seconds + 59) / 60} min"
}

@Composable
private fun KeypadKey(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LedgerThemeTokens.colors
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colors.surfaceSunken)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (enabled) LedgerPalette.Ink else colors.inkMuted
        when {
            label != null -> Text(label, style = MaterialTheme.typography.headlineSmall, color = tint, fontWeight = FontWeight.SemiBold)
            icon != null -> Icon(icon, contentDescription = null, tint = if (enabled) colors.inkSoft else colors.inkMuted, modifier = Modifier.size(26.dp))
        }
    }
}
