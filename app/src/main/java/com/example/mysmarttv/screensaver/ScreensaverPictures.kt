package com.example.mysmarttv.screensaver

import android.graphics.BitmapFactory
import android.text.Html
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.example.mysmarttv.museum.Artwork
import com.example.mysmarttv.museum.MuseumClient
import com.example.mysmarttv.museum.maker
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Where the screensaver's pictures come from. Chosen in Settings. */
enum class PictureSource(val label: String) {
    Nasa("NASA"),
    Met("Met Museum"),
    Both("NASA + Met Museum"),
}

// What the space pictures are searched for in NASA's image library. A couple are picked at random each time it runs low.
private val NASA_TOPICS = listOf(
    "nebula", "galaxy", "hubble", "james webb", "star cluster", "milky way", "saturn", "jupiter",
    "mars surface", "earth from space", "aurora from space", "supernova", "moon surface", "solar flare",
)

// Where the Met's paintings are searched for, as its department number and a search word. One is picked at random
// each time it runs low: European Paintings, the American Wing, and Asian Art.
private val MET_SEARCHES = listOf(11 to "painting", 11 to "landscape", 1 to "painting", 6 to "landscape")

// Wide space pictures only, so they fill the TV without cutting off much.
private const val MIN_ASPECT = 1.3f

// Pictures are shrunk to about the TV's width, so a huge original doesn't take up too much memory.
private const val SCREEN_WIDTH = 1920

// Larger NASA files than this are skipped; some originals are hundreds of megabytes.
private const val MAX_FILE_BYTES = 15_000_000

// Descriptions are cut after the sentence that passes this length, so a long one doesn't cover the picture.
private const val DESCRIPTION_LENGTH = 300

/**
 * A picture ready to show. [fillScreen] crops it to fill the TV; otherwise it's shown whole, like a painting on a wall.
 * A painting from the Met carries its [artwork], so holding OK can open it in the Museum Channel.
 */
data class ScreensaverPicture(
    val title: String,
    val description: String,
    val image: ImageBitmap,
    val fillScreen: Boolean,
    val source: PictureSource,
    val artwork: Artwork? = null,
)

private data class Listing(val title: String, val description: String, val imageUrl: String)

/**
 * Pictures for the screensaver: space pictures from NASA's image library, paintings from the Metropolitan
 * Museum of Art, or both taking turns. Both are free and need no key. One picture is kept downloaded ahead,
 * so the screensaver can show it the moment it starts. [source] is read each time, so a change in Settings
 * takes effect with the next picture.
 */
class ScreensaverPictures(private val source: () -> PictureSource) {
    private val nasaWaiting = ArrayDeque<Listing>()
    // The Met's search only gives object numbers; each one's details are looked up when its turn comes.
    private val metWaiting = ArrayDeque<Int>()
    private var ready: ScreensaverPicture? = null

    /** The picture on screen right now, while the screensaver is showing. */
    var showing by mutableStateOf<ScreensaverPicture?>(null)
    // With both chosen, which one is next.
    private var nasaNext = true
    private val lock = Mutex()

    /** Downloads the next picture, if one isn't already waiting. */
    suspend fun prepare() = lock.withLock {
        // A picture downloaded before the setting changed is thrown out.
        if (ready?.let { allowed(it.source) } == false) ready = null
        if (ready == null) ready = load()
    }

    /** The picture downloaded by [prepare], or nothing if it couldn't get one. */
    fun take(): ScreensaverPicture? = ready.also { ready = null }

    private fun allowed(pictureSource: PictureSource) = source().let { it == PictureSource.Both || it == pictureSource }

    private suspend fun load(): ScreensaverPicture? {
        val useNasa = when (source()) {
            PictureSource.Nasa -> true
            PictureSource.Met -> false
            PictureSource.Both -> nasaNext.also { nasaNext = !nasaNext }
        }
        return if (useNasa) loadNasa() else loadMet()
    }

    // Tries a few in a row, in case a picture fails to download.
    private suspend fun loadNasa(): ScreensaverPicture? {
        repeat(5) {
            if (nasaWaiting.isEmpty()) nasaWaiting.addAll(searchNasa())
            val listing = nasaWaiting.removeFirstOrNull() ?: return null
            download(listing.imageUrl)?.let {
                return ScreensaverPicture(listing.title, listing.description, it, fillScreen = true, PictureSource.Nasa)
            }
        }
        return null
    }

