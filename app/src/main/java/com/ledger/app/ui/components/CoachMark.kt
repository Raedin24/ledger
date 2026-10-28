package com.ledger.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.tutorial.TutorialStep

/**
 * Colours for the setup guide. The guide's card is the only dark surface in
 * the app, so it reads as an overlay rather than another card. That is why
 * these live here and not in the shared palette.
 */
private object Coach {
    val Card = Color(0xFF2A251D)
    val CardWarn = Color(0xFF3A2F1A)
    val Label = Color(0xFFCF9A3F)
    val LabelWarn = Color(0xFFE6B866)
    val LabelDone = Color(0xFF8FC08A)
    val LabelResumed = Color(0xFF8AA8C4)
    val Title = Color(0xFFF1E9D8)
    val TitleWarn = Color(0xFFF6ECD6)
    val Body = Color(0xFFC2B394)
    val BodyWarn = Color(0xFFD6C295)
    val PrimaryBg = Color(0xFFEFE7D5)
    val PrimaryFg = Color(0xFF2A251D)
    val Secondary = Color(0xFFA99B7D)
    val SecondaryWarn = Color(0xFFBFA268)
    val Dismiss = Color(0xFF8F8163)
    val DismissWarn = Color(0xFFA68A55)
    val DotOn = Color(0xFFCF9A3F)
    val DotOff = Color(0xFF5C5138)
    val Spotlight = Color(0xFFA9772A)
    val SpotlightHalo = Color(0x38A9772A)
    /** Only about 12%, so the highlighted control stays bright and readable. */
    val Scrim = Color(0x1F1E160C)
}

/** How the guide's card looks. */
enum class CoachTone {
    /** An ordinary step. */
    NORMAL,
    /** Something the user declined (such as SMS access). Amber, but not alarming. */
    WARNING,
    /** The step the user just finished. */
    DONE,
    /** Reopened from Settings → Replay setup guide. */
    RESUMED,
}

/** Which edge the card's pointer is on, and how far along it. */
sealed interface CoachBeak {
    data object None : CoachBeak
    /** Pointer on the top edge, pointing up at a control above the card. */
    data class Top(val fraction: Float = 0.5f) : CoachBeak
    /** Pointer on the bottom edge, pointing down at a control below the card. */
    data class Bottom(val fraction: Float = 0.5f) : CoachBeak
}

/**
 * One step of the setup guide: a dark card that points at a real control,
 * says why it is useful, and always offers a way out.
 *
 * It never covers the control it is about. Callers place it in free space and
 * aim the [beak] (the small pointer) at the control. [onDismiss] is the close
 * button and pauses the whole guide. [secondaryLabel] ("Skip") only moves past
 * this step.
 */
@Composable
fun CoachMark(
    label: String,
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    tone: CoachTone = CoachTone.NORMAL,
    beak: CoachBeak = CoachBeak.None,
    /** 1-based position. Leave at 0 to hide the progress dots. */
    step: Int = 0,
    totalSteps: Int = TutorialStep.COUNTED,
) {
    val warn = tone == CoachTone.WARNING
    val cardColor = if (warn) Coach.CardWarn else Coach.Card

    Column(modifier) {
        if (beak is CoachBeak.Top) Beak(cardColor, beak.fraction, pointingUp = true)

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(cardColor)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            // Header: the step label and the close button.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ToneLabel(label, tone, Modifier.weight(1f))
                Icon(
                    Icons.Default.Close,
                    contentDescription = "End guide",
                    tint = if (warn) Coach.DismissWarn else Coach.Dismiss,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss)
                        .padding(3.dp),
                )
            }

            Spacer(Modifier.height(7.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (warn) Coach.TitleWarn else Coach.Title,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                body,
                style = MaterialTheme.typography.labelLarge,
                color = if (warn) Coach.BodyWarn else Coach.Body,
                lineHeight = 16.sp,
            )

            if (primaryLabel.isNotEmpty() || secondaryLabel != null) {
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (primaryLabel.isNotEmpty()) {
                        Text(
                            primaryLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (warn) Coach.CardWarn else Coach.PrimaryFg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (warn) Coach.LabelWarn else Coach.PrimaryBg)
                                .clickable(onClick = onPrimary)
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                        )
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        Text(
                            secondaryLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (warn) Coach.SecondaryWarn else Coach.Secondary,
                            maxLines = 1,
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onSecondary)
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            if (step > 0) {
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(totalSteps) { i ->
                        Box(
                            Modifier
                                .width(16.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i == step - 1) Coach.DotOn else Coach.DotOff),
                        )
                    }
                }
            }
        }

        if (beak is CoachBeak.Bottom) Beak(cardColor, beak.fraction, pointingUp = false)
    }
}

/** The small rotated square that points from the card to its control. */
@Composable
private fun Beak(color: Color, fraction: Float, pointingUp: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val x = maxWidth * fraction.coerceIn(0f, 1f)
        Box(
            Modifier
                .offset(x = x - 7.dp, y = if (pointingUp) 7.dp else (-7).dp)
                .size(14.dp)
                .rotate(45f)
                .background(color),
        )
        // Leave room for the half of the pointer that sticks out past the card.
        Spacer(Modifier.height(7.dp))
    }
}

@Composable
private fun ToneLabel(text: String, tone: CoachTone, modifier: Modifier = Modifier) {
    val color = when (tone) {
        CoachTone.NORMAL -> Coach.Label
        CoachTone.WARNING -> Coach.LabelWarn
        CoachTone.DONE -> Coach.LabelDone
        CoachTone.RESUMED -> Coach.LabelResumed
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (tone) {
            CoachTone.WARNING -> Icon(
                Icons.Default.WarningAmber, contentDescription = null,
                tint = color, modifier = Modifier.size(13.dp).padding(end = 1.dp),
            )
            CoachTone.DONE -> Icon(
                Icons.Default.Check, contentDescription = null,
                tint = color, modifier = Modifier.size(13.dp).padding(end = 1.dp),
            )
            else -> Unit
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            fontSize = 10.sp,
            modifier = Modifier.padding(start = if (tone == CoachTone.NORMAL || tone == CoachTone.RESUMED) 0.dp else 5.dp),
        )
    }
}

/**
 * The gold outline that makes the highlighted control stand out above the
 * scrim. It goes on the real control, so the user taps exactly what the guide
 * describes.
 */
fun Modifier.spotlight(active: Boolean, shape: RoundedCornerShape = RoundedCornerShape(12.dp)): Modifier =
    if (!active) this
    else this
        .border(9.dp, Coach.SpotlightHalo, shape)
        .border(3.dp, Coach.Spotlight, shape)

/**
 * The faint shade over everything except the highlighted control. It is only
 * about 12% and doesn't take taps, so the guide never blocks what it describes.
 */
@Composable
fun CoachScrim(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Coach.Scrim))
    }
}

/**
 * How a guide card appears: it fades in and rises into place, so it reads as
 * sitting over the screen.
 */
@Composable
fun CoachMarkHost(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 4 },
    ) { content() }
}

/** "STEP 4 OF 8", or the header for the card's tone. */
fun coachLabel(step: TutorialStep, tone: CoachTone): String = when {
    tone == CoachTone.DONE -> "DONE · STEP ${step.number}"
    !step.counted -> "OPTIONAL · YOU'RE DONE"
    tone == CoachTone.RESUMED -> "PICKING UP · STEP ${step.number} OF ${TutorialStep.COUNTED}"
    else -> "STEP ${step.number} OF ${TutorialStep.COUNTED}"
}
