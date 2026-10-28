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
        uri?.let { vm.importFrom(it, passphrase = null) }
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.onSmsPermissionResult(granted)
    }

    // The guide's import step points at the import row, which is near the end
    // of a scrolling list, so scroll it into view.
    val step = TutorialStep.IMPORT_PAST
    val showImportStep = tutorial.showing(step)
    val listState = rememberLazyListState()
    LaunchedEffect(showImportStep) {
        if (!showImportStep) return@LaunchedEffect
        // The step is often already current on the first composition, before the
        // list has been laid out. Wait for items rather than skipping the scroll.
        val count = snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        // Second from the end, because the spacer is last. Worked out rather than
        // fixed, so adding a row above doesn't break it.
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
            InfoNote("Your data stays on this device unless you export it.")
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
                "Restore from backup", "Merge a .ledger file into this device",
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
            // The import runs in the background and reports through the status
            // banner. The row shows a spinner, and the rest of the app stays usable.
            DataAction(
                title = "Import past transactions",
                subtitle = if (importing) "Scanning your inbox…"
                else "Read the alerts already in your inbox",
                badge = "SMS",
                busy = importing,
                spotlit = showImportStep,
            ) {
                if (importing) return@DataAction
                if (CapturePermission.canReadInbox(context)) vm.importSms()
                else smsPermission.launch(CapturePermission.READ_SMS)
            }
        }

        // Room for the guide's card without covering the row it points at.
        item { Spacer(Modifier.height(if (showImportStep) 250.dp else 90.dp)) }
    }

    if (askBackupPassphrase) {
        PassphraseDialog(
            title = "Encrypt backup",
            message = "Choose a passphrase. You'll need it to restore. It can't be recovered.",
            confirmLabel = "Choose file",
            choosing = true,
            onConfirm = { pass ->
                pendingBackupPassphrase = pass
                askBackupPassphrase = false
                backupCreate.launch("ledger-backup-$tag.ledger")
            },
            onDismiss = { askBackupPassphrase = false },
        )
    }

    pendingRestoreUri?.let { uri ->
        // A restore adds the backup's transactions to what is already here, and
        // the dialog says so. Nothing on the device is removed, and a transaction
        // in both is kept once.
        PassphraseDialog(
            title = "Restore from backup",
            message = "Enter the backup's passphrase. Nothing on this device is deleted.",
            confirmLabel = "Restore",
            onConfirm = { pass ->
                vm.importFrom(uri, passphrase = pass, restoring = true)
                pendingRestoreUri = null
            },
            onDismiss = { pendingRestoreUri = null },
        )
    }

    // The import step offers to start an import, and completes when one
    // finishes. It watches a counter of finished imports.
    LaunchedEffect(importsFinished) {
        if (importsFinished > 0 && tutorial.showing(step)) tutorialVm.complete(step)
    }
    Box(Modifier.fillMaxSize()) {
        CoachScrim(showImportStep)
        CoachMarkHost(
            visible = showImportStep,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            CoachMark(
                label = coachLabel(step, if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL),
                title = step.title,
                // The import runs in the background, so the guide doesn't keep the user
                // here. Once it starts, the main button moves the guide on.
                body = if (importing)
                    "This runs in the background."
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

/**
 * @param choosing true when setting a new passphrase for a backup. It must be
 *   at least [MIN_PASSPHRASE] characters and typed twice: a typo would make the
 *   backup impossible to open, and a very short passphrase is easy to guess.
 *   False when entering an existing passphrase to restore.
 */
@Composable
private fun PassphraseDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    choosing: Boolean = false,
) {
    var passphrase by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val problem = when {
        !choosing -> null
        passphrase.length < MIN_PASSPHRASE -> "At least $MIN_PASSPHRASE characters."
        repeat.isNotEmpty() && repeat != passphrase -> "The two don't match."
        else -> null
    }
    val ready = if (choosing) passphrase.length >= MIN_PASSPHRASE && repeat == passphrase else passphrase.isNotEmpty()
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
                    colors = passphraseColors(),
                )
                if (choosing) {
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        placeholder = { Text("Type it again") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = passphraseColors(),
                    )
                    // Only show the problem once something is typed.
                    if (passphrase.isNotEmpty() && problem != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(problem, style = MaterialTheme.typography.labelSmall, color = LedgerPalette.Spend)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (ready) onConfirm(passphrase) }, enabled = ready) {
                Text(
                    confirmLabel,
                    color = if (ready) LedgerPalette.Gold else LedgerPalette.InkMuted,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LedgerPalette.InkMuted) } },
    )
}

/** The shortest passphrase a new backup accepts. */
private const val MIN_PASSPHRASE = 4

@Composable
private fun passphraseColors() = TextFieldDefaults.colors(
    focusedContainerColor = LedgerPalette.SurfaceAlt,
    unfocusedContainerColor = LedgerPalette.SurfaceAlt,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

/** Matches [LedgerCard]'s corner radius, so the guide's highlight follows the row. */
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
 * One row in the data list. The badge is a short label (CSV, JSON, SMS) for
 * file formats, or a line icon for actions. No emoji, which would clash with
 * the app's muted colours.
 *
 * [busy] replaces the arrow with a spinner, so a long action shows progress in
 * place. [spotlit] highlights the row for the setup guide.
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
