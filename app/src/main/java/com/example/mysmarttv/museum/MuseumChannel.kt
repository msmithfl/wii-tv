package com.example.mysmarttv.museum

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.mysmarttv.R
import com.example.mysmarttv.gallery.GalleryLook
import com.example.mysmarttv.gallery.GalleryStore

/** The Museum Channel: cream gallery walls and deep red bars with a gold rim. */
val MuseumLook = GalleryLook(
    channelName = "Museum Channel",
    featuredLabel = "Artwork of the Day",
    chooseLabel = "Select a Gallery",
    opening = "Opening the museum…",
    wall = Brush.verticalGradient(listOf(Color(0xFFEFE9DF), Color(0xFFD9D0C2))),
    barFill = Brush.verticalGradient(listOf(Color(0xFF2A1216), Color(0xFF471D24), Color(0xFF66303A))),
    barEdge = Color(0xFFD2B06A),
    barDivider = Color(0xFF8A5A50),
    band = Brush.horizontalGradient(listOf(Color(0xFF5A222B), Color(0xFF7A3440), Color(0x007A3440))),
    accent = Color(0xFFB8913F),
    mat = Color(0xFF2B2624),
    cardColor = Color(0xFF4A1E25),
    banner = R.drawable.museum_app,
    hangFeatured = true,
)

/** The Museum Channel's galleries, from the Met. */
fun museumStore() = GalleryStore(MuseumSection.entries) { section, seed ->
    MuseumClient.gallery(section as MuseumSection, seed)
}
