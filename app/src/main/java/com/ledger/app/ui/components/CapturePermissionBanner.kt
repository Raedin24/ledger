package com.ledger.app.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ledger.app.security.CapturePermission
import com.ledger.app.ui.theme.LedgerPalette

/**
 * Prompts for the RECEIVE_SMS runtime permission that automatic capture needs,
 * and renders nothing once it is granted.
 *
 * It re-checks on every ON_RESUME, so it disappears immediately after the user
 * grants access — whether from the system dialog or the app-settings page (the
 * fallback once the OS stops showing the dialog on repeated denials).
 */
@Composable
fun CapturePermissionBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(CapturePermission.isGranted(context)) }
    var requestedOnce by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = CapturePermission.isGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Capture notifications are opt-in on Android 13+; chain the request once the
    // user has just turned capture on, so alerts can actually be shown.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* best-effort; capture works regardless */ }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        granted = isGranted
        requestedOnce = true
        if (isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (granted) return

    // "Don't ask again": we've asked, it's still denied, and the OS will no
    // longer surface the rationale dialog — the only path left is app settings.
    val blockedPermanently = requestedOnce && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, CapturePermission.RECEIVE_SMS)

    LedgerCard(modifier) {
        Text(
            "Automatic capture is off",
            style = MaterialTheme.typography.titleMedium,
            color = LedgerPalette.Ink,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (blockedPermanently) {
                "Ledger needs SMS access to read transaction alerts. Enable it in " +
                    "Settings to start capturing."
            } else {
                "Ledger reads transaction alerts on-device to build your ledger. " +
                    "Nothing is stored raw and nothing leaves the phone."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = LedgerPalette.InkSoft,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (blockedPermanently) context.openAppSettings()
                else launcher.launch(CapturePermission.RECEIVE_SMS)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = LedgerPalette.Spend,
                contentColor = LedgerPalette.Surface,
            ),
        ) {
            Text(if (blockedPermanently) "Open Settings" else "Turn on capture")
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        },
    )
}
