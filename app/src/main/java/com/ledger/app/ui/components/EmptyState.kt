package com.ledger.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.LedgerPalette

// Illustration-specific paper tones, lifted straight from the design mock. These
// aren't part of the semantic palette — they only exist inside the line-art.
private val EnvFill = Color(0xFFF7F2E7)
private val EnvStroke = Color(0xFFD8CDB5)
private val LineDark = Color(0xFFC9BDA3)
private val LineSoft = Color(0xFFDED3BA)
private val DocLineSoft = Color(0xFFE3D8C0)
private val GreenBadgeBg = Color(0xFFE5ECDD)
private val GoldBadgeBg = Color(0xFFF0E6CF)
private val LensStroke = Color(0xFFB9772E)

/** Which small status badge floats on the corner of an envelope illustration. */
enum class EmptyBadge { Check, Plus }

/**
 * The shared empty-state layout from the redesign: a floating line-art
 * illustration, a serif headline, a soft body line, and an optional dark action
 * button + footnote. Used for first-run, all-caught-up, and no-match states.
 */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    /** Rings the action with the guide's gold halo when a coach-mark points at it. */
    spotlightAction: Boolean = false,
    footnote: (@Composable () -> Unit)? = null,
    illustration: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Floaty { illustration() }
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = titleStyle,
            color = LedgerPalette.Ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = LedgerPalette.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 250.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            PrimaryDarkButton(
                actionLabel,
                onAction,
                modifier = Modifier.spotlight(spotlightAction, RoundedCornerShape(13.dp)),
            )
        }
        if (footnote != null) {
            Spacer(Modifier.height(16.dp))
            footnote()
        }
    }
}

/** The dark, cream-on-ink pill button from the mock's empty states. */
@Composable
fun PrimaryDarkButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(LedgerPalette.Ink)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = LedgerPalette.Background,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** "🔒 Nothing leaves this device" style reassurance line under a first-run state. */
@Composable
fun PrivacyFootnote(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = LedgerPalette.InkMuted, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = LedgerPalette.InkMuted)
    }
}

/** Gentle infinite bob, matching the mock's `floaty` keyframe. */
@Composable
private fun Floaty(content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "floaty")
    val dy by transition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dy",
    )
    Box(Modifier.offset(y = dy.dp)) { content() }
}

// ---- Illustrations (Canvas line-art translated from the mock's SVGs) ----

/** An envelope with a small status badge — the arriving-SMS motif. */
@Composable
fun EnvelopeIllustration(badge: EmptyBadge, modifier: Modifier = Modifier) {
    Canvas(modifier.size(112.dp)) {
        val u = size.width / 112f
        fun at(x: Float, y: Float) = Offset(x * u, y * u)
        val body = Offset(22f * u, 26f * u)
        val bodySize = Size(60f * u, 42f * u)
        val radius = CornerRadius(13f * u, 13f * u)
        drawRoundRect(EnvFill, topLeft = body, size = bodySize, cornerRadius = radius)
        drawRoundRect(EnvStroke, topLeft = body, size = bodySize, cornerRadius = radius, style = Stroke(2f * u))

        // Dog-ear flap hanging off the lower-left corner.
        val flap = Path().apply {
            moveTo(34f * u, 68f * u); lineTo(34f * u, 78f * u); lineTo(45f * u, 68f * u); close()
        }
        drawPath(flap, EnvFill)
        drawPath(flap, EnvStroke, style = Stroke(2f * u))

        drawRoundRect(LineDark, topLeft = at(33f, 38f), size = Size(30f * u, 5f * u), cornerRadius = CornerRadius(2.5f * u, 2.5f * u))
        drawRoundRect(LineSoft, topLeft = at(33f, 49f), size = Size(40f * u, 5f * u), cornerRadius = CornerRadius(2.5f * u, 2.5f * u))

        val badgeCenter = at(83f, 31f)
        val badgeRadius = 15f * u
        when (badge) {
            EmptyBadge.Check -> {
                drawCircle(GreenBadgeBg, badgeRadius, badgeCenter)
                drawCheck(badgeCenter, badgeRadius, LedgerPalette.Income, 2.6f * u)
            }
            EmptyBadge.Plus -> {
                drawCircle(GoldBadgeBg, badgeRadius, badgeCenter)
                drawPlus(badgeCenter, badgeRadius, LedgerPalette.GoldDeep, 2.8f * u)
            }
        }
    }
}

/** A document behind a magnifier — the "searched but found nothing" motif. */
@Composable
fun SearchIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.size(104.dp)) {
        val u = size.width / 104f
        fun at(x: Float, y: Float) = Offset(x * u, y * u)
        val doc = Offset(22f * u, 20f * u)
        val docSize = Size(50f * u, 48f * u)
        val radius = CornerRadius(11f * u, 11f * u)
        drawRoundRect(EnvFill, topLeft = doc, size = docSize, cornerRadius = radius)
        drawRoundRect(EnvStroke, topLeft = doc, size = docSize, cornerRadius = radius, style = Stroke(2f * u))

        drawRoundRect(EnvStroke, topLeft = at(31f, 31f), size = Size(24f * u, 5f * u), cornerRadius = CornerRadius(2.5f * u, 2.5f * u))
        drawRoundRect(DocLineSoft, topLeft = at(31f, 42f), size = Size(32f * u, 4.5f * u), cornerRadius = CornerRadius(2.25f * u, 2.25f * u))
        drawRoundRect(DocLineSoft, topLeft = at(31f, 52f), size = Size(18f * u, 4.5f * u), cornerRadius = CornerRadius(2.25f * u, 2.25f * u))

        val lens = at(66f, 66f)
        val lensRadius = 15f * u
        drawCircle(LedgerPalette.Background, lensRadius, lens)
        drawCircle(LensStroke, lensRadius, lens, style = Stroke(3.4f * u))
        drawLine(LensStroke, at(77f, 77f), at(88f, 88f), 3.4f * u, cap = StrokeCap.Round)
    }
}

/** A soft green disc with a check — the "all caught up" motif. */
@Composable
fun CheckCircleIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier.size(64.dp)) {
        val r = size.minDimension / 2f
        drawCircle(GreenBadgeBg, r, center)
        drawCheck(center, r * 0.9f, LedgerPalette.Income, size.minDimension * 0.045f)
    }
}

private fun DrawScope.drawCheck(c: Offset, r: Float, color: Color, width: Float) {
    val p1 = Offset(c.x - 0.42f * r, c.y + 0.02f * r)
    val p2 = Offset(c.x - 0.10f * r, c.y + 0.34f * r)
    val p3 = Offset(c.x + 0.46f * r, c.y - 0.40f * r)
    drawLine(color, p1, p2, width, cap = StrokeCap.Round)
    drawLine(color, p2, p3, width, cap = StrokeCap.Round)
}

private fun DrawScope.drawPlus(c: Offset, r: Float, color: Color, width: Float) {
    drawLine(color, Offset(c.x, c.y - 0.4f * r), Offset(c.x, c.y + 0.4f * r), width, cap = StrokeCap.Round)
    drawLine(color, Offset(c.x - 0.4f * r, c.y), Offset(c.x + 0.4f * r, c.y), width, cap = StrokeCap.Round)
}
