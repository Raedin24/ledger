package com.ledger.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.tutorial.TutorialStep
import com.ledger.domain.model.Institution
import com.ledger.app.ui.components.CoachBeak
import com.ledger.app.ui.components.CoachMark
import com.ledger.app.ui.components.CoachMarkHost
import com.ledger.app.ui.components.CoachScrim
import com.ledger.app.ui.components.CoachTone
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.coachLabel
import com.ledger.app.ui.components.spotlight
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.theme.LedgerThemeTokens
import com.ledger.app.ui.vm.OwnAccountsViewModel
import com.ledger.app.ui.vm.SecurityViewModel
import com.ledger.app.ui.vm.SettingsViewModel
import com.ledger.app.ui.vm.TutorialViewModel

/**
 * Positions of the two cards the setup guide anchors to, so it can scroll them
 * into view. The list below is a fixed sequence of `item {}` blocks — header,
 * senders, my accounts, sorting, security, privacy, data, getting started — so
 * these are stable; keep them in step if a card is inserted.
 */
private const val ACCOUNTS_CARD = 2
private const val SECURITY_CARD = 4

@Composable
fun SettingsScreen(
    onAddSender: () -> Unit,
    onManageRules: () -> Unit,
    onManageCategories: () -> Unit,
    onOpenData: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    securityVm: SecurityViewModel = hiltViewModel(),
    ownAccountsVm: OwnAccountsViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    val senders by vm.senders.collectAsStateWithLifecycle(initialValue = emptyList())
    val security by securityVm.state.collectAsStateWithLifecycle()
    val ownAccounts by ownAccountsVm.accounts.collectAsStateWithLifecycle()
    val ownAccountError by ownAccountsVm.error.collectAsStateWithLifecycle()
    val colors = LedgerThemeTokens.colors
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    var pinFlow by remember { mutableStateOf(PinFlow.NONE) }
    var addingOwnAccount by remember { mutableStateOf(false) }

    // Two beats live on this screen: "mark your own accounts" (5) and the
    // optional lock epilogue (7).
    val accountsStep = TutorialStep.OWN_ACCOUNTS
    val lockStep = TutorialStep.APP_LOCK
    val showAccountsBeat = tutorial.showing(accountsStep) && !addingOwnAccount
    val showLockBeat = tutorial.showing(lockStep) && pinFlow == PinFlow.NONE
    val tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL

    // Settings is longer than a screen, and both beats anchor to cards below the
    // fold — Security is the fifth card down. Without this the guide arrived here
    // pointing at whatever happened to be on screen (Sorting, usually) instead of
    // the control it was describing.
    val listState = rememberLazyListState()
    LaunchedEffect(showAccountsBeat, showLockBeat) {
        when {
            showAccountsBeat -> listState.animateScrollToItem(ACCOUNTS_CARD)
            showLockBeat -> listState.animateScrollToItem(SECURITY_CARD)
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Text("Settings", style = MaterialTheme.typography.displayMedium)
        }

        // Trusted senders
        item {
            LedgerCard {
                Text("Trusted senders", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Only messages from these providers are ever parsed.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted,
                )
                Spacer(Modifier.height(8.dp))
                if (senders.isEmpty()) {
                    Text("MTN MoMo · GhanaPay · Telecel Cash", style = MaterialTheme.typography.bodyLarge)
                } else {
                    senders.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.displayName, style = MaterialTheme.typography.bodyLarge)
                            Text(if (s.liveCaptureEnabled) "Live" else "Paused", color = if (s.liveCaptureEnabled) colors.income else colors.inkMuted, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Add a sender", style = MaterialTheme.typography.titleMedium, color = colors.gold, modifier = Modifier.clickable(onClick = onAddSender))
            }
        }

        // My accounts — powers self-transfer detection
        item {
            LedgerCard {
                Text("My accounts", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Your own mobile-money wallets. Money you move between them " +
                        "isn't counted as spending — only the fee is. Anything sent " +
                        "elsewhere, or withdrawn, still counts. One number can hold " +
                        "more than one wallet; add each separately.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted,
                )
                Spacer(Modifier.height(10.dp))
                if (ownAccounts.isEmpty()) {
                    Text(
                        "None added yet — every transfer is treated as spending.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft,
                    )
                } else {
                    ownAccounts.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.account.label, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    listOfNotNull(row.account.identifier, row.senderLabel)
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall, color = colors.inkMuted,
                                )
                                // Spelling out the aliases is what makes a missed
                                // self-transfer diagnosable: if the name in the
                                // alert isn't listed here, that's the reason.
                                if (row.names.isNotEmpty()) {
                                    Text(
                                        "Known as ${row.names.joinToString(", ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.inkMuted,
                                    )
                                }
                            }
                            Text(
                                "Remove",
                                style = MaterialTheme.typography.labelLarge,
                                color = LedgerPalette.Danger,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { ownAccountsVm.remove(row.account) },
                            )
                        }
                    }
                }
                ownAccountError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = colors.spend)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add an account",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.gold,
                    modifier = Modifier
                        .spotlight(showAccountsBeat, RoundedCornerShape(10.dp))
                        .clickable { ownAccountsVm.clearError(); addingOwnAccount = true }
                        .padding(if (showAccountsBeat) 6.dp else 0.dp),
                )
            }
        }

        // Sorting — rules & categories
        item {
            LedgerCard {
                Text("Sorting", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                ActionLine("Rules", "How senders auto-categorise", onManageRules)
                ActionLine("Categories", "Add, rename, reorder, or hide", onManageCategories)
            }
        }

        // Security — opt-in app lock (PIN first, biometric as a convenience on top)
        item {
            LedgerCard {
                Text("Security", style = MaterialTheme.typography.titleLarge)

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ask every time the app opens", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (security.hasPin) "A PIN is required to open Ledger"
                            else "Off — Ledger opens straight to your dashboard",
                            style = MaterialTheme.typography.labelSmall, color = colors.inkMuted,
                        )
                    }
                    Switch(
                        checked = security.hasPin,
                        onCheckedChange = { on -> if (on) pinFlow = PinFlow.SET else pinFlow = PinFlow.CONFIRM_OFF },
                        modifier = Modifier.spotlight(showLockBeat, RoundedCornerShape(16.dp)),
                    )
                }

                if (security.hasPin) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Unlock with biometrics", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (security.biometricAvailable) "Fingerprint or face, with your PIN as backup"
                                else "No biometrics enrolled on this device",
                                style = MaterialTheme.typography.labelSmall, color = colors.inkMuted,
                            )
                        }
                        Switch(
                            checked = security.biometricEnabled,
                            enabled = security.biometricAvailable,
                            onCheckedChange = { securityVm.setBiometricEnabled(it) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Change PIN",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.gold,
                        modifier = Modifier.clickable { pinFlow = PinFlow.CHANGE },
                    )
                }
            }
        }

        // Privacy
        item {
            LedgerCard {
                Text("Privacy", style = MaterialTheme.typography.titleLarge)
                PrivacyLine("Raw SMS is never stored — only parsed fields.")
                PrivacyLine("No internet permission. Nothing leaves this device.")
                PrivacyLine("Database encrypted with a hardware-backed key.")
            }
        }

        // Data — export, backup, import
        item {
            LedgerCard {
                Text("Data", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                ActionLine("Export & backup", "CSV, JSON, encrypted backup & restore", onOpenData)
            }
        }

        // The guide is always retrievable — dismissing it is never final.
        item {
            LedgerCard {
                Text("Getting started", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                ActionLine(
                    "Replay setup guide",
                    if (tutorial.done.containsAll(TutorialStep.entries.toList())) "Walk through it again from the start"
                    else "Pick up where you left off",
                    tutorialVm::replay,
                )
            }
        }
        item { Spacer(Modifier.height(if (showAccountsBeat || showLockBeat) 200.dp else 90.dp)) }
    }

        // Beats 5 and 7 share this screen but never overlap — the manager only
        // ever nominates one current beat.
        CoachScrim(showAccountsBeat || showLockBeat)
        CoachMarkHost(
            visible = showAccountsBeat,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            CoachMark(
                label = coachLabel(accountsStep, tone),
                title = accountsStep.title,
                body = accountsStep.body,
                primaryLabel = accountsStep.primary,
                onPrimary = {
                    tutorialVm.complete(accountsStep)
                    ownAccountsVm.clearError()
                    addingOwnAccount = true
                },
                secondaryLabel = accountsStep.secondary,
                onSecondary = { tutorialVm.skip(accountsStep) },
                onDismiss = tutorialVm::dismiss,
                tone = tone,
                beak = CoachBeak.Top(0.25f),
                step = accountsStep.number,
            )
        }
        CoachMarkHost(
            visible = showLockBeat,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            CoachMark(
                label = coachLabel(lockStep, CoachTone.DONE),
                title = lockStep.title,
                body = lockStep.body,
                primaryLabel = lockStep.primary,
                onPrimary = { tutorialVm.complete(lockStep); pinFlow = PinFlow.SET },
                secondaryLabel = lockStep.secondary,
                onSecondary = { tutorialVm.skip(lockStep) },
                onDismiss = tutorialVm::dismiss,
                tone = CoachTone.DONE,
                beak = CoachBeak.Top(0.25f),
            )
        }
    }

    if (addingOwnAccount) {
        OwnAccountDialog(
            onConfirm = { identifier, label, institution, names ->
                ownAccountsVm.add(identifier, label, institution, names)
                // Both routes to this dialog spotlight a real control, so the beat
                // has to close here rather than only in the coach-mark's button —
                // a user who tapped "Add an account" directly did the thing and
                // was still being asked to.
                if (tutorial.showing(accountsStep)) tutorialVm.complete(accountsStep)
                addingOwnAccount = false
            },
            onDismiss = { addingOwnAccount = false },
        )
    }

    when (pinFlow) {
        PinFlow.NONE -> Unit

        PinFlow.SET -> PinDialog(
            title = "Set a PIN",
            message = "Choose 4–8 digits. You'll enter this each time Ledger opens.",
            confirmTwice = true,
            onDismiss = { pinFlow = PinFlow.NONE },
            onSubmit = { pin ->
                securityVm.setPin(pin)
                // Same reason as the own-account dialog: reaching this via the
                // spotlit switch rather than the coach-mark's button still
                // finishes the beat.
                if (tutorial.showing(lockStep)) tutorialVm.complete(lockStep)
                pinFlow = PinFlow.NONE
                null
            },
        )

        PinFlow.CHANGE -> PinDialog(
            title = "Change PIN",
            message = "Enter your current PIN first.",
            confirmTwice = false,
            onDismiss = { pinFlow = PinFlow.NONE },
            onSubmit = { pin ->
                if (securityVm.verifyPin(pin)) { pinFlow = PinFlow.SET; null } else "That PIN doesn't match"
            },
        )

        PinFlow.CONFIRM_OFF -> PinDialog(
            title = "Turn the lock off",
            message = "Enter your PIN to remove app-open protection.",
            confirmTwice = false,
            onDismiss = { pinFlow = PinFlow.NONE },
            onSubmit = { pin ->
                if (securityVm.verifyPin(pin)) { securityVm.disableLock(); pinFlow = PinFlow.NONE; null }
                else "That PIN doesn't match"
            },
        )
    }
}

