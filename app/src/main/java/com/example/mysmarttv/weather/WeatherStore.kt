package com.example.mysmarttv.weather

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes

// Where the forecast is for. A city name ("Chicago"), "City, State" ("Springfield, Illinois") or a ZIP code.
const val WEATHER_LOCATION = "Coral Springs, Florida"

private val REFRESH_EVERY = 30.minutes

/** Holds the latest forecast. Its state is Compose state, so the tile and screen update on their own. */
class WeatherStore {
    var weather by mutableStateOf<Weather?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    /** Fetches now and then every half hour, for as long as the caller keeps it running. */
    suspend fun keepFresh() {
        while (true) {
            refresh()
            delay(REFRESH_EVERY)
        }
    }

    private suspend fun refresh() {
        try {
            weather = WeatherClient.fetch(WEATHER_LOCATION)
            failed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Keep showing the last forecast if there is one; the tile only says so when there's nothing.
            failed = true
        }
    }
}
