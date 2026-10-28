package com.ledger.app.ui.screens

import com.ledger.app.data.db.TransactionEntity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ledger.app.tutorial.TutorialStep
import com.ledger.app.ui.components.CoachBeak
import com.ledger.app.ui.components.CoachMark
import com.ledger.app.ui.components.CoachMarkHost
import com.ledger.app.ui.components.CoachScrim
import com.ledger.app.ui.components.CoachTone
import com.ledger.app.ui.components.LedgerCard
import com.ledger.app.ui.components.coachLabel
import com.ledger.app.ui.components.spotlight
import com.ledger.app.ui.components.formatCedis
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.domain.model.AmountExpression
import com.ledger.domain.model.Money
import com.ledger.app.ui.vm.CashViewModel
import com.ledger.app.ui.vm.TutorialViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The cash ledger: cash on hand, and every cash movement. That is income and
 * spending entered by hand, plus wallet withdrawals and deposits moved here.
 */
@Composable
fun CashScreen(
    onBack: () -> Unit,
    onAdd: (direction: String) -> Unit,
    onOpen: (TransactionEntity) -> Unit,
    vm: CashViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    val step = TutorialStep.CASH_ON_HAND
    val showBeat = tutorial.showing(step)

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            ScreenHeader("Cash", onBack)
        }
        item {
            LedgerCard {
                Text("On hand", style = MaterialTheme.typography.labelSmall, color = LedgerPalette.InkMuted)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatCedis(state.balanceMinor),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (state.balanceMinor < 0) LedgerPalette.Spend else LedgerPalette.Ink,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CashButton("+ Received", LedgerPalette.Income, Modifier.weight(1f)) { onAdd("CREDIT") }
                    CashButton("− Spent", LedgerPalette.Spend, Modifier.weight(1f)) { onAdd("DEBIT") }
                }
                Text(
                    "Set cash on hand",
                    style = MaterialTheme.typography.titleMedium,
                    color = LedgerPalette.Gold,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 2.dp)
                        .spotlight(showBeat, RoundedCornerShape(10.dp))
                        .clickable { onAdd(COUNT) }
                        .padding(if (showBeat) 6.dp else 0.dp),
                )
            }
        }
        if (state.loaded && state.entries.isEmpty()) {
            item { EmptyHint("No cash recorded yet.") }
        }
        items(state.entries, key = { it.id }, contentType = { "txn-row" }) { txn ->
            TransactionRow(txn, onClick = { onOpen(txn) })
        }
        item { Spacer(Modifier.height(if (showBeat) 200.dp else 90.dp)) }
    }

        CoachScrim(showBeat)
        CoachMarkHost(
            visible = showBeat,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            val tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL
            CoachMark(
                label = coachLabel(step, tone),
                title = step.title,
                body = step.body,
                primaryLabel = step.primary,
                // Completed when the count is saved (see LedgerApp).
                onPrimary = { onAdd(COUNT) },
                secondaryLabel = step.secondary,
                onSecondary = { tutorialVm.skip(step) },
                onDismiss = tutorialVm::dismiss,
                tone = tone,
                beak = CoachBeak.Top(0.25f),
                step = step.number,
            )
        }
    }
}

@Composable
private fun CashButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(color, ButtonShape)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

/** The entry screen's third mode: set cash on hand to a counted amount. */
const val COUNT = "COUNT"

