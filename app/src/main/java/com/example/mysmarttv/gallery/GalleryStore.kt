package com.example.mysmarttv.gallery

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

// How often to check whether it's a new day, and how soon to try again when the source couldn't be reached.
private val CHECK_EVERY = 30.minutes
private val RETRY_AFTER = 2.minutes

/**
 * A gallery channel's sections. Each changes once a day, so it shows the same pieces all day. The first section,
 * the daily picks, loads when the app starts, for the dashboard card; the others load the first time they're
 * opened each day. [fetch] gets a section's pieces; the same seed should give the same pieces.
 * Its state is Compose state, so the card and screens update on their own.
 */
class GalleryStore(
    val sections: List<GallerySection>,
    private val fetch: suspend (section: GallerySection, seed: Long) -> List<Exhibit>,
) {
    var galleries by mutableStateOf<Map<GallerySection, List<Exhibit>>>(emptyMap())
        private set
    // Sections that couldn't be loaded on their last try.
    var failed by mutableStateOf<Set<GallerySection>>(emptySet())
        private set

    // The day the sections above are for. A new day clears them.
    private var day = galleryDay()

    private val today get() = sections.first()

    /** The day's first piece, shown on the dashboard card and at the top of the channel. */
    val featured: Exhibit? get() = galleries[today]?.firstOrNull()

    /** Loads the daily picks now and again each new day, for as long as the caller keeps it running. */
    suspend fun keepFresh() {
        while (true) {
            load(today)
            delay(if (today in galleries) CHECK_EVERY else RETRY_AFTER)
        }
    }

    /** Loads a section, unless it's already loaded today. */
    suspend fun load(section: GallerySection) {
        if (day != galleryDay()) {
            day = galleryDay()
            galleries = emptyMap()
        }
        if (section in galleries) return
        try {
            val pieces = fetch(section, day.toEpochDay() * 31 + sections.indexOf(section))
            if (pieces.isEmpty()) throw IllegalStateException("No pieces")
            galleries = galleries + (section to pieces)
            failed = failed - section
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = failed + section
        }
    }
}

// Like the News Channel's headlines, a new day's galleries start in the morning, not at midnight.
private const val NEW_DAY_HOUR = 6L

private fun galleryDay(): LocalDate = LocalDateTime.now().minusHours(NEW_DAY_HOUR).toLocalDate()
