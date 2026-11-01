package com.example.mysmarttv.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Two pages of 12.
const val SLOT_COUNT = 24
private const val PREFS_NAME = "dashboard"
private const val SLOTS_KEY = "slots"

data class Channel(val name: String, val packageName: String)

// Stands in for a package name so the Forecast Channel can sit in a slot like an app.
// Kept as "@weather" so slots saved before the rename still find it.
const val WEATHER_ID = "@weather"
val WeatherChannel = Channel("Forecast Channel", WEATHER_ID)

const val NEWS_ID = "@news"
val NewsChannel = Channel("News Channel", NEWS_ID)

const val MUSEUM_ID = "@museum"
val MuseumChannel = Channel("Museum Channel", MUSEUM_ID)

/** The channels built into MySmartTV itself, offered alongside the TV's installed apps. */
val BuiltInChannels = listOf(WeatherChannel, NewsChannel, MuseumChannel)

// What the dashboard starts with before anything is added or removed.
private val defaultPackages = listOf(
    "com.google.android.youtube.tv",
    "com.github.damontecres.wholphin",
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
)

/** Which app sits in each dashboard slot, saved on the TV so it survives restarts. */
class ChannelStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var slots by mutableStateOf(load())
        private set

    val packageNames: Set<String> get() = slots.mapNotNull { it?.packageName }.toSet()

    fun set(slot: Int, packageName: String) = update(slot, packageName)

    fun remove(slot: Int) = update(slot, null)

    private fun update(slot: Int, packageName: String?) {
        val packages = slots.map { it?.packageName }.toMutableList()
        packages[slot] = packageName
        prefs.edit().putString(SLOTS_KEY, packages.joinToString(",") { it.orEmpty() }).apply()
        slots = packages.map { it?.let { pkg -> channelFor(pkg) } }
    }

    private fun load(): List<Channel?> {
        val saved = prefs.getString(SLOTS_KEY, null)?.split(",") ?: defaultPackages
        return List(SLOT_COUNT) { index ->
            saved.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { channelFor(it) }
        }
    }

    // Null if the app has been uninstalled, so its slot shows as empty.
    private fun channelFor(packageName: String): Channel? =
        BuiltInChannels.find { it.packageName == packageName }
            ?: appLabel(context, packageName)?.let { Channel(it, packageName) }
}
