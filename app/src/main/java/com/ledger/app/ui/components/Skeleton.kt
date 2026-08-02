package com.ledger.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ledger.app.ui.theme.LedgerPalette

/**
 * Wireframe placeholders shown while the encrypted database is still opening.
 *
 * A cold SQLCipher open plus the first query takes long enough to notice —
 * especially straight after an SMS backfill, where the ledger is suddenly large.
 * Rendering the shape of the screen that is coming reads as loading; the
 * alternatives both lie. A bare greeting looks like a hang, and an empty state
 * ("All caught up", "Welcome to Ledger") actively contradicts what is about to
 * appear a frame later.
 *
 * The whole block breathes as one, rather than each bar animating on its own
 * schedule — one animation to drive, and it reads as a single surface settling.
 */
@Composable
private fun skeletonAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "alpha",
    )
    return alpha
}

/** One placeholder bar. [widthFraction] lets a block of them look like prose. */
@Composable
fun SkeletonBar(
    widthFraction: Float = 1f,
    height: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(LedgerPalette.SurfaceSunken, RoundedCornerShape(6.dp)),
    )
}

/** The dashboard's shape: two stat cards, a summary, a chart, then a few rows. */
@Composable
fun OverviewSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.alpha(skeletonAlpha()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SkeletonCard(Modifier.weight(1f), height = 92.dp)
            SkeletonCard(Modifier.weight(1f), height = 92.dp)
        }
        SkeletonCard(height = 56.dp)
        SkeletonCard(height = 128.dp)
        Spacer(Modifier.height(2.dp))
        SkeletonBar(0.4f, 20.dp)
        repeat(3) { SkeletonRow() }
    }
}

/** The queue's shape: a stack of tall review cards. */
@Composable
fun ReviewSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.alpha(skeletonAlpha()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(2) { SkeletonCard(height = 180.dp) }
    }
}

/** The history list's shape: a day header, then rows, twice over. */
@Composable
fun HistorySkeleton(modifier: Modifier = Modifier) {
    Column(modifier.alpha(skeletonAlpha()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(2) {
            SkeletonBar(0.28f, 12.dp, Modifier.padding(top = 6.dp, bottom = 2.dp))
            repeat(3) { SkeletonRow() }
        }
    }
}

/** The breakdown's shape: the ring panel, then a stack of legend rows. */
@Composable
fun BreakdownSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.alpha(skeletonAlpha()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SkeletonCard(height = 236.dp)
        repeat(5) { SkeletonBar(1f, 46.dp) }
    }
}

/** A blank card of a given height, for the larger dashboard panels. */
@Composable
private fun SkeletonCard(modifier: Modifier = Modifier, height: Dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(LedgerPalette.Surface, RoundedCornerShape(16.dp)),
    )
}

/** A transaction row's shape: monogram, two lines of text, an amount. */
@Composable
private fun SkeletonRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(LedgerPalette.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).background(LedgerPalette.SurfaceSunken, CircleShape))
        Column(
            Modifier.weight(1f).padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SkeletonBar(0.55f, 13.dp)
            SkeletonBar(0.35f, 10.dp)
        }
        SkeletonBar(height = 15.dp, modifier = Modifier.width(64.dp))
    }
}
