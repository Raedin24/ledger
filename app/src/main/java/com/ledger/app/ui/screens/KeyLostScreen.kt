package com.ledger.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.LedgerPalette

/**
 * Shown instead of the app when the database key can't be recovered (see
 * [com.ledger.app.data.crypto.DatabaseKeyProvider.KeyUnavailableException]).
 *
 * The data is encrypted with a key that only this phone's secure hardware
 * held, so the app can't read it. Instead of crashing on every launch, it
 * explains this and shows the way forward.
 */
@Composable
fun KeyLostScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().background(LedgerPalette.Background).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ledger can't open your data", style = MaterialTheme.typography.headlineMedium, color = LedgerPalette.Ink)
        Spacer(Modifier.height(12.dp))
        Text(
            "The key that unlocks it on this phone is gone, usually after the phone's secure storage was reset. " +
                "The data can't be read without it.",
            style = MaterialTheme.typography.bodyLarge,
            color = LedgerPalette.InkSoft,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "To start again, clear Ledger's storage in Android settings, then restore a backup if you have one.",
            style = MaterialTheme.typography.bodyLarge,
            color = LedgerPalette.InkSoft,
        )
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth()
                .background(LedgerPalette.Spend, RoundedCornerShape(14.dp))
                .clickable {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Open app settings", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
