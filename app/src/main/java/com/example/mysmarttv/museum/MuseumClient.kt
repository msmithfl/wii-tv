package com.example.mysmarttv.museum

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
enum class MuseumSection(val title: String, val departmentId: Int?) {
    // Not a department of its own: a handful of highlights from across the museum, picked once a day.
    Today("Today's Gallery", null),
    EuropeanPaintings("European Paintings", 11),
    AsianArt("Asian Art", 6),
    EgyptianArt("Egyptian Art", 10),
    GreekAndRoman("Greek and Roman Art", 13),
    ArmsAndArmor("Arms and Armor", 4),
    MusicalInstruments("Musical Instruments", 18),
}

data class Artwork(
    val id: Int,
    val title: String,
    val artist: String,
    // Where the artist was from and when they lived, like "Dutch, Zundert 1853–1890 Auvers-sur-Oise".
    val artistBio: String,
    val date: String,
    val medium: String,
    val dimensions: String,
    val culture: String,
    val department: String,
    // The full-size picture, and a smaller one (about 600 wide) for lists.
    val imageUrl: String,
    val smallImageUrl: String,
    // Which of the Met's galleries it hangs in, or empty if it isn't on display.
    val gallery: String,
    val creditLine: String,
)

// How many pieces a gallery shows, and how many the day's gallery picks.
private const val GALLERY_SIZE = 24
private const val TODAY_SIZE = 6

/** Reads the Metropolitan Museum of Art's collection. It's free and needs no account or key. */
object MuseumClient {
    private const val BASE = "https://collectionapi.metmuseum.org/public/collection"

    // Looking up many pieces at once is fine, but not all at the same moment.
    private val lookups = Semaphore(6)

    /**
     * A gallery's pieces. [seed] picks which stretch of the department's highlights is shown, so the same
     * seed gives the same pieces; it changes each day for a fresh gallery.
     */
    suspend fun gallery(section: MuseumSection, seed: Long): List<Artwork> {
        val size = if (section == MuseumSection.Today) TODAY_SIZE else GALLERY_SIZE
        // Asks for extra, since now and then one turns out to have no picture the Met can share.
        val ids = searchHighlights(section.departmentId, count = size * 2, seed = seed + section.ordinal)
        return artworks(ids).take(size)
    }

    /** One piece, or nothing if it has no picture the Met can share. */
    suspend fun artwork(id: Int): Artwork? = withContext(Dispatchers.IO) {
        try {
            toArtwork(id, JSONObject(read("$BASE/v1/objects/$id")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** IDs of highlights from [departmentId] (or anywhere, if null), a run of [count] starting at a spot [seed] picks. */
    suspend fun searchHighlights(departmentId: Int?, count: Int, seed: Long): List<Int> = withContext(Dispatchers.IO) {
        val department = departmentId?.let { "&departmentId=$it" }.orEmpty()
        val base = "$BASE/v1.1/search?hasImages=true&isHighlight=true$department&q=${URLEncoder.encode("*", "UTF-8")}"
        val total = JSONObject(read("$base&limit=1")).optInt("total")
        val offset = Random(seed).nextInt(maxOf(1, total - count))
        val ids = JSONObject(read("$base&limit=$count&offset=$offset")).getJSONArray("objectIDs")
        List(ids.length()) { ids.getInt(it) }
    }

    // Looks the pieces up side by side, keeping their order and dropping any without a picture.
    private suspend fun artworks(ids: List<Int>): List<Artwork> = coroutineScope {
        ids.map { id -> async { lookups.withPermit { artwork(id) } } }.awaitAll().filterNotNull()
    }

    // Pieces the Met can't share freely come without a picture link, and are left out.
    private fun toArtwork(id: Int, json: JSONObject): Artwork? {
        val imageUrl = json.optString("primaryImage").ifEmpty { return null }
        return Artwork(
            id = id,
            title = json.optString("title").ifEmpty { return null },
            artist = json.optString("artistDisplayName"),
            artistBio = json.optString("artistDisplayBio"),
            date = json.optString("objectDate"),
            medium = json.optString("medium"),
            dimensions = json.optString("dimensions"),
            culture = json.optString("culture"),
            department = json.optString("department"),
            imageUrl = imageUrl,
            smallImageUrl = json.optString("primaryImageSmall").ifEmpty { imageUrl },
            gallery = json.optString("GalleryNumber"),
            creditLine = json.optString("creditLine"),
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

/** Who made it, for a caption: the artist, or the culture it came from when the artist isn't known. */
val Artwork.maker: String get() = artist.ifEmpty { culture }
