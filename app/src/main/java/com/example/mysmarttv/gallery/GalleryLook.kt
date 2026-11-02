package com.example.mysmarttv.gallery

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * What makes each gallery channel its own: its words and colors. The layout is shared.
 * The colors stay the same in light and dark mode, like the News Channel's.
 */
class GalleryLook(
    val channelName: String,
    // The label over the day's featured piece, like "Artwork of the Day".
    val featuredLabel: String,
    // The band over the list of sections, like "Select a Gallery".
    val chooseLabel: String,
    // Shown in place of the featured piece's title until it loads.
    val opening: String,
    // The page background, the top and bottom bars, the rim along the bars' inner edge, and the lines between panels.
    val wall: Brush,
    val barFill: Brush,
    val barEdge: Color,
    val barDivider: Color,
    // The band over the section list, fading out to the right.
    val band: Brush,
    // Labels and the line under titles.
    val accent: Color,
    // The dark mat each picture sits on, and the dashboard card's color while its picture loads.
    val mat: Color,
    val cardColor: Color,
    // The dashboard card's picture, from res/drawable. Without one, the card shows the day's featured piece.
    @DrawableRes val banner: Int? = null,
    // Whether the card writes the channel's name across the top. Off when the banner has the name in it already.
    val cardTitle: Boolean = true,
    // Whether the day's featured piece hangs in a frame in the middle of the banner, like on a gallery wall.
    val hangFeatured: Boolean = false,
) {
    val underline = Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.6f), Color.Transparent))
}
