package com.example.mysmarttv.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private const val PREFS_NAME = "settings"
private const val DARK_MODE_KEY = "dark_mode"
private const val SCREENSAVER_KEY = "screensaver_minutes"
private const val SLEEP_AFTER_KEY = "sleep_after_minutes"
private const val NEWS_CARD_HEADLINES_KEY = "news_card_headlines"
private const val NEWS_TEXT_SIZE_KEY = "news_text_size"

/** The News story text sizes, as a share of the largest. The largest is the default. */
val NEWS_TEXT_SIZES = listOf(0.7f, 0.8f, 0.9f, 1f)

/** The screensaver delays to choose from, in minutes. 0 turns it off. */
val SCREENSAVER_OPTIONS = listOf(0, 1, 2, 5, 10, 15, 30)
private const val DEFAULT_SCREENSAVER_MINUTES = 10

/** How long the screensaver runs before the TV is allowed to sleep, in minutes. 0 means never. */
val SLEEP_AFTER_OPTIONS = listOf(0, 30, 60, 120, 240)
private const val DEFAULT_SLEEP_AFTER_MINUTES = 60

/** MySmartTV's own settings, saved on the TV. Compose state, so screens update as soon as one changes. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var darkMode by mutableStateOf(prefs.getBoolean(DARK_MODE_KEY, false))
        private set

    var screensaverMinutes by mutableIntStateOf(prefs.getInt(SCREENSAVER_KEY, DEFAULT_SCREENSAVER_MINUTES))
        private set

    var sleepAfterMinutes by mutableIntStateOf(prefs.getInt(SLEEP_AFTER_KEY, DEFAULT_SLEEP_AFTER_MINUTES))
        private set

    // Whether the News Channel's card scrolls headlines. Set from inside the News Channel.
    var newsCardHeadlines by mutableStateOf(prefs.getBoolean(NEWS_CARD_HEADLINES_KEY, true))
        private set

    fun toggleNewsCardHeadlines() {
        newsCardHeadlines = !newsCardHeadlines
        prefs.edit().putBoolean(NEWS_CARD_HEADLINES_KEY, newsCardHeadlines).apply()
    }

    // How big a News story's text is: a position in [NEWS_TEXT_SIZES], set with the story page's zoom buttons.
    var newsTextSize by mutableIntStateOf(
        prefs.getInt(NEWS_TEXT_SIZE_KEY, NEWS_TEXT_SIZES.lastIndex).coerceIn(NEWS_TEXT_SIZES.indices)
    )
        private set

    /** Steps the story text one size smaller or larger, stopping at the smallest and largest. */
    fun changeNewsTextSize(step: Int) {
        newsTextSize = (newsTextSize + step).coerceIn(NEWS_TEXT_SIZES.indices)
        prefs.edit().putInt(NEWS_TEXT_SIZE_KEY, newsTextSize).apply()
    }

    fun toggleDarkMode() {
        darkMode = !darkMode
        prefs.edit().putBoolean(DARK_MODE_KEY, darkMode).apply()
    }

    /** Steps to the next delay in [SCREENSAVER_OPTIONS], wrapping from the longest back to Off. */
    fun nextScreensaverDelay() {
        val index = SCREENSAVER_OPTIONS.indexOf(screensaverMinutes)
        screensaverMinutes = SCREENSAVER_OPTIONS[(index + 1) % SCREENSAVER_OPTIONS.size]
        prefs.edit().putInt(SCREENSAVER_KEY, screensaverMinutes).apply()
    }

    /** Steps to the next choice in [SLEEP_AFTER_OPTIONS], wrapping from the longest back to Never. */
    fun nextSleepDelay() {
        val index = SLEEP_AFTER_OPTIONS.indexOf(sleepAfterMinutes)
        sleepAfterMinutes = SLEEP_AFTER_OPTIONS[(index + 1) % SLEEP_AFTER_OPTIONS.size]
        prefs.edit().putInt(SLEEP_AFTER_KEY, sleepAfterMinutes).apply()
    }
}