/**
 * Enter one cash movement by hand. [direction] is a stored Direction name, or
 * [COUNT] to set cash on hand directly.
 *
 * @param onFindWithdrawal where "A wallet withdrawal" goes after the spend is
 *   saved: a screen where the user can move a withdrawal to cash.
 * @param onCounted called once a [COUNT] is saved, before [onDone].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CashEntryScreen(
    direction: String,
    onDone: () -> Unit,
    onFindWithdrawal: () -> Unit = onDone,
    onCounted: () -> Unit = {},
    vm: CashViewModel = hiltViewModel(),
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val cash by vm.state.collectAsStateWithLifecycle()
    val credit = direction == "CREDIT"
    val counting = direction == COUNT
    // The keypad's sum, such as "120+45". See AmountExpression.
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var picking by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var askingShortfall by remember { mutableStateOf(false) }

    val amountMinor = AmountExpression.evaluate(amount) ?: 0L
    val valid = !saving && amountMinor <= Money.CEILING.minor && if (counting) {
        amount.isNotEmpty() && amountMinor >= 0
    } else {
        amountMinor > 0 && category != null
    }
    // How much this spend needs beyond the cash on hand. A balance already
    // below zero is included, so a top-up brings it back to zero.
    val shortfall = if (credit || counting) 0L else amountMinor - cash.balanceMinor

    fun save(topUp: Long = 0, then: () -> Unit = onDone) {
        saving = true
        vm.add(direction, amountMinor, category, description, note, occurredAt(day), onSaved = then, topUpMinor = topUp)
    }

    val offered = categories.forDirection(direction)
    val quick = remember(categories, direction, category) {
        val head = categories.quickForDirection(direction)
        if (category != null && category !in head) head + category!! else head
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(18.dp))
        ScreenHeader(
            when {
                counting -> "Cash on hand"
                credit -> "Cash received"
                else -> "Cash spent"
            },
            onDone,
        )
        Spacer(Modifier.height(12.dp))

        AmountDisplay(amount, amountMinor)
        if (counting) {
            Text(
                "Enter what you have now. Ledger shows ${formatCedis(cash.balanceMinor)}.",
                style = MaterialTheme.typography.bodyMedium,
                color = LedgerPalette.InkMuted,
                modifier = Modifier.padding(top = 10.dp, start = 2.dp),
            )
        }
        if (!counting) {

        Spacer(Modifier.height(14.dp))
        FieldLabel("Category")
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { cat -> Pill(cat, cat == category) { category = cat } }
            if (offered.any { it !in quick }) Pill("More…", false) { picking = true }
        }

        Spacer(Modifier.height(14.dp))
        FieldLabel(if (credit) "From" else "Paid to")
        Spacer(Modifier.height(4.dp))
        PlainField(description, { description = it }, "Optional", Modifier.fillMaxWidth())

        Spacer(Modifier.height(14.dp))
        FieldLabel("Date")
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth()
                .background(LedgerPalette.SurfaceAlt, FieldShape)
                .clickable { pickingDate = true }
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Text(dayLabel(day), style = MaterialTheme.typography.bodyMedium, color = LedgerPalette.Ink)
        }

        Spacer(Modifier.height(14.dp))
        FieldLabel("Note")
        Spacer(Modifier.height(4.dp))
        PlainField(note, { note = it }, "Optional", Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(12.dp))
    }

        // The keypad is pinned below the form, like a calculator's, so it stays
        // under the thumb.
        Spacer(Modifier.height(8.dp))
        AmountKeypad(
            onKey = { amount = AmountExpression.press(amount, it) },
            onBackspace = { amount = AmountExpression.backspace(amount) },
            onClear = { amount = "" },
            onEquals = { amount = AmountExpression.collapse(amount) },
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth()
                .background(if (valid) LedgerPalette.Income else LedgerPalette.SurfaceSunken, ButtonShape)
                .clickable(enabled = valid) {
                    when {
                        counting -> { saving = true; vm.setOnHand(amountMinor) { onCounted(); onDone() } }
                        shortfall > 0 -> askingShortfall = true
                        else -> save()
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Save",
                style = MaterialTheme.typography.titleMedium,
                color = if (valid) Color.White else LedgerPalette.InkMuted,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    if (picking) {
        CategoryPickerSheet(
            categories = offered,
            current = category,
            onPick = { category = it; picking = false },
            onDismiss = { picking = false },
            title = "Choose a category",
        )
    }
    if (askingShortfall) {
        ShortfallDialog(
            onHandMinor = cash.balanceMinor,
            onAlreadyHad = { askingShortfall = false; save(topUp = shortfall) },
            onWithdrawal = { askingShortfall = false; save(then = onFindWithdrawal) },
            onSaveAnyway = { askingShortfall = false; save() },
            onDismiss = { askingShortfall = false },
        )
    }
    if (pickingDate) {
        DayPickerDialog(
            initialDay = day,
            onConfirm = { day = it; pickingDate = false },
            onDismiss = { pickingDate = false },
        )
    }
}

/**
 * Shown when a cash spend is more than the ledger says is on hand. The user
 * can add the cash they already had (as an adjustment, since it isn't
 * income), go and find the withdrawal it came from, or save anyway. The spend
 * is saved whichever they choose.
 */
@Composable
private fun ShortfallDialog(
    onHandMinor: Long,
    onAlreadyHad: () -> Unit,
    onWithdrawal: () -> Unit,
    onSaveAnyway: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LedgerPalette.Surface,
        title = { Text("Not enough cash on hand", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    "On hand: ${formatCedis(onHandMinor)}. Where did the cash come from?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LedgerPalette.InkSoft,
                )
                Spacer(Modifier.height(10.dp))
                ShortfallChoice("Cash I already had", onAlreadyHad)
                ShortfallChoice("A wallet withdrawal", onWithdrawal)
                ShortfallChoice("Save anyway", onSaveAnyway, muted = true)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LedgerPalette.InkMuted) } },
    )
}

@Composable
private fun ShortfallChoice(label: String, onClick: () -> Unit, muted: Boolean = false) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (muted) LedgerPalette.InkMuted else LedgerPalette.Gold,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

