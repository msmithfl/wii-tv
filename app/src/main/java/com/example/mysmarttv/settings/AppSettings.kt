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