private enum class PinFlow { NONE, SET, CHANGE, CONFIRM_OFF }

/**
 * Captures one of the user's own accounts: its number, which provider it lives
 * with, and the names other providers print for it.
 *
 * The names field is the point of this dialog. GhanaPay writes the account
 * *number* into its alerts, so a transfer to the user's own MoMo is recognised
 * from the number alone. MTN writes a *name* — "payment received from AMA
 * OWUSU" — which no amount of number-matching can catch, so that leg of the
 * same transfer was counted as income from a stranger. Listing the names makes
 * both halves recognisable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OwnAccountDialog(
    onConfirm: (identifier: String, label: String, institution: String?, names: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LedgerThemeTokens.colors
    var identifier by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var names by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf<Institution?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add one of your wallets") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "The mobile-money number for this wallet. Any format works — " +
                        "024…, +23324…, spaces and dashes are all fine.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = identifier,
                    onValueChange = { identifier = it },
                    label = { Text("Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    "WHICH WALLET IS THIS?",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkMuted,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SenderChip("Not sure", institution == null) { institution = null }
                    Institution.entries.forEach { inst ->
                        SenderChip(inst.displayName, institution == inst) { institution = inst }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Wallets are kept apart by provider, so the same number can be " +
                        "added once for each — useful when one line carries both a " +
                        "MoMo and a GhanaPay account under different names.",
                    style = MaterialTheme.typography.labelSmall, color = colors.inkMuted,
                )

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = names,
                    onValueChange = { names = it },
                    label = { Text("Names it shows up as") },
                    placeholder = { Text("The name providers print for it") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Separate several with commas. MTN says \"received from " +
                        "{name}\" instead of giving a number, so without the name " +
                        "a transfer from your own wallet looks like income from " +
                        "someone else. Different providers may print different " +
                        "names for the same account — list them all.",
                    style = MaterialTheme.typography.labelSmall, color = colors.inkMuted,
                )

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name it (optional)") },
                    placeholder = { Text("e.g. My main wallet") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = identifier.count(Char::isDigit) >= 6,
                onClick = { onConfirm(identifier, label, institution?.name, names) },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SenderChip(label: String, active: Boolean, onClick: () -> Unit) {
    var modifier = Modifier
        .clip(SenderChipShape)
        .background(if (active) LedgerPalette.Ink else LedgerPalette.SurfaceAlt)
    if (!active) modifier = modifier.border(1.dp, LedgerPalette.Hairline, SenderChipShape)
    Box(modifier.clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 7.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) LedgerPalette.Background else LedgerPalette.InkSoft,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private val SenderChipShape = RoundedCornerShape(20.dp)

/**
 * PIN entry dialog. [onSubmit] returns an error message to show, or null when the
 * flow handled the PIN and moved on. With [confirmTwice] the user must type the
 * same PIN a second time before it's accepted.
 */
@Composable
private fun PinDialog(
    title: String,
    message: String,
    confirmTwice: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> String?,
) {
    val colors = LedgerThemeTokens.colors
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val current = if (confirming) repeat else pin
    val valid = current.length in 4..8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (confirming) "Confirm your PIN" else title) },
        text = {
            Column {
                Text(
                    if (confirming) "Type the same digits again." else message,
                    style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = current,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(8)
                        error = null
                        if (confirming) repeat = digits else pin = digits
                    },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.labelSmall, color = colors.spend)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    when {
                        confirmTwice && !confirming -> { confirming = true }
                        confirming -> {
                            if (repeat == pin) error = onSubmit(pin)
                            else { error = "Those PINs don't match"; repeat = "" }
                        }
                        else -> {
                            error = onSubmit(pin)
                            if (error != null) pin = ""
                        }
                    }
                },
            ) { Text(if (confirmTwice && !confirming) "Next" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PrivacyLine(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodyMedium, color = LedgerThemeTokens.colors.inkSoft, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun ActionLine(title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = LedgerThemeTokens.colors.inkMuted)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = LedgerThemeTokens.colors.inkMuted)
    }
}
