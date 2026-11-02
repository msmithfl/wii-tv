package com.example.mysmarttv.screensaver

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.mysmarttv.R
import com.example.mysmarttv.gallery.Exhibit
import com.example.mysmarttv.museum.MuseumClient
import com.example.mysmarttv.science.NasaClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Where the screensaver's pictures come from. Chosen in Settings, which steps through them in this order. */
enum class PictureSource(val label: String) {
    Nasa("NASA"),
    Met("Met Museum"),
    // The photos the original Chromecast showed on its home screen.
    Chromecast("Chromecast Photos"),
    // Takes turns between the sources above. Named Both from when there were only two, so a saved choice still matches.
    Both("Mix of All"),
}

// The sources a mix takes turns between.
private val MIX = listOf(PictureSource.Nasa, PictureSource.Met, PictureSource.Chromecast)

// What the space pictures are searched for in NASA's image library. A couple are picked at random each time it runs low.
private val NASA_TOPICS = listOf(
    "nebula", "galaxy", "hubble", "james webb", "star cluster", "milky way", "saturn", "jupiter",
    "mars surface", "earth from space", "aurora from space", "supernova", "moon surface", "solar flare",
)

// Where the Met's paintings are searched for, as its department number and a search word. One is picked at random
// each time it runs low: European Paintings, American Decorative Arts, and Asian Art.
private val MET_SEARCHES = listOf(11 to "painting", 11 to "landscape", 1 to "painting", 6 to "landscape")

// Pictures are shrunk to about the TV's width, so a huge original doesn't take up too much memory.
private const val SCREEN_WIDTH = 1920

/**
 * A picture ready to show, with the piece it's of, so holding OK can open it in the Science or Museum Channel.
 * [fillScreen] crops it to fill the TV; otherwise it's shown whole, like a painting on a wall.
 * A Chromecast photo has no channel; its piece is just the photographer's name, as the maker.
 */
data class ScreensaverPicture(
    val exhibit: Exhibit,
    val image: ImageBitmap,
    val fillScreen: Boolean,
    val source: PictureSource,
)

/**
 * Pictures for the screensaver: space pictures from NASA's image library, paintings from the Metropolitan
 * Museum of Art, the original Chromecast's photos, or all three taking turns. One picture is kept downloaded
 * ahead, so the screensaver can show it the moment it starts. [source] is read each time, so a change in
 * Settings takes effect with the next picture.
 */
class ScreensaverPictures(private val context: Context, private val source: () -> PictureSource) {
    private val nasaWaiting = ArrayDeque<Exhibit>()
    // The Met's search only gives object numbers; each one's details are looked up when its turn comes.
    private val metWaiting = ArrayDeque<Int>()
    private val chromecastWaiting = ArrayDeque<Exhibit>()
    private var ready: ScreensaverPicture? = null
    // In a mix, whose turn is next.
    private var mixTurn = 0
    private val lock = Mutex()

    /** The picture on screen right now, while the screensaver is showing. */
    var showing by mutableStateOf<ScreensaverPicture?>(null)

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
        val from = source().takeUnless { it == PictureSource.Both } ?: MIX[mixTurn++ % MIX.size]
        return when (from) {
            PictureSource.Met -> loadMet()
            PictureSource.Chromecast -> loadChromecast()
            else -> loadNasa()
        }
    }

    // Tries a few in a row, in case a picture fails to download.
    private suspend fun loadNasa(): ScreensaverPicture? {
        repeat(5) {
            if (nasaWaiting.isEmpty()) {
                nasaWaiting.addAll(NASA_TOPICS.shuffled().take(2).flatMap { NasaClient.search(it, (1..3).random(), wideOnly = true) }.shuffled())
            }
            val exhibit = nasaWaiting.removeFirstOrNull() ?: return null
            download(exhibit.imageUrl)?.let { return ScreensaverPicture(exhibit, it, fillScreen = true, PictureSource.Nasa) }
        }
        return null
    }

    // More tries than NASA, since some of the Met's search results turn out to have no picture it can share.
    private suspend fun loadMet(): ScreensaverPicture? {
        repeat(8) {
            if (metWaiting.isEmpty()) metWaiting.addAll(searchMet())
            val id = metWaiting.removeFirstOrNull() ?: return null
            val exhibit = MuseumClient.exhibit(id) ?: return@repeat
            download(exhibit.imageUrl)?.let { return ScreensaverPicture(exhibit, it, fillScreen = false, PictureSource.Met) }
        }
        return null
    }

    // The photos come from a list kept in the app (res/raw/chromecast_backgrounds.json), shuffled, all shown once
    // before any repeats. The list only has photos whose links still worked when it was made; a few may have
    // stopped since, so this tries a few in a row.
    private suspend fun loadChromecast(): ScreensaverPicture? {
        repeat(5) {
            if (chromecastWaiting.isEmpty()) chromecastWaiting.addAll(chromecastPhotos().shuffled())
            val exhibit = chromecastWaiting.removeFirstOrNull() ?: return null
            download(exhibit.imageUrl)?.let { return ScreensaverPicture(exhibit, it, fillScreen = true, PictureSource.Chromecast) }
        }
        return null
    }

    // From github.com/dconnolly/chromecast-backgrounds, each with the photographer's name when it's known.
    private suspend fun chromecastPhotos(): List<Exhibit> = withContext(Dispatchers.IO) {
        val text = context.resources.openRawResource(R.raw.chromecast_backgrounds).bufferedReader().use { it.readText() }
        val photos = JSONArray(text)
        List(photos.length()) { index ->
            val photo = photos.getJSONObject(index)
            val url = photo.getString("url")
            Exhibit(
                id = url,
                title = "",
                maker = photo.optString("author"),
                makerDetail = "",
                imageUrl = url,
                smallImageUrl = url,
                facts = emptyList(),
                description = "",
                summary = "",
                footnote = "",
            )
        }
    }

    // A random stretch of a random search's results, since the search comes back in pages.
    private suspend fun searchMet(): List<Int> = withContext(Dispatchers.IO) {
        try {
            val (department, word) = MET_SEARCHES.random()
            val base = "https://collectionapi.metmuseum.org/public/collection/v1.1/search?hasImages=true&departmentId=$department&q=$word"
            val total = JSONObject(read("$base&limit=1")).optInt("total")
            val offset = (0 until maxOf(1, total - 50)).random()
            val ids = JSONObject(read("$base&limit=50&offset=$offset")).getJSONArray("objectIDs")
            List(ids.length()) { ids.getInt(it) }.shuffled()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
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
