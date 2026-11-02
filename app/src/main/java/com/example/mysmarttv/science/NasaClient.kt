package com.example.mysmarttv.science

import android.text.Html
import com.example.mysmarttv.gallery.Exhibit
import com.example.mysmarttv.gallery.GallerySection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * The Science Channel's topics, in the order they're listed, each with what it searches NASA's image library for.
 * A topic with several searches uses a different one each day.
 */
enum class ScienceSection(override val title: String, val searches: List<String>) : GallerySection {
    // Not a topic of its own: one picture from each of these searches, picked once a day.
    Today("Today's Pictures", listOf("galaxy", "nebula", "saturn", "jupiter", "earth from space", "solar dynamics observatory")),
    Galaxies("Galaxies", listOf("galaxy")),
    Nebulae("Nebulae", listOf("nebula")),
    Planets("The Planets", listOf("saturn", "jupiter", "mars surface", "neptune")),
    Earth("Earth from Space", listOf("earth from space", "aurora")),
    Sun("The Sun", listOf("solar dynamics observatory")),
    Astronauts("Astronauts", listOf("spacewalk")),
    Launches("Launches", listOf("rocket launch")),
}

// How many pictures a topic shows.
private const val TOPIC_SIZE = 24

// Pictures smaller than this across are left out; they'd look blurry on the TV.
private const val MIN_WIDTH = 1000

// Pictures are shrunk to about the TV's width anyway, and larger files than this are skipped; some originals
// are hundreds of megabytes.
private const val SCREEN_WIDTH = 1920
private const val MAX_FILE_BYTES = 15_000_000

// Wide pictures, for the screensaver, which fills the TV with them.
private const val WIDE_ASPECT = 1.3f

// The screensaver's caption is cut after the sentence that passes this length, so it doesn't cover the picture.
private const val SUMMARY_LENGTH = 300

private val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy")

/** Reads NASA's image library: its photos and pictures, each with a write-up. It's free and needs no key. */
object NasaClient {

    /** A topic's pictures. The same [seed] gives the same pictures; it changes each day for fresh ones. */
    suspend fun topic(section: ScienceSection, seed: Long): List<Exhibit> {
        val random = Random(seed)
        return if (section == ScienceSection.Today) {
            // The first picture from each search that isn't already picked, since one can turn up in two searches.
            section.searches.fold(listOf()) { picked, query ->
                val next = search(query, page = random.nextInt(1, 4)).shuffled(random)
                    .firstOrNull { candidate -> picked.none { it.id == candidate.id } }
                if (next != null) picked + next else picked
            }
        } else {
            search(section.searches[random.nextInt(section.searches.size)], page = random.nextInt(1, 4))
                .shuffled(random)
                .take(TOPIC_SIZE)
        }
    }

    /**
     * One page of pictures for [query], skipping any too small for the TV. With [wideOnly], only pictures much
     * wider than they are tall. A failed search or a page past the end gives nothing.
     */
    suspend fun search(query: String, page: Int, wideOnly: Boolean = false): List<Exhibit> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val json = JSONObject(read("https://images-api.nasa.gov/search?q=$encoded&media_type=image&page_size=50&page=$page"))
            val items = json.getJSONObject("collection").getJSONArray("items")
            List(items.length()) { items.getJSONObject(it) }
                .mapNotNull { toExhibit(it, wideOnly) }
                // NASA often posts the same picture more than once.
                .distinctBy { it.title }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Each item lists the same picture at several sizes. The full one is the smallest that still fills the TV,
    // and the small one, for lists, is the smallest at least 600 across.
    private fun toExhibit(item: JSONObject, wideOnly: Boolean): Exhibit? {
        val data = item.optJSONArray("data")?.optJSONObject(0) ?: return null
        val links = item.optJSONArray("links") ?: return null
        val sizes = List(links.length()) { links.getJSONObject(it) }
            .filter { it.optString("render") == "image" && it.optInt("width") > 0 && it.optInt("height") > 0 }
            .filter { it.optString("href").let { href -> href.endsWith(".jpg") || href.endsWith(".png") } }
            .filter { it.optInt("size") <= MAX_FILE_BYTES }
        val widest = sizes.maxByOrNull { it.optInt("width") } ?: return null
        if (widest.optInt("width") < MIN_WIDTH) return null
        if (wideOnly && widest.optInt("width") < widest.optInt("height") * WIDE_ASPECT) return null
        val full = sizes.filter { it.optInt("width") >= SCREEN_WIDTH }.minByOrNull { it.optInt("width") } ?: widest
        val small = sizes.filter { it.optInt("width") >= 600 }.minByOrNull { it.optInt("width") } ?: widest

        val description = plainText(data.optString("description"))
        val credit = plainText(data.optString("secondary_creator").ifEmpty { data.optString("photographer") })
        val keywords = data.optJSONArray("keywords")?.let { list -> List(list.length()) { list.optString(it) } }.orEmpty()
        return Exhibit(
            id = data.optString("nasa_id"),
            title = plainText(data.optString("title")).ifEmpty { return null },
            maker = credit.ifEmpty { "NASA" },
            makerDetail = "",
            imageUrl = full.getString("href"),
            smallImageUrl = small.getString("href"),
            facts = listOf(
                "Date" to data.optString("date_created").let { date ->
                    runCatching { ZonedDateTime.parse(date).format(dateFormat) }.getOrDefault("")
                },
                "From" to centerName(data.optString("center")),
                "NASA ID" to data.optString("nasa_id"),
            ),
            description = description,
            summary = shorten(description),
            footnote = keywords.filter { it.isNotBlank() }.take(6).joinToString(" · "),
        )
    }

    // NASA lists where a picture came from by a short code.
    private fun centerName(code: String): String = when (code) {
        "JPL" -> "Jet Propulsion Laboratory"
        "GSFC" -> "Goddard Space Flight Center"
        "JSC" -> "Johnson Space Center"
        "KSC" -> "Kennedy Space Center"
        "MSFC" -> "Marshall Space Flight Center"
        "ARC" -> "Ames Research Center"
        "LRC" -> "Langley Research Center"
        "GRC" -> "Glenn Research Center"
        "AFRC" -> "Armstrong Flight Research Center"
        "SSC" -> "Stennis Space Center"
        "HQ" -> "NASA Headquarters"
        else -> code
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

    // Descriptions sometimes carry HTML tags or entities.
    private fun plainText(text: String): String =
        Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    private fun shorten(text: String): String {
        if (text.length <= SUMMARY_LENGTH) return text
        val end = Regex("[.!?](\\s|$)").find(text, SUMMARY_LENGTH)?.range?.first ?: return text
        return text.substring(0, end + 1)
    }
}
