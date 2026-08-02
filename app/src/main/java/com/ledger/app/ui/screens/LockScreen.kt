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
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * App-open PIN lock. Shows a dot indicator + numeric keypad; auto-submits once
 * the entered digits reach the stored PIN length. When [showBiometric] is set,
 * a fingerprint key re-triggers the biometric prompt, and the prompt is fired
 * once automatically on entry.
 */
@Composable
fun LockScreen(
    pinLength: Int,
    showBiometric: Boolean,
    onVerifyPin: (String) -> Boolean,
    onUnlocked: () -> Unit,
    onBiometric: () -> Unit = {},
) {
    val colors = LedgerThemeTokens.colors
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }

    // Auto-launch biometric once when it's the primary method.
    LaunchedEffect(showBiometric) { if (showBiometric) onBiometric() }

    fun submit(pin: String) {
        if (onVerifyPin(pin)) {
            onUnlocked()
        } else {
            error = true
            entered = ""
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
        if (entered.length >= pinLength) return
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
            if (error) "Incorrect PIN — try again" else "Enter your PIN to continue",
            style = MaterialTheme.typography.bodyLarge,
            color = if (error) colors.spend else colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        // Dot indicator.
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

        // Keypad.
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { rowDigits ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowDigits.forEach { d -> KeypadKey(label = d.toString(), onClick = { press(d) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Biometric (or empty spacer to keep the grid aligned).
                if (showBiometric) {
                    KeypadKey(icon = Icons.Default.Fingerprint, onClick = onBiometric)
                } else {
                    Spacer(Modifier.size(72.dp))
                }
                KeypadKey(label = "0", onClick = { press(0) })
                KeypadKey(
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    onClick = { if (entered.isNotEmpty()) { error = false; entered = entered.dropLast(1) } },
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = LedgerThemeTokens.colors
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(colors.surfaceSunken)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            label != null -> Text(label, style = MaterialTheme.typography.headlineSmall, color = LedgerPalette.Ink, fontWeight = FontWeight.SemiBold)
            icon != null -> Icon(icon, contentDescription = null, tint = colors.inkSoft, modifier = Modifier.size(26.dp))
        }
    }
}
