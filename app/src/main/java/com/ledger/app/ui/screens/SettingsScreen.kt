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
import androidx.compose.foundation.layout.size
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
import com.ledger.domain.model.FiscalMonth
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
import com.ledger.app.data.prefs.ordinal
import com.ledger.app.ui.vm.OwnAccountsViewModel
import com.ledger.app.ui.vm.PeriodViewModel
import com.ledger.app.ui.vm.SecurityViewModel
import com.ledger.app.ui.vm.TutorialViewModel

/**
 * List positions of the cards the setup guide points at, so it can scroll them
 * into view. The list below is a fixed sequence of `item {}` blocks: header,
 * providers, my accounts, sorting, security, budgets, privacy, data, getting
 * started. Update these if a card is added above any of them.
 */
private const val ACCOUNTS_CARD = 2
private const val SECURITY_CARD = 4
private const val BUDGETS_CARD = 5

@Composable
fun SettingsScreen(
    onManageRules: () -> Unit,
    onManageCategories: () -> Unit,
    onManageBudgets: () -> Unit,
    onOpenData: () -> Unit,
    securityVm: SecurityViewModel = hiltViewModel(),
    ownAccountsVm: OwnAccountsViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
    periodVm: PeriodViewModel = hiltViewModel(),
) {
    val security by securityVm.state.collectAsStateWithLifecycle()
    val ownAccounts by ownAccountsVm.accounts.collectAsStateWithLifecycle()
    val ownAccountError by ownAccountsVm.error.collectAsStateWithLifecycle()
    val colors = LedgerThemeTokens.colors
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    var pinFlow by remember { mutableStateOf(PinFlow.NONE) }
    var addingOwnAccount by remember { mutableStateOf(false) }
    val monthStart by periodVm.startDay.collectAsStateWithLifecycle()
    val carryOver by periodVm.carryOver.collectAsStateWithLifecycle()
    var pickingMonthStart by remember { mutableStateOf(false) }

    // Four guide steps live on this screen: own accounts, the month's start day
    // and budgets in a row, then the optional lock step at the very end.
    val accountsStep = TutorialStep.OWN_ACCOUNTS
    val monthStep = TutorialStep.MONTH_START
    val budgetsStep = TutorialStep.BUDGETS
    val lockStep = TutorialStep.APP_LOCK
    val showAccountsBeat = tutorial.showing(accountsStep) && !addingOwnAccount
    // Hidden while the accounts dialog is open. "Add mine" completes the accounts
    // step as the dialog opens, which makes this step current behind the dialog.
    val showMonthBeat = tutorial.showing(monthStep) && !pickingMonthStart && !addingOwnAccount
    val showBudgetsBeat = tutorial.showing(budgetsStep) && !pickingMonthStart
    val showLockBeat = tutorial.showing(lockStep) && pinFlow == PinFlow.NONE
    val anyBeat = showAccountsBeat || showMonthBeat || showBudgetsBeat || showLockBeat
    val tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL

    // Settings is longer than the screen, and the cards these steps point at
    // can be below the fold, so scroll the right one into view.
    val listState = rememberLazyListState()
    LaunchedEffect(showAccountsBeat, showMonthBeat, showBudgetsBeat, showLockBeat) {
        when {
            showAccountsBeat -> listState.animateScrollToItem(ACCOUNTS_CARD)
            showMonthBeat || showBudgetsBeat -> listState.animateScrollToItem(BUDGETS_CARD)
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

        item {
            LedgerCard {
                Text("Captured providers", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    Institution.PROVIDERS.joinToString(" · ") { it.displayName },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }

        // My accounts: used to recognise transfers between the user's own wallets.
        item {
            LedgerCard {
                Text("My accounts", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Transfers between your own wallets aren't counted as spending.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted,
                )
                Spacer(Modifier.height(10.dp))
                if (ownAccounts.isEmpty()) {
                    Text(
                        "None added yet.",
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
                                // Listing the names helps explain a missed self-transfer: if the name
                                // in the alert isn't here, that's why.
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

        item {
            LedgerCard {
                Text("Sorting", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                ActionLine("Rules", "How senders auto-categorise", onManageRules)
                ActionLine("Categories", "Add, rename, reorder, or hide", onManageCategories)
            }
        }

        // Security: the optional app lock. A PIN first, then biometrics on top.
        item {
            LedgerCard {
                Text("Security", style = MaterialTheme.typography.titleLarge)

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Ask every time the app opens", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (security.hasPin) "PIN required" else "Off",
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

        // Budgets. Keep this below Security: the guide scrolls to fixed list
        // positions, and a card added above Security would shift them.
        item {
            LedgerCard {
                Text("Budgets", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                ActionLine(
                    "Monthly budgets",
                    "Set, change, or remove a limit",
                    onManageBudgets,
                    Modifier.spotlight(showBudgetsBeat, RoundedCornerShape(10.dp)),
                )
                ActionLine(
                    "Month starts on",
                    "The ${ordinal(monthStart)}",
                    { pickingMonthStart = true },
                    Modifier.spotlight(showMonthBeat, RoundedCornerShape(10.dp)),
                )
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Carry over savings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Adds what's left each month to the next",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.inkMuted,
                        )
                    }
                    Switch(checked = carryOver, onCheckedChange = periodVm::setCarryOver)
                }
            }
        }

        item {
            LedgerCard {
                Text("Privacy", style = MaterialTheme.typography.titleLarge)
                PrivacyLine("Raw SMS is never stored. Only parsed fields are kept.")
                PrivacyLine("No internet permission. Nothing leaves this device.")
                PrivacyLine("Database encrypted with a hardware-backed key.")
            }
        }

        item {
            LedgerCard {
                Text("Data", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                ActionLine("Export & backup", "CSV, JSON, encrypted backup & restore", onOpenData)
            }
        }

        // The guide can always be replayed, so dismissing it is never final.
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
        item { Spacer(Modifier.height(if (anyBeat) 200.dp else 90.dp)) }
    }

        // Only one guide step is current at a time, so these never overlap.
        CoachScrim(anyBeat)
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
            visible = showMonthBeat,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            CoachMark(
                label = coachLabel(monthStep, tone),
                title = monthStep.title,
                body = monthStep.body,
                primaryLabel = monthStep.primary,
                // The step completes when the dialog closes, whether or not a day was picked.
                onPrimary = { pickingMonthStart = true },
                secondaryLabel = monthStep.secondary,
                onSecondary = { tutorialVm.skip(monthStep) },
                onDismiss = tutorialVm::dismiss,
                tone = tone,
                beak = CoachBeak.Top(0.25f),
                step = monthStep.number,
            )
        }
        CoachMarkHost(
            visible = showBudgetsBeat,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            CoachMark(
                label = coachLabel(budgetsStep, tone),
                title = budgetsStep.title,
                body = budgetsStep.body,
                primaryLabel = budgetsStep.primary,
                // Completed when the user leaves the Budgets screen (see LedgerApp).
                // Completing it here would send the guide to the next step straight away.
                onPrimary = onManageBudgets,
                secondaryLabel = budgetsStep.secondary,
                onSecondary = { tutorialVm.skip(budgetsStep) },
                onDismiss = tutorialVm::dismiss,
                tone = tone,
                beak = CoachBeak.Top(0.25f),
                step = budgetsStep.number,
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

    if (pickingMonthStart) {
        val closeMonthStart = {
            pickingMonthStart = false
            if (tutorial.showing(monthStep)) tutorialVm.complete(monthStep)
        }
        MonthStartDialog(
            current = monthStart,
            onPick = { periodVm.setStartDay(it); closeMonthStart() },
            onDismiss = closeMonthStart,
        )
    }
    if (addingOwnAccount) {
        OwnAccountDialog(
            onConfirm = { identifier, label, institution, names ->
                ownAccountsVm.add(identifier, label, institution, names)
                // The user can reach this dialog from the guide's button or by tapping
                // "Add an account" directly. Either way they did the step, so complete it.
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
                // As with the accounts dialog: turning the switch on directly also
                // completes the step.
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
 * Adds one of the user's own accounts: its number, its provider, and the names
 * other providers print for it.
 *
 * The names matter most. GhanaPay puts the account number in its alerts, so a
 * transfer to the user's own wallet is recognised from the number. MTN puts a
 * name instead ("payment received from AMA OWUSU"), so that side of the
 * transfer can only be recognised if the name is listed here.
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
                    Institution.PROVIDERS.forEach { inst ->
                        SenderChip(inst.displayName, institution == inst) { institution = inst }
                    }
                }

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
                    "The name alerts show instead of the number. Separate several with commas.",
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

/** Day-of-month grid from 1 to 31. See [com.ledger.domain.model.FiscalMonth]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthStartDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LedgerPalette.Surface,
        title = { Text("Month starts on", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "Set it to your payday. Shorter months use their last day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerThemeTokens.colors.inkMuted,
                )
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    maxItemsInEachRow = 7,
                ) {
                    (1..FiscalMonth.MAX_START_DAY).forEach { day ->
                        val active = day == current
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) LedgerPalette.Gold else LedgerPalette.SurfaceSunken)
                                .clickable { onPick(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$day",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) androidx.compose.ui.graphics.Color.White else LedgerPalette.Ink,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = LedgerPalette.Gold, fontWeight = FontWeight.Bold) }
        },
    )
}

@Composable
private fun ActionLine(title: String, sub: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
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
