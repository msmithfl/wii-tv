package com.example.mysmarttv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/** The background with faint horizontal lines used on every screen. */
@Composable
fun Modifier.wiiBackdrop(): Modifier {
    val stripe = WiiStripe
    return this
        .background(WiiBackground)
        .drawBehind {
            val step = 6.dp.toPx()
            var y = 0f
            while (y < size.height) {
                drawLine(stripe, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                y += step
            }
        }
}
