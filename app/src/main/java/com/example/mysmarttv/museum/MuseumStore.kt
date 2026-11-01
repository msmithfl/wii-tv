package com.example.mysmarttv.museum

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.time.Duration.Companion.minutes

// How often to check whether it's a new day, and how soon to try again when the museum couldn't be reached.
private val CHECK_EVERY = 30.minutes
private val RETRY_AFTER = 2.minutes

/**
 * The Museum Channel's galleries. Each changes once a day, so it shows the same pieces all day. Today's Gallery
 * loads when the app starts, for the dashboard card; the others load the first time they're opened each day.
 * Its state is Compose state, so the card and screens update on their own.
 */
class MuseumStore {
    var galleries by mutableStateOf<Map<MuseumSection, List<Artwork>>>(emptyMap())
        private set
    // Galleries that couldn't be loaded on their last try.
    var failed by mutableStateOf<Set<MuseumSection>>(emptySet())
        private set

    // The day the galleries above are for. A new day clears them.
    private var day = museumDay()

    /** The day's first piece, shown on the dashboard card. */
    val artworkOfTheDay: Artwork? get() = galleries[MuseumSection.Today]?.firstOrNull()

    /** Loads Today's Gallery now and again each new day, for as long as the caller keeps it running. */
    suspend fun keepFresh() {
        while (true) {
            load(MuseumSection.Today)
            delay(if (MuseumSection.Today in galleries) CHECK_EVERY else RETRY_AFTER)
        }
    }

    /** Loads a gallery, unless it's already loaded today. */
    suspend fun load(section: MuseumSection) {
        if (day != museumDay()) {
            day = museumDay()
            galleries = emptyMap()
        }
        if (section in galleries) return
        try {
            val pieces = MuseumClient.gallery(section, seed = day.toEpochDay())
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

private fun museumDay(): LocalDate = LocalDateTime.now().minusHours(NEW_DAY_HOUR).toLocalDate()
