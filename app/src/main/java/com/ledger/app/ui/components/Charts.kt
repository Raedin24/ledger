package com.ledger.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.LedgerPalette

/**
 * A daily bar chart for the cash-flow timeline, drawn without a chart library.
 * Each value is one day, and bar height is relative to the biggest day, so it
 * shows the pattern of spending rather than exact figures. Days with nothing
 * spent show a faint track.
 */
@Composable
/**
 * @param elapsed how many of the days have happened. Later days are drawn
 *  fainter, so a day with no spending looks different from a day still to
 *  come, and an early month doesn't look like a month of no spending.
 */
fun MiniBarChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    barColor: Color = LedgerPalette.Spend,
    trackColor: Color = LedgerPalette.SurfaceSunken,
    elapsed: Int = values.size,
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier) {
        val n = values.size.coerceAtLeast(1)
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val radius = CornerRadius(barW / 2f, barW / 2f)
        values.forEachIndexed { i, v ->
            val x = i * (barW + gap)
            // A faint full-height track, so a quiet month still looks like a timeline.
            drawRoundRect(
                color = trackColor,
                alpha = if (i < elapsed) 1f else 0.45f,
                topLeft = Offset(x, 0f),
                size = Size(barW, size.height),
                cornerRadius = radius,
            )
            if (v > 0) {
                val h = (v.toFloat() / max) * size.height
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = radius,
                )
            }
        }
    }
}

/** Shared by every bar and badge. */
private val BarShape = RoundedCornerShape(4.dp)
private val BadgeShape = RoundedCornerShape(8.dp)

/**
 * A thin proportional bar for the Top spending list. [fraction] is clamped to
 * 0..1, and the fill is the category's accent at 72% opacity.
 *
 * Both layers draw their own rounded shape instead of clipping, which keeps a
 * long list of these cheap to draw.
 */
@Composable
fun CategoryBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(7.dp)
            .background(LedgerPalette.SurfaceSunken, BarShape),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(7.dp)
                .background(color.copy(alpha = 0.72f), BarShape),
        )
    }
}

/** One wedge of a [DonutChart]. */
data class DonutSlice(val value: Long, val color: Color)

/**
 * A ring chart showing the breakdown at a glance, with the total in the middle.
 *
 * Drawn as arcs on a Canvas instead of using a chart library, which keeps the
 * app small and fully offline.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    trackColor: Color = LedgerPalette.SurfaceSunken,
    centre: @Composable () -> Unit = {},
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val thickness = size.minDimension * 0.17f
            val diameter = size.minDimension - thickness
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val ring = Stroke(width = thickness, cap = StrokeCap.Butt)

            // A faint full ring underneath, so an empty month still looks like a chart.
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = ring)

            val total = slices.sumOf { it.value }.coerceAtLeast(1L)
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value.toFloat() / total)
                if (sweep > 0f) {
                    // A thin gap between slices, so neighbouring colours don't run together.
                    drawArc(
                        color = slice.color,
                        startAngle = start + SLICE_GAP / 2f,
                        sweepAngle = (sweep - SLICE_GAP).coerceAtLeast(MIN_SWEEP),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = ring,
                    )
                }
                start += sweep
            }
        }
        centre()
    }
}

/**
 * Degrees of gap between slices, and the smallest size a slice is drawn at,
 * so a tiny slice doesn't disappear into the gap.
 */
private const val SLICE_GAP = 1.6f
private const val MIN_SWEEP = 0.8f

/**
 * A small pill showing the change in spending from last month. Spending more
 * is shown in terracotta and spending less in green, which matches what the
 * user cares about rather than the sign of the number.
 */
@Composable
fun MonthDeltaBadge(percent: Int, modifier: Modifier = Modifier) {
    val up = percent > 0
    val flat = percent == 0
    val color = when {
        flat -> LedgerPalette.InkMuted
        up -> LedgerPalette.Spend
        else -> LedgerPalette.Income
    }
    val arrow = when {
        flat -> "→"
        up -> "▲"
        else -> "▼"
    }
    Text(
        "$arrow ${kotlin.math.abs(percent)}%",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(LedgerPalette.SurfaceSunken, BadgeShape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
