package com.example.mysmarttv.science

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.mysmarttv.R
import com.example.mysmarttv.gallery.GalleryLook
import com.example.mysmarttv.gallery.GalleryStore

/** The Science Channel: cool grey-blue walls and deep space-blue bars with a sky-blue rim. */
val ScienceLook = GalleryLook(
    channelName = "Science Channel",
    featuredLabel = "Picture of the Day",
    chooseLabel = "Select a Topic",
    opening = "Looking up…",
    wall = Brush.verticalGradient(listOf(Color(0xFFE7ECF2), Color(0xFFCBD5E0))),
    barFill = Brush.verticalGradient(listOf(Color(0xFF0A1428), Color(0xFF16284D), Color(0xFF243F73))),
    barEdge = Color(0xFF7FD0F0),
    barDivider = Color(0xFF3F5E8C),
    band = Brush.horizontalGradient(listOf(Color(0xFF14284F), Color(0xFF22407A), Color(0x0022407A))),
    accent = Color(0xFF2F7DB5),
    mat = Color(0xFF070B14),
    cardColor = Color(0xFF0E1B38),
    banner = R.drawable.science_app,
    cardTitle = false,
)

/** The Science Channel's topics, from NASA. */
fun scienceStore() = GalleryStore(ScienceSection.entries) { section, seed ->
    NasaClient.topic(section as ScienceSection, seed)
}
