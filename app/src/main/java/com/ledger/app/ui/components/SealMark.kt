package com.ledger.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path

/**
 * The ₵ in the middle of the Ledger seal, as a [Path].
 *
 * It uses the same shape as `res/drawable/ic_launcher_foreground.xml`, so the
 * splash matches the launcher icon. Drawing it as text would depend on the
 * device's fonts and wouldn't sit centred. The shape is symmetric about the
 * design's point (50, 48.6), so [centre] is exactly its centre.
 *
 * The numbers are in the design's 100-unit box. [unit] is one design unit in
 * pixels, so passing `size.minDimension / 100f` sizes the ₵ against the ring
 * the same way the launcher icon does.
 *
 * The two outer shapes are drawn in the same direction and the bowl's inner
 * curve in the opposite one. With the default fill rule, that makes the inner
 * curve a hole while the bowl and the stroke overlap as solid.
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

/** The centre of the design's box. Every number in [cediPath] is relative to it. */
private const val DESIGN_CX = 50f
private const val DESIGN_CY = 48.6f
