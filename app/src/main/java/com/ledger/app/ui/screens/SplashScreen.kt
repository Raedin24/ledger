package com.ledger.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ledger.app.ui.components.cediPath
import com.ledger.app.ui.theme.LedgerPalette
import com.ledger.app.ui.theme.Newsreader
import kotlinx.coroutines.delay

/** Paper cream, used for the mark sitting on the terracotta tile. */
private val SealPaper = Color(0xFFF1E9D8)

/**
 * Speeds up the design's animation timings to 90%.
 *
 * The proportions between steps stay the same. At full length the splash
 * would hold every launch for 2.4 seconds; at 0.9 it takes about 2.16
 * seconds, which is still long enough to see the seal draw.
 */
private const val TEMPO = 0.9f

private const val MARK_RISE_MS = 700
private const val SEAL_DRAW_MS = 1100
private const val SEAL_DRAW_DELAY = 450
private const val GLYPH_MS = 1000
private const val GLYPH_DELAY = 700
private const val STRUCK_MS = 1000
private const val STRUCK_DELAY = 900
private const val WORD_MS = 1100
private const val WORD_DELAY = 300
private const val SUB_MS = 1300
private const val SUB_DELAY = 500
private const val FOOTER_MS = 1600
private const val FOOTER_DELAY = 800

/** When the last animation (the footer) finishes. The splash is held exactly this long. */
private val TIMELINE_MS = (FOOTER_DELAY + FOOTER_MS) * TEMPO

private val MarkRiseEasing = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)

/**
 * The launch splash: a coin seal drawing itself on ruled paper.
 *
 * The system splash only shows the plain cream page (see
 * `splash_no_icon.xml`). This screen does the whole animation, because an
 * animation can't be handed over halfway.
 *
 * It runs on its own timer and calls [onFinished] when the animation ends. It
 * never waits for data; each screen's loading placeholders cover that.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    // One clock drives every animation, so they stay in step.
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(TIMELINE_MS, tween(TIMELINE_MS.toInt(), easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        delay(TIMELINE_MS.toLong())
        onFinished()
    }
    val t = clock.value

    val riseProgress = MarkRiseEasing.transform(phase(t, 0, MARK_RISE_MS))

    Box(
        Modifier.fillMaxSize().background(LedgerPalette.Background),
        contentAlignment = Alignment.Center,
    ) {
        RuledPaper()

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SealTile(
                // The design's stroke-dashoffset on the ring becomes a sweep angle here.
                ringProgress = phase(t, SEAL_DRAW_DELAY, SEAL_DRAW_MS),
                glyphAlpha = wordFade(t, GLYPH_DELAY, GLYPH_MS),
                struckAlpha = wordFade(t, STRUCK_DELAY, STRUCK_MS),
                modifier = Modifier
                    .padding(bottom = 26.dp)
                    .graphicsLayer {
                        // markRise: up 10dp and out from 96% as the mark lands.
                        alpha = phase(t, 0, MARK_RISE_MS)
                        scaleX = 0.96f + 0.04f * riseProgress
                        scaleY = scaleX
                        translationY = (1f - riseProgress) * 10.dp.toPx()
                    },
            )

            Text(
                "Ledger",
                fontFamily = Newsreader,
                fontWeight = FontWeight.SemiBold,
                fontSize = 40.sp,
                letterSpacing = (-0.4).sp,
                color = LedgerPalette.Ink,
                modifier = Modifier.alpha(wordFade(t, WORD_DELAY, WORD_MS)),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your money, kept on this phone",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 12.5.sp,
                color = LedgerPalette.InkMuted,
                letterSpacing = 0.4.sp,
                modifier = Modifier.alpha(wordFade(t, SUB_DELAY, SUB_MS)),
            )
        }

        // The privacy line sits at the bottom of the splash.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp)
                .alpha(wordFade(t, FOOTER_DELAY, FOOTER_MS)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PulsingDots()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = LedgerPalette.InkMuted,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    "Nothing leaves this device",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = LedgerPalette.InkMuted,
                    letterSpacing = 0.33.sp,
                )
            }
        }
    }
}

/**
 * Progress from 0 to 1 through the window starting at [delay] and lasting
 * [duration]. Both are scaled by [TEMPO].
 */
