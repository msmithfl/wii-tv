package com.example.mysmarttv.news

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

private const val PREFS_NAME = "news"
private const val DATE_KEY = "todays_headlines_date"
private const val STORIES_KEY = "todays_headlines"

/**
 * The sections the day's headlines come from, one each, in the order they're listed, taking each section's
 * first story. BBC lists its stories in the order its editors rank them and Hacker News by readers' votes.
 * NPR's feeds are newest first, so its story comes from the hand-picked home page stories rather than a topic.
 */
private val PICK_FROM = listOf(
    NewsSection.NprTopStories,
    NewsSection.International,
    NewsSection.Business,
    NewsSection.Technology,
    NewsSection.Science,
    NewsSection.HackerNews,
)

/**
 * A handful of headlines picked once a day at 6 AM, so the card and the Today's Headlines section show the same
 * stories all day instead of changing with every refresh. Saved on the TV, so a restart keeps them too.
 */
class TodaysHeadlines(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Today's picks, or nothing if they haven't been made yet today. */
    fun load(): List<Story> {
        if (prefs.getString(DATE_KEY, null) != newsDay().toString()) return emptyList()
        val saved = JSONArray(prefs.getString(STORIES_KEY, "[]"))
        return List(saved.length()) { fromJson(saved.getJSONObject(it)) }
    }

    /**
     * Fills in today's picks from the latest [stories]. Picks already made today stay as they are; a section
     * that hadn't loaded yet when the others were picked gets its story added once it has.
     */
    fun update(stories: Map<NewsSection, List<Story>>): List<Story> {
        val kept = load()
        // Already one from every section today (and from the same sections as now, in case those changed).
        if (kept.map { it.section } == PICK_FROM) return kept
        val picks = kept.filter { it.section in PICK_FROM }.associateBy { it.section }.toMutableMap()
        for (section in PICK_FROM) {
            if (section in picks) continue
            // The same story can lead two sections; take the next one down rather than repeat it.
            val story = stories[section]?.firstOrNull { candidate -> picks.values.none { it.title == candidate.title } }
            if (story != null) picks[section] = story.copy(section = section)
        }
        val ordered = PICK_FROM.mapNotNull { picks[it] }
        prefs.edit()
            .putString(DATE_KEY, newsDay().toString())
            .putString(STORIES_KEY, JSONArray(ordered.map(::toJson)).toString())
            .apply()
        return ordered
    }

    private fun toJson(story: Story) = JSONObject()
        .put("title", story.title)
        .put("summary", story.summary)
        .put("image", story.imageUrl)
        .put("published", story.published?.toString())
        .put("source", story.source)
        .put("link", story.link)
        .put("section", story.section?.name)

    private fun fromJson(json: JSONObject) = Story(
        title = json.getString("title"),
        summary = json.optString("summary"),
        imageUrl = json.optString("image").ifEmpty { null },
        published = json.optString("published").ifEmpty { null }?.let { runCatching { ZonedDateTime.parse(it) }.getOrNull() },
        source = json.getString("source"),
        link = json.optString("link").ifEmpty { null },
        section = json.optString("section").ifEmpty { null }?.let { name -> NewsSection.entries.firstOrNull { it.name == name } },
    )
}

// A new day's headlines are picked once it's morning, not at midnight, so they come from that morning's
// news. Before then it still counts as the day before.
private const val NEW_DAY_HOUR = 6L

private fun newsDay(): LocalDate = LocalDateTime.now().minusHours(NEW_DAY_HOUR).toLocalDate()
