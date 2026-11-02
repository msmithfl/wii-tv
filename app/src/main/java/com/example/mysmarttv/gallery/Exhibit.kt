package com.example.mysmarttv.gallery

/**
 * One piece in a gallery channel, like a painting in the Museum Channel or a space picture in the Science Channel.
 * Each source fills in what it knows; anything empty is left off the page.
 */
data class Exhibit(
    val id: String,
    val title: String,
    // Who made it, like an artist or a NASA team, and a line more about them.
    val maker: String,
    val makerDetail: String,
    // The full-size picture, and a smaller one for lists and the dashboard card.
    val imageUrl: String,
    val smallImageUrl: String,
    // Labelled details for the page, like "Date" to "1889", in the order they're shown.
    val facts: List<Pair<String, String>>,
    // A longer write-up, when the source has one.
    val description: String,
    // A line or two for the screensaver's caption.
    val summary: String,
    // Small print at the bottom of the page, like who gave it to the museum.
    val footnote: String,
)

/** A section of a gallery channel. The first section of each channel is its daily picks. */
interface GallerySection {
    val title: String
}