private fun phase(t: Float, delay: Int, duration: Int): Float =
    ((t - delay * TEMPO) / (duration * TEMPO)).coerceIn(0f, 1f)

/**
 * The design's `wordFade`. It stays at zero for the first 60% of its run, so
 * the seal lands before the words appear.
 */
private fun wordFade(t: Float, delay: Int, duration: Int): Float =
    ((phase(t, delay, duration) - 0.6f) / 0.4f).coerceIn(0f, 1f)

/**
 * Faint ruled lines behind the mark, like a ledger page. Drawn rather than
 * tiled from a bitmap, so they stay sharp at any screen density.
 */
@Composable
private fun RuledPaper() {
    Canvas(Modifier.fillMaxSize()) {
        val gap = 46.dp.toPx()
        val ink = LedgerPalette.Ink.copy(alpha = 0.05f)
        val hairline = 1.dp.toPx()
        var y = gap
        while (y < size.height) {
            drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = hairline)
            y += gap
        }
    }
}

/**
 * The terracotta tile and the seal on it. [ringProgress] draws the outer ring
 * from the top; [struckAlpha] fades in the dashed inner ring and [glyphAlpha]
 * the ₵.
 */
@Composable
private fun SealTile(
    ringProgress: Float,
    glyphAlpha: Float,
    struckAlpha: Float,
    modifier: Modifier = Modifier,
) {
    // The ₵ uses the launcher icon's own outline, built once for this density.
    // Using the real shape (see cediPath) rather than a text glyph keeps it
    // identical to the icon and centred, whatever fonts the device has.
    val markPx = with(LocalDensity.current) { SEAL_CANVAS.toPx() }
    val glyph = remember(markPx) { cediPath(markPx / 100f, Offset(markPx / 2f, markPx / 2f)) }

    Box(
        modifier
            .size(118.dp)
            .background(LedgerPalette.Spend, TileShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(SEAL_CANVAS)) {
            // The design uses a 100-unit box, so its sizes carry over directly.
            val u = size.minDimension / 100f
            val centre = Offset(size.width / 2f, size.height / 2f)

            val rOuter = 33f * u
            drawArc(
                color = SealPaper,
                startAngle = -90f,
                sweepAngle = 360f * ringProgress,
                useCenter = false,
                topLeft = Offset(centre.x - rOuter, centre.y - rOuter),
                size = Size(rOuter * 2, rOuter * 2),
                style = Stroke(width = 3.4f * u, cap = StrokeCap.Butt),
            )

            if (struckAlpha > 0f) {
                drawCircle(
                    color = SealPaper,
                    radius = 27f * u,
                    center = centre,
                    alpha = struckAlpha,
                    style = Stroke(
                        width = 1.3f * u,
                        // The design's `stroke-dasharray: 2 5.5`, in those units.
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * u, 5.5f * u)),
                    ),
                )
            }

            if (glyphAlpha > 0f) {
                drawPath(glyph, SealPaper, alpha = glyphAlpha)
            }
        }
    }
}

/**
 * The seal's drawing area inside the tile. The ₵ path is sized from it, so it
 * has to be a constant rather than read from the layout.
 */
private val SEAL_CANVAS = 70.dp
private val TileShape = RoundedCornerShape(30.dp)

/** Three gold dots that pulse one after another. */
@Composable
private fun PulsingDots() {
    val transition = rememberInfiniteTransition(label = "splash-dots")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600, delayMillis = i * 200),
                    RepeatMode.Reverse,
                ),
                label = "dot$i",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .background(LedgerPalette.Gold, CircleShape),
            )
        }
    }
}
