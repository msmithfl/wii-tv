package com.example.mysmarttv.museum

import com.example.mysmarttv.gallery.Exhibit
import com.example.mysmarttv.gallery.GallerySection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.random.Random

/**
 * The Museum Channel's galleries, in the order they're listed. Each is one of the Met's departments, showing only
 * the pieces the museum marks as highlights: its best-known works.
 */
enum class MuseumSection(override val title: String, val departmentId: Int?) : GallerySection {
    // Not a department of its own: a handful of highlights from across the museum, picked once a day.
    Today("Today's Gallery", null),
    EuropeanPaintings("European Paintings", 11),
    AsianArt("Asian Art", 6),
    EgyptianArt("Egyptian Art", 10),
    GreekAndRoman("Greek and Roman Art", 13),
    ArmsAndArmor("Arms and Armor", 4),
    MusicalInstruments("Musical Instruments", 18),
}

// How many pieces a gallery shows, and how many the day's gallery picks.
private const val GALLERY_SIZE = 24
private const val TODAY_SIZE = 6

/** Reads the Metropolitan Museum of Art's collection. It's free and needs no account or key. */
object MuseumClient {
    private const val BASE = "https://collectionapi.metmuseum.org/public/collection"

    // Looking up many pieces at once is fine, but not all at the same moment.
    private val lookups = Semaphore(6)

    /** A gallery's pieces. The same [seed] gives the same pieces; it changes each day for a fresh gallery. */
    suspend fun gallery(section: MuseumSection, seed: Long): List<Exhibit> {
        val size = if (section == MuseumSection.Today) TODAY_SIZE else GALLERY_SIZE
        // Asks for extra, since now and then one turns out to have no picture the Met can share.
        val ids = searchHighlights(section.departmentId, count = size * 2, seed = seed)
        return exhibits(ids).take(size)
    }

    /** One piece, or nothing if it has no picture the Met can share. */
    suspend fun exhibit(id: Int): Exhibit? = withContext(Dispatchers.IO) {
        try {
            toExhibit(id, JSONObject(read("$BASE/v1/objects/$id")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // IDs of highlights from [departmentId] (or anywhere, if null), a run of [count] starting at a spot [seed] picks.
    private suspend fun searchHighlights(departmentId: Int?, count: Int, seed: Long): List<Int> = withContext(Dispatchers.IO) {
        val department = departmentId?.let { "&departmentId=$it" }.orEmpty()
        val base = "$BASE/v1.1/search?hasImages=true&isHighlight=true$department&q=${URLEncoder.encode("*", "UTF-8")}"
        val total = JSONObject(read("$base&limit=1")).optInt("total")
        val offset = Random(seed).nextInt(maxOf(1, total - count))
        val ids = JSONObject(read("$base&limit=$count&offset=$offset")).getJSONArray("objectIDs")
        List(ids.length()) { ids.getInt(it) }
    }

    // Looks the pieces up side by side, keeping their order and dropping any without a picture.
    private suspend fun exhibits(ids: List<Int>): List<Exhibit> = coroutineScope {
        ids.map { id -> async { lookups.withPermit { exhibit(id) } } }.awaitAll().filterNotNull()
    }

    // Pieces the Met can't share freely come without a picture link, and are left out.
    // The Met doesn't write descriptions, so the label is all facts.
    private fun toExhibit(id: Int, json: JSONObject): Exhibit? {
        val imageUrl = json.optString("primaryImage").ifEmpty { return null }
        val artist = json.optString("artistDisplayName")
        val bio = json.optString("artistDisplayBio")
        val culture = json.optString("culture")
        val date = json.optString("objectDate")
        val medium = json.optString("medium")
        val gallery = json.optString("GalleryNumber")
        // The culture stands in as the maker when the artist isn't known, so it's only listed when there is one.
        val maker = artist.ifEmpty { culture }
        return Exhibit(
            id = id.toString(),
            title = json.optString("title").ifEmpty { return null },
            maker = maker,
            makerDetail = bio,
            imageUrl = imageUrl,
            smallImageUrl = json.optString("primaryImageSmall").ifEmpty { imageUrl },
            facts = listOf(
                "Date" to date,
                "Medium" to medium,
                "Size" to json.optString("dimensions"),
                "Culture" to if (artist.isNotEmpty()) culture else "",
                "Department" to json.optString("department"),
                "On view" to if (gallery.isNotEmpty()) "Gallery $gallery, The Met" else "Not on display right now",
            ),
            description = "",
            summary = listOf(
                if (artist.isNotEmpty() && bio.isNotEmpty()) "$artist ($bio)" else maker,
                listOf(date, medium).filter { it.isNotEmpty() }.joinToString(" · "),
            ).filter { it.isNotEmpty() }.joinToString("\n"),
            footnote = json.optString("creditLine"),
        )
    }

    private fun read(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