/** What has been typed, spaced out, with the running total below once there is a sum. */
@Composable
private fun AmountDisplay(expression: String, totalMinor: Long) {
    val shown = expression
        .replace("${AmountExpression.PLUS}", " ${AmountExpression.PLUS} ")
        .replace("${AmountExpression.MINUS}", " ${AmountExpression.MINUS} ")
    Column(
        Modifier.fillMaxWidth().background(LedgerPalette.SurfaceAlt, FieldShape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            if (shown.isEmpty()) "₵0.00" else "₵$shown",
            style = MaterialTheme.typography.headlineMedium,
            color = if (shown.isEmpty()) LedgerPalette.InkMuted else LedgerPalette.Ink,
            maxLines = 2,
        )
        if (AmountExpression.hasOperator(expression)) {
            Text(
                "= ${formatCedis(totalMinor)}",
                style = MaterialTheme.typography.titleMedium,
                color = if (totalMinor > 0) LedgerPalette.InkSoft else LedgerPalette.Spend,
            )
        }
    }
}

/** Digits, a point, add and subtract, backspace, clear and equals. */
@Composable
private fun AmountKeypad(
    onKey: (Char) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onEquals: () -> Unit,
) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "${AmountExpression.MINUS}"),
        listOf("1", "2", "3", "${AmountExpression.PLUS}"),
        listOf(".", "0", "C", "="),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val isOperator = key.length == 1 && !key[0].isDigit() && key != "."
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .background(
                                if (isOperator) LedgerPalette.ChipBg else LedgerPalette.SurfaceSunken,
                                KeyShape,
                            )
                            .clickable {
                                when (key) {
                                    "⌫" -> onBackspace()
                                    "C" -> onClear()
                                    "=" -> onEquals()
                                    else -> onKey(key[0])
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            key,
                            style = MaterialTheme.typography.titleLarge,
                            color = if (isOperator) LedgerPalette.GoldDeep else LedgerPalette.Ink,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Today keeps the current time, so the entry sorts after earlier ones. An
 * earlier day is stamped at midday.
 */
private fun occurredAt(epochDay: Long): Long {
    val zone = ZoneId.systemDefault()
    val date = LocalDate.ofEpochDay(epochDay)
    val time = if (date == LocalDate.now(zone)) LocalTime.now(zone) else LocalTime.NOON
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

private fun dayLabel(epochDay: Long): String {
    val date = LocalDate.ofEpochDay(epochDay)
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayFormat.format(date)
    }
}

/**
 * A single-day picker. Material's picker works in UTC midnights; this converts
 * at the edges so the rest of the screen uses plain local dates.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(initialDay: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    val todayUtc = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.ofEpochDay(initialDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
        },
    )
    val colors = DatePickerDefaults.colors(containerColor = LedgerPalette.Background)
    // Day first and weeks starting Monday, like the range picker (see DateRangeDialog).
    val configuration = LocalConfiguration.current
    val ghanaian = remember(configuration) {
        Configuration(configuration).apply {
            setLocale(Locale.Builder().setLanguage("en").setRegion("GH").build())
        }
    }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = colors,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay())
                } ?: onDismiss()
            }) { Text("OK", color = LedgerPalette.Gold, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = LedgerPalette.InkMuted) } },
    ) {
        val column = this
        // Provided inside the dialog. A Dialog has its own view, which would
        // replace an override made outside it.
        CompositionLocalProvider(LocalConfiguration provides ghanaian) {
            with(column) {
                DatePicker(state = state, modifier = Modifier.weight(1f), colors = colors, showModeToggle = false)
            }
        }
    }
}

/**
 * Asks whether a wallet withdrawal (or deposit) was cash, so it isn't counted
 * twice. Shown on the review card and the transaction screen.
 */
@Composable
fun CashMovePrompt(direction: String, onMove: () -> Unit, onKeep: () -> Unit) {
    val debit = direction != "CREDIT"
    Column(
        Modifier.fillMaxWidth()
            .background(LedgerPalette.ChipBg, PromptShape)
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            if (debit) "Cash withdrawal" else "Cash deposit",
            style = MaterialTheme.typography.titleMedium,
            color = LedgerPalette.GoldDeep,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (debit) "Move it to cash on hand?" else "Take it from cash on hand?",
            style = MaterialTheme.typography.bodyMedium,
            color = LedgerPalette.InkSoft,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onKeep) { Text("No", color = LedgerPalette.InkMuted) }
            TextButton(onClick = onMove) {
                Text(if (debit) "Move to cash" else "Take from cash", color = LedgerPalette.Gold, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private val ButtonShape = RoundedCornerShape(14.dp)
private val FieldShape = RoundedCornerShape(12.dp)
private val PromptShape = RoundedCornerShape(12.dp)
private val KeyShape = RoundedCornerShape(12.dp)
