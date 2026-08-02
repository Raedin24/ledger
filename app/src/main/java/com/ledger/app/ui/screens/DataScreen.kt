package com.ledger.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.security.CapturePermission
import com.ledger.app.tutorial.TutorialStep
import com.ledger.app.ui.components.CoachBeak
import com.ledger.app.ui.components.CoachMark
import com.ledger.app.ui.components.CoachMarkHost
import com.ledger.app.ui.components.CoachScrim
import com.ledger.app.ui.components.CoachTone
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.coachLabel
import com.ledger.app.ui.components.spotlight
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.vm.DataViewModel
import com.ledger.app.ui.vm.TutorialViewModel
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@Composable
fun DataScreen(
    onBack: () -> Unit,
    vm: DataViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    val status by vm.status.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val importsFinished by vm.importsFinished.collectAsStateWithLifecycle()
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    val tag = remember { LocalDate.now().toString() }
    val context = LocalContext.current

    // Backup asks for a passphrase first, then picks a destination.
    var askBackupPassphrase by remember { mutableStateOf(false) }
    var pendingBackupPassphrase by remember { mutableStateOf("") }
    // Restore picks a file first, then asks for its passphrase.
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    val csvExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }
    val jsonExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::exportJson)
    }
    val backupCreate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null && pendingBackupPassphrase.isNotEmpty()) vm.backup(uri, pendingBackupPassphrase)
        pendingBackupPassphrase = ""
    }
    val restoreOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestoreUri = uri
    }
    val importOpen = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importFrom(it, passphrase = null, replace = false) }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onSmsPermissionResult(granted)
    }

    // Beat 3 of the setup guide lives on this screen and points at the import row,
    // which is the *last* thing in a scrolling list — so the guide has to bring it
    // into view, or the mark ends up describing something off-screen (or sitting
    // directly on top of it).
    val step = TutorialStep.IMPORT_PAST
    val showBeat3 = tutorial.showing(step)
    val listState = rememberLazyListState()
    LaunchedEffect(showBeat3) {
        if (!showBeat3) return@LaunchedEffect
        // The guide usually arrives with this beat already current, so the first
        // composition runs before layout and the item count is still zero — wait
        // for it rather than silently skipping the scroll.
        val count = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        // Second from the end: the trailing spacer is last. Derived rather than
        // hard-coded so adding a data action above doesn't silently mis-aim this.
        listState.animateScrollToItem((count - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            ScreenHeader("Export & backup", onBack)
        }

        status?.let { message ->
            item { StatusBanner(message, onDismiss = vm::clearStatus) }
        }

        item {
            InfoNote("Your data never leaves this device unless you export it here. There is no cloud sync — by design.")
        }

        item { Caption("Export data") }
        item {
            DataAction("Export as CSV", "Open in Sheets or Excel", badge = "CSV") { csvExport.launch("ledger-$tag.csv") }
        }
        item {
            DataAction("Export as JSON", "Full data with rules & notes", badge = "JSON") { jsonExport.launch("ledger-$tag.json") }
        }

        item { Caption("Backup & restore") }
        item {
            DataAction(
                "Create backup", "Passphrase-encrypted .ledger file",
                icon = Icons.Outlined.Lock,
            ) { askBackupPassphrase = true }
        }
        item {
            DataAction(
                "Restore from backup", "Replace all data on this device",
                icon = Icons.Outlined.Restore,
            ) { restoreOpen.launch(arrayOf("*/*")) }
        }
        item {
            DataAction(
                "Import from file", "Merge a JSON export into this device",
                icon = Icons.Outlined.FileDownload,
            ) { importOpen.launch(arrayOf("*/*")) }
        }

        item { Caption("Import past messages") }
        item {
            // The scan runs in the background and reports through the status
            // banner. The row itself carries the spinner, so the rest of the app
            // stays usable while a long backfill finishes.
            DataAction(
                title = "Import past transactions",
                subtitle = if (importing) "Scanning your inbox…"
                else "Read existing alerts from trusted senders",
                badge = "SMS",
                busy = importing,
                spotlit = showBeat3,
            ) {
                if (importing) return@DataAction
                if (CapturePermission.canReadInbox(context)) vm.importSms()
                else smsPermission.launch(CapturePermission.READ_SMS)
            }
        }

        // Room for the coach-mark to sit without covering the row it describes.
        item { Spacer(Modifier.height(if (showBeat3) 250.dp else 90.dp)) }
    }

    if (askBackupPassphrase) {
        PassphraseDialog(
            title = "Encrypt backup",
            message = "Choose a passphrase. You'll need it to restore — it can't be recovered.",
            confirmLabel = "Choose file",
            onConfirm = { pass ->
                pendingBackupPassphrase = pass
                askBackupPassphrase = false
                backupCreate.launch("ledger-backup-$tag.ledger")
            },
            onDismiss = { askBackupPassphrase = false },
        )
    }

    pendingRestoreUri?.let { uri ->
        PassphraseDialog(
            title = "Restore from backup?",
            message = "This replaces everything currently on this device — it can't be undone. Enter the backup's passphrase.",
            confirmLabel = "Restore",
            destructive = true,
            onConfirm = { pass ->
                vm.importFrom(uri, passphrase = pass, replace = true)
                pendingRestoreUri = null
            },
            onDismiss = { pendingRestoreUri = null },
        )
    }

    // Beat 3 offers to start the import, and completes itself once a scan
    // finishes — learned from a counter rather than by owning a modal. Gating it
    // on "running" alone stalled the guide for anyone who never started an import.
    LaunchedEffect(importsFinished) {
        if (importsFinished > 0 && tutorial.showing(step)) tutorialVm.complete(step)
    }
    Box(Modifier.fillMaxSize()) {
        CoachScrim(showBeat3)
        CoachMarkHost(
            visible = showBeat3,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            CoachMark(
                label = coachLabel(step, if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL),
                title = step.title,
                // The scan runs in the background, so the guide must not hold the
                // user here until it lands. Once it's going, the primary becomes
                // the way onward rather than disappearing.
                body = if (importing)
                    "It's reading your inbox in the background — you don't have to wait here."
                else step.body,
                primaryLabel = if (importing) "Next step" else step.primary,
                onPrimary = {
                    if (importing) {
                        tutorialVm.complete(step)
                    } else if (CapturePermission.canReadInbox(context)) {
                        vm.importSms()
                    } else {
                        smsPermission.launch(CapturePermission.READ_SMS)
                    }
                },
                secondaryLabel = if (importing) null else step.secondary,
                onSecondary = { tutorialVm.skip(step) },
                onDismiss = tutorialVm::dismiss,
                tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL,
                beak = CoachBeak.Top(0.5f),
                step = step.number,
            )
        }
    }
}