    // More tries than NASA, since some of the Met's search results turn out to have no picture it can share.
    private suspend fun loadMet(): ScreensaverPicture? {
        repeat(8) {
            if (metWaiting.isEmpty()) metWaiting.addAll(searchMet())
            val id = metWaiting.removeFirstOrNull() ?: return null
            val artwork = MuseumClient.artwork(id) ?: return@repeat
            download(artwork.imageUrl)?.let {
                return ScreensaverPicture(artwork.title, metCaption(artwork), it, fillScreen = false, PictureSource.Met, artwork)
            }
        }
        return null
    }

    private suspend fun searchNasa(): List<Listing> = withContext(Dispatchers.IO) {
        NASA_TOPICS.shuffled().take(2).flatMap { topic ->
            orEmpty {
                val query = URLEncoder.encode(topic, "UTF-8")
                val page = (1..3).random()
                val json = JSONObject(read("https://images-api.nasa.gov/search?q=$query&media_type=image&page_size=50&page=$page"))
                val items = json.getJSONObject("collection").getJSONArray("items")
                List(items.length()) { items.getJSONObject(it) }.mapNotNull(::nasaListing)
            }
        }.shuffled()
    }

    // Each item lists the same picture at several sizes. Picks the smallest that still fills the TV.
    private fun nasaListing(item: JSONObject): Listing? {
        val data = item.optJSONArray("data")?.optJSONObject(0) ?: return null
        val links = item.optJSONArray("links") ?: return null
        val sizes = List(links.length()) { links.getJSONObject(it) }
            .filter { it.optString("render") == "image" && it.optInt("width") > 0 && it.optInt("height") > 0 }
            .filter { it.optString("href").let { href -> href.endsWith(".jpg") || href.endsWith(".png") } }
            .filter { it.optInt("size") <= MAX_FILE_BYTES }
        val widest = sizes.maxByOrNull { it.optInt("width") } ?: return null
        if (widest.optInt("width") < widest.optInt("height") * MIN_ASPECT) return null
        val chosen = sizes.filter { it.optInt("width") >= SCREEN_WIDTH }.minByOrNull { it.optInt("width") } ?: widest
        return Listing(
            title = plainText(data.optString("title")).ifEmpty { return null },
            description = shorten(plainText(data.optString("description"))),
            imageUrl = chosen.getString("href"),
        )
    }

    // A random stretch of a random search's results, since the search comes back in pages.
    private suspend fun searchMet(): List<Int> = withContext(Dispatchers.IO) {
        orEmpty {
            val (department, word) = MET_SEARCHES.random()
            val base = "https://collectionapi.metmuseum.org/public/collection/v1.1/search?hasImages=true&departmentId=$department&q=$word"
            val total = JSONObject(read("$base&limit=1")).optInt("total")
            val offset = (0 until maxOf(1, total - 50)).random()
            val ids = JSONObject(read("$base&limit=50&offset=$offset")).getJSONArray("objectIDs")
            List(ids.length()) { ids.getInt(it) }.shuffled()
        }
    }

    // The caption is who made it, then when and what it's made of. The Met doesn't write descriptions.
    private fun metCaption(artwork: Artwork): String {
        val maker = if (artwork.artist.isNotEmpty() && artwork.artistBio.isNotEmpty()) {
            "${artwork.artist} (${artwork.artistBio})"
        } else {
            artwork.maker
        }
        val details = listOf(artwork.date, artwork.medium).filter { it.isNotEmpty() }.joinToString(" · ")
        return listOf(maker, details).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private suspend fun download(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            val bytes = URL(url).openStream().use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= SCREEN_WIDTH) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // A failed search just means nothing new this time; the next one tries again.
    private inline fun <T> orEmpty(block: () -> List<T>): List<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
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
        if (text.length <= DESCRIPTION_LENGTH) return text
        val end = Regex("[.!?](\\s|$)").find(text, DESCRIPTION_LENGTH)?.range?.first ?: return text
        return text.substring(0, end + 1)
    }
}
