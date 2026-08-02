package com.ledger.app.ui.screens

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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.ledger.app.ui.components.directionLabel
import com.ledger.app.ui.components.formatCedis
import com.ledger.app.ui.components.spotlight
import com.ledger.app.ui.theme.LedgerThemeTokens
import com.ledger.app.ui.vm.OnboardingViewModel
import com.ledger.app.ui.vm.TutorialViewModel

@Composable
fun OnboardingSenderScreen(
    /** True when the category shortlist has yet to be offered — see
     *  [OnboardingViewModel.enableAndFinish]. */
    onDone: (pickCategories: Boolean) -> Unit,
    onSkip: () -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
    tutorialVm: TutorialViewModel = hiltViewModel(),
) {
    val sample by vm.sample.collectAsStateWithLifecycle(initialValue = "")
    val detection by vm.detection.collectAsStateWithLifecycle(initialValue = null)
    val tutorial by tutorialVm.state.collectAsStateWithLifecycle()
    val colors = LedgerThemeTokens.colors
    val step = TutorialStep.DETECT_FORMAT
    // Beat 2 steps aside the moment the user has a result to act on.
    val showBeat2 = tutorial.showing(step) && detection == null

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text("Add a sender", style = MaterialTheme.typography.displayMedium)
        Text(
            "Paste one message so the app can learn the format. Nothing is sent anywhere — it's parsed on this device.",
            style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted,
        )

        LedgerCard {
            Text("Sample message", style = MaterialTheme.typography.labelSmall, color = colors.inkMuted)
            TextField(
                value = sample,
                onValueChange = vm::onSampleChange,
                placeholder = { Text("Paste a transaction SMS…") },
                modifier = Modifier.fillMaxWidth().height(140.dp),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceAlt,
                    unfocusedContainerColor = colors.surfaceAlt,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        PrimaryButton(
            "Detect format",
            enabled = sample.isNotBlank(),
            bg = colors.gold,
            modifier = Modifier.spotlight(showBeat2, RoundedCornerShape(14.dp)),
        ) { vm.detect() }

        detection?.let { d ->
            LedgerCard {
                if (d.recognised && d.preview != null) {
                    Text("Format recognised ✓", style = MaterialTheme.typography.titleMedium, color = colors.income)
                    Text(d.institution?.displayName ?: "", style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        directionLabel(d.preview.direction.name) + " " +
                            formatCedis(d.preview.amount.minor) +
                            (d.preview.counterparty?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Enable live capture", enabled = true, bg = colors.income) {
                        // Enabling the sender *is* beat 2 done — otherwise the
                        // guide sits here forever, since the mark hides itself
                        // as soon as a format is recognised.
                        if (tutorial.showing(step)) tutorialVm.complete(step)
                        vm.enableAndFinish(onDone)
                    }
                } else {
                    Text("Couldn't recognise that one", style = MaterialTheme.typography.titleMedium, color = colors.spend)
                    Text(
                        "It may be an OTP, a promo, or a format not yet supported. Try a clear debit or credit alert.",
                        style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted,
                    )
                }
            }
        }

        Text("Skip for now", style = MaterialTheme.typography.titleMedium, color = colors.inkMuted, modifier = Modifier.clickable(onClick = onSkip).padding(8.dp))
    }

        // Beat 2 — beak points down at the Detect button above it.
        CoachScrim(showBeat2)
        CoachMarkHost(
            visible = showBeat2,
            modifier = Modifier.align(Alignment.BottomCenter).padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
        ) {
            CoachMark(
                label = coachLabel(step, if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL),
                title = step.title,
                body = step.body,
                primaryLabel = step.primary,
                onPrimary = { tutorialVm.complete(step) },
                secondaryLabel = step.secondary,
                onSecondary = { tutorialVm.skip(step); onSkip() },
                onDismiss = tutorialVm::dismiss,
                tone = if (tutorial.resumed) CoachTone.RESUMED else CoachTone.NORMAL,
                beak = CoachBeak.Top(0.3f),
                step = step.number,
            )
        }
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    enabled: Boolean,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else LedgerThemeTokens.colors.surfaceSunken)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) Color.White else LedgerThemeTokens.colors.inkMuted, fontWeight = FontWeight.Bold)
    }
}
