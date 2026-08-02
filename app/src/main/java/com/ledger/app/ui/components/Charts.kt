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
 * A dependency-free daily bar chart (the cash-flow timeline). Each value is one
 * day; bar height is proportional to the largest day so the shape reads as a
 * spending rhythm rather than absolute figures. Zero-days show as a faint track.
 */
@Composable
fun MiniBarChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    barColor: Color = LedgerPalette.Spend,
    trackColor: Color = LedgerPalette.SurfaceSunken,
) {
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier) {
        val n = values.size.coerceAtLeast(1)
        val gap = 3.dp.toPx()
        val barW = ((size.width - gap * (n - 1)) / n).coerceAtLeast(1f)
        val radius = CornerRadius(barW / 2f, barW / 2f)
        values.forEachIndexed { i, v ->
            val x = i * (barW + gap)
            // Faint full-height track so sparse months still read as a timeline.
            drawRoundRect(
                color = trackColor,
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

/** Hoisted so every bar and badge shares one shape instance. */
private val BarShape = RoundedCornerShape(4.dp)
private val BadgeShape = RoundedCornerShape(8.dp)

/**
 * A thin proportional track for the top-spending list. [fraction] is clamped to
 * 0..1; the fill uses the category accent at the mock's 72% opacity.
 *
 * Both layers draw their own rounded rect rather than clipping to one, so a list
 * of these doesn't promote a graphics layer per row.
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
 * A ring chart — the breakdown's shape at a glance, with the total sitting in the
 * hole where it can be read against the slices rather than beside them.
 *
 * Drawn as arcs on a Canvas rather than pulled from a chart library: it keeps the
 * app dependency-lean and offline, which is the whole premise.
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

            // A faint full ring underneath, so an empty month still reads as a
            // chart rather than as a rendering failure.
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = ring)

            val total = slices.sumOf { it.value }.coerceAtLeast(1L)
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value.toFloat() / total)
                if (sweep > 0f) {
                    // A hair of background between wedges keeps neighbouring
                    // accents from reading as one block.
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

/** Degrees of background left between wedges, and the floor that keeps a tiny
 *  slice visible instead of vanishing into the gap. */
private const val SLICE_GAP = 1.6f
private const val MIN_SWEEP = 0.8f

/**
 * Small pill showing the month-over-month spend change. Spending *more* is red
 * (terracotta), spending *less* is green — the direction the user cares about,
 * not raw arithmetic sign.
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
