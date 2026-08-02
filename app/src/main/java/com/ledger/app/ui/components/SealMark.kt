package com.ledger.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * The ₵ at the heart of the Ledger seal, as a [Path].
 *
 * This is the *same geometry* as `res/drawable/ic_launcher_foreground.xml`, and
 * that is the point. The splash previously drew the glyph as `Text("₵")`, which
 * resolves to whatever the platform serif happens to be — so the mark's weight,
 * its slant, and its position inside the ring all changed by device, and the
 * glyph sat visibly off-centre because a text line box reserves descender space
 * the character never uses. Drawing the path centres it by construction: the
 * outline's bounding box is symmetric about the design's (50, 48.6) by
 * definition, so [centre] is exactly the centre.
 *
 * Authored in the design's 100-unit glyph space; [unit] is one design unit in
 * pixels, so passing `size.minDimension / 100f` puts the ₵ at the same size
 * relative to the ring as the launcher icon does.
 *
 * The two sub-paths are wound the same way (both counter-clockwise) while the
 * bowl's counter is wound against them. Under the default non-zero fill that
 * makes the counter a hole and the bowl/stroke overlap solid — the opposite
 * pairing would punch a hole exactly where the stroke crosses the bowl.
 */
fun cediPath(unit: Float, centre: Offset): Path {
    fun x(v: Float) = centre.x + (v - DESIGN_CX) * unit
    fun y(v: Float) = centre.y + (v - DESIGN_CY) * unit
    fun oval(rx: Float, ry: Float) =
        Rect(x(DESIGN_CX - rx), y(DESIGN_CY - ry), x(DESIGN_CX + rx), y(DESIGN_CY + ry))

    return Path().apply {
        // The bowl: outer arc the long way round (counter-clockwise, leaving the
        // aperture on the right), then the counter back the long way clockwise.
        moveTo(x(58.948f), y(34.674f))
        arcTo(oval(16f, 16.8f), -56f, -248f, forceMoveTo = false)
        lineTo(x(58.35f), y(54.471f))
        arcTo(oval(9.6f, 11.9f), 29.6f, 300.8f, forceMoveTo = false)
        close()

        // The stroke: a parallelogram leaning 14° off vertical, overhanging the
        // bowl top and bottom.
        moveTo(x(51.99f), y(29.4f))
        lineTo(x(42.41f), y(67.8f))
        lineTo(x(48.01f), y(67.8f))
        lineTo(x(57.59f), y(29.4f))
        close()
    }
}

/** The design's glyph-space centre — every number in [cediPath] is relative to it. */
private const val DESIGN_CX = 50f
private const val DESIGN_CY = 48.6f
