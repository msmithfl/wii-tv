package com.example.mysmarttv.news

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val REFRESH_EVERY = 20.minutes
private val SECTION_TIME_LIMIT = 20.seconds
private val RETRY_MISSING_AFTER = 2.minutes

// The sections that have a feed to fetch.
private val FEEDS = NewsSection.entries.filter { it.hasFeed }

/** Holds the latest stories for each section. Its state is Compose state, so the tile and screen update on their own. */
class NewsStore(context: Context) {
    var stories by mutableStateOf<Map<NewsSection, List<Story>>>(emptyMap())
        private set
    var updatedAt by mutableStateOf<Instant?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    private val todaysPicks = TodaysHeadlines(context)

    /** A handful of stories picked once a day; the same all day, for the card and the Today's Headlines section. */
    var todaysHeadlines by mutableStateOf(todaysPicks.load())
        private set

    // A failed download only fails its own section, not the others.
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)


    /** Fetches now and then every 20 minutes, for as long as the caller keeps it running. */
    suspend fun keepFresh() {
        while (true) {
            refresh()
            // If any section didn't come through, try again soon rather than waiting the full 20 minutes.
            delay(if (stories.size < FEEDS.size) RETRY_MISSING_AFTER else REFRESH_EVERY)
        }
    }

    // Every section is fetched at once. A section that fails keeps its last stories, if it had any.
    private suspend fun refresh() {
        // The downloads run outside this refresh, so one stuck on a slow connection can be left behind once
        // its time is up instead of holding up the others. It finishes or fails on its own later.
        val downloads = FEEDS.map { section ->
            section to downloadScope.async { NewsClient.fetch(section) }
        }
        val results = downloads.map { (section, download) ->
            try {
                withTimeoutOrNull(SECTION_TIME_LIMIT) { section to download.await() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        val fetched = results.filterNotNull().toMap()
        if (fetched.isNotEmpty()) {
            stories = stories + fetched
            todaysHeadlines = todaysPicks.update(stories)
            updatedAt = Instant.now()
        }
        failed = fetched.isEmpty()
    }
}
