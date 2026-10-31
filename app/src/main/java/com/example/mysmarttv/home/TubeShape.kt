package com.example.mysmarttv.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.hypot

/**
 * An old tube TV screen, like the Wii's channel cards: rounded corners, with every edge bowing gently outward
 * so the card looks slightly puffed out. The middle of each edge touches the card's bounds; the ends sit a
 * little inside them.
 *
 * [topBow] and [sideBow] are how far the edges bow, as a share of the card's height and width.
 * [corner] is the corner size, as a share of the card's height.
 */
class TubeShape(
    private val topBow: Float = 0.012f,
    private val sideBow: Float = 0.005f,
    private val corner: Float = 0.2f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val b = h * topBow
        val s = w * sideBow
        val r = h * corner

        // Each edge is a curve that starts and ends just inside the bounds and reaches them in the middle.
        // A control point twice as far out as the ends are in puts the curve's midpoint right on the bound.
        val topStart = Offset(r, b)
        val topControl = Offset(w / 2, -b)
        val topEnd = Offset(w - r, b)
        val rightStart = Offset(w - s, r)
        val rightControl = Offset(w + s, h / 2)
        val rightEnd = Offset(w - s, h - r)
        val bottomStart = Offset(w - r, h - b)
        val bottomControl = Offset(w / 2, h + b)
        val bottomEnd = Offset(r, h - b)
        val leftStart = Offset(s, h - r)
        val leftControl = Offset(-s, h / 2)
        val leftEnd = Offset(s, r)

        val path = Path().apply {
            moveTo(topStart.x, topStart.y)
            quadraticTo(topControl.x, topControl.y, topEnd.x, topEnd.y)
            corner(topEnd, topControl, rightStart, rightControl, r)
            quadraticTo(rightControl.x, rightControl.y, rightEnd.x, rightEnd.y)
            corner(rightEnd, rightControl, bottomStart, bottomControl, r)
            quadraticTo(bottomControl.x, bottomControl.y, bottomEnd.x, bottomEnd.y)
            corner(bottomEnd, bottomControl, leftStart, leftControl, r)
            quadraticTo(leftControl.x, leftControl.y, leftEnd.x, leftEnd.y)
            corner(leftEnd, leftControl, topStart, topControl, r)
            close()
        }
        return Outline.Generic(path)
    }

    // A rounded corner from the end of one edge to the start of the next. It leaves each edge heading the same
    // way the edge was going, so the outline stays smooth with no kink where they meet.
    private fun Path.corner(from: Offset, fromControl: Offset, to: Offset, toControl: Offset, radius: Float) {
        val pull = radius * 0.55f
        val out = (from - fromControl).normalized() * pull
        val into = (toControl - to).normalized() * pull
        cubicTo(from.x + out.x, from.y + out.y, to.x - into.x, to.y - into.y, to.x, to.y)
    }

    private fun Offset.normalized(): Offset {
        val length = hypot(x, y)
        return if (length == 0f) this else this / length
    }
}
