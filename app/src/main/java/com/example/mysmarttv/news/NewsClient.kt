package com.example.mysmarttv.news

import android.text.Html
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The News Channel's sections, in the order they're listed, each with the free RSS feed it comes from. */
enum class NewsSection(val title: String, val source: String, val feedUrl: String, val listed: Boolean = true) {
    // Not a feed of its own: a handful of stories picked once a day from the sections below.
    Today("Today's Headlines", "", ""),
    HackerNews("Hacker News", "HACKER NEWS", "https://news.ycombinator.com/rss"),
    National("National News", "NPR", "https://feeds.npr.org/1003/rss.xml"),
    International("International News", "BBC NEWS", "https://feeds.bbci.co.uk/news/world/rss.xml"),
    Business("Business", "BBC NEWS", "https://feeds.bbci.co.uk/news/business/rss.xml"),
    Technology("Technology", "BBC NEWS", "https://feeds.bbci.co.uk/news/technology/rss.xml"),
    Science("Science", "BBC NEWS", "https://feeds.bbci.co.uk/news/science_and_environment/rss.xml"),
    Sports("Sports", "NPR", "https://feeds.npr.org/1055/rss.xml"),
    Entertainment("Entertainment", "BBC NEWS", "https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml"),

    // Not listed as a section: the stories NPR's editors chose for its home page, where Today's Headlines
    // takes its NPR story from. Kept last, so the listed sections' positions match their place in the list.
    NprTopStories("NPR Top Stories", "NPR", "https://feeds.npr.org/1002/rss.xml", listed = false);

    val hasFeed: Boolean get() = feedUrl.isNotEmpty()

    companion object {
        /** The sections shown in the News Channel's list, in order. */
        val listed = entries.filter { it.listed }
    }
}

data class Story(
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val published: ZonedDateTime?,
    val source: String,
    // The story's web page, where the full text is.
    val link: String?,
    // Which section it came from.
    val section: NewsSection? = null,
)

/** Reads the sections' RSS feeds. RSS is free and needs no account or key. */
object NewsClient {

    suspend fun fetch(section: NewsSection): List<Story> = withContext(Dispatchers.IO) {
        val connection = URL(section.feedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        try {
            connection.inputStream.use { readFeed(it, section.source) }
                .map { it.copy(section = section) }
                // NPR's radio pieces come without a picture, and only a sentence or two is written up for
                // them, so they're left out. Its written stories always have a picture.
                .filter { section.source != "NPR" || it.imageUrl != null }
        } finally {
            connection.disconnect()
        }
    }

    // Walks the feed's <item>s. Tag names keep their prefixes (like media:thumbnail), since namespaces are off.
    private fun readFeed(input: InputStream, source: String): List<Story> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(input, null)
        val stories = mutableListOf<Story>()
        var item: MutableMap<String, String>? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (val tag = parser.name) {
                    "item" -> item = mutableMapOf()
                    "media:thumbnail", "media:content" ->
                        item?.putIfAbsent("image", parser.getAttributeValue(null, "url").orEmpty())
                    "title", "description", "pubDate", "link", "content:encoded" ->
                        if (item != null) item[tag] = parser.nextText()
                }
                XmlPullParser.END_TAG -> if (parser.name == "item") {
                    item?.let { toStory(it, source) }?.let(stories::add)
                    item = null
                }
            }
        }
        return stories
    }

    private fun toStory(item: Map<String, String>, source: String): Story? {
        val title = plainText(item["title"]).ifEmpty { return null }
        return Story(
            title = title,
            // Hacker News has no summary, just a "Comments" link back to its discussion page.
            summary = plainText(item["description"]).takeUnless { it == "Comments" }.orEmpty(),
            imageUrl = (item["image"]?.ifEmpty { null } ?: firstImage(item["content:encoded"]))?.let(::biggerImage),
            published = item["pubDate"]?.let { runCatching { ZonedDateTime.parse(it.trim(), DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull() },
            source = source,
            link = item["link"]?.trim()?.ifEmpty { null },
        )
    }

    /**
     * The whole story, as paragraphs, read from its web page. Feeds only carry a sentence or two, so this is
     * fetched when a story is opened. NPR radio pieces have little written text, so their transcript is used too.
     */
    suspend fun fetchArticle(link: String): List<String> = withContext(Dispatchers.IO) {
        articles.get(link) ?: run {
            val page = Jsoup.connect(link).userAgent("Mozilla/5.0").timeout(10_000).get()
            val nprText = page.select("#storytext > p")
            // Hacker News links to sites of every kind, so after NPR's and the usual article layout, it falls
            // back to the page's main area, then to any paragraph at all.
            val paragraphs = when {
                nprText.isEmpty() -> listOf("article p", "main p", "p")
                    .map { page.select(it) }
                    .firstOrNull { it.isNotEmpty() }
                    .orEmpty()
                nprText.size < 3 -> nprText + page.select(".transcript p")
                else -> nprText
            }
            paragraphs
                // Leaves out captions and "This video can not be played" notes, which sit in picture and video blocks.
                .filter { p -> p.parents().none { it.tagName() == "figure" || it.tagName() == "figcaption" } }
                .map { it.text().trim() }
                // Leaves out short bits like photo credits and "Watch:" labels.
                .filter { it.split(' ').size > 4 }
                .distinct()
                .also { if (it.isNotEmpty()) articles.put(link, it) }
        }
    }

    // Stories already read, so opening one again doesn't download its page again.
    private val articles = LruCache<String, List<String>>(30)

    // Feeds sometimes put HTML tags or entities in their text.
    private fun plainText(text: String?): String =
        Html.fromHtml(text.orEmpty(), Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    // NPR puts its picture inside the story's HTML, next to an invisible tracking pixel to skip.
    private fun firstImage(html: String?): String? =
        Regex("""<img[^>]+src=['"]([^'"]+)['"]""").findAll(html.orEmpty())
            .map { it.groupValues[1] }
            .firstOrNull { "tracking" !in it }

    // Asks for a mid-sized picture: BBC sends tiny 240-wide thumbnails, and NPR full-size photos.
    private fun biggerImage(url: String): String = url
        .replace("/standard/240/", "/standard/800/")
        .replace(Regex("/resize/[^/]+/"), "/resize/800/")
}