@Composable
private fun PassphraseDialog(
    title: String,
    message: String,
    confirmLabel: String,
    destructive: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LedgerPalette.Surface,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkSoft)
                Spacer(Modifier.height(12.dp))
                TextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    placeholder = { Text("Passphrase") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = LedgerPalette.SurfaceAlt,
                        unfocusedContainerColor = LedgerPalette.SurfaceAlt,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (passphrase.isNotEmpty()) onConfirm(passphrase) }) {
                Text(confirmLabel, color = if (destructive) LedgerPalette.Danger else LedgerPalette.Gold, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LedgerPalette.InkMuted) } },
    )
}

/** Matches [LedgerCard]'s radius so the guide's halo traces the row, not a box
 *  around it. */
private val DataActionShape = RoundedCornerShape(16.dp)
private val NoteShape = RoundedCornerShape(14.dp)

@Composable
private fun Caption(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = LedgerPalette.InkMuted,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
}

@Composable
private fun InfoNote(text: String) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFEEF1E6), NoteShape)
            .border(1.dp, LedgerPalette.Income.copy(alpha = 0.22f), NoteShape)
            .padding(14.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.IncomeDeep)
    }
}

@Composable
private fun StatusBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(LedgerPalette.SurfaceSunken)
            .clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.Ink, modifier = Modifier.weight(1f))
        Text("✕", style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.InkMuted)
    }
}

/**
 * One row in the data list. The badge is either a short lettermark (CSV, JSON,
 * SMS) or a line icon — the file formats read as text, the actions as glyphs.
 * Deliberately not emoji: those render in their own colour and weight and sit
 * badly against the app's muted ink-on-paper palette.
 *
 * [busy] swaps the chevron for a spinner, which is how a long-running action
 * shows progress in place instead of behind a modal. [spotlit] lifts the row out
 * of the setup guide's scrim, so what the user is told to tap is what they tap.
 */
@Composable
private fun DataAction(
    title: String,
    subtitle: String,
    badge: String? = null,
    icon: ImageVector? = null,
    busy: Boolean = false,
    spotlit: Boolean = false,
    onClick: () -> Unit,
) {
    LedgerCard(
        modifier = Modifier.spotlight(spotlit, DataActionShape).clickable(onClick = onClick),
        padding = PaddingValues(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(LedgerPalette.SurfaceAlt, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    icon != null -> Icon(
                        icon,
                        contentDescription = null,
                        tint = LedgerPalette.InkSoft,
                        modifier = Modifier.size(20.dp),
                    )
                    badge != null -> Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = LedgerPalette.InkSoft,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = LedgerPalette.Ink)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = LedgerPalette.InkMuted)
            }
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LedgerPalette.Gold,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerPalette.InkMuted)
            }
        }
    }
}
