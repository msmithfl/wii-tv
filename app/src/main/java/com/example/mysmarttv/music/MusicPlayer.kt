package com.example.mysmarttv.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

private const val RESTART_THRESHOLD_MS = 3_000
private const val PREFS_NAME = "music"
private const val LOOP_MODE_KEY = "loop_mode"
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "wav", "flac")
private val LEADING_NUMBER = Regex("""^\d+\s*[.)\-_]?\s*""")

/** The TV's Music/Wii folder. Songs are copied here with adb, so they stay out of the app itself. */
val MUSIC_FOLDER: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Wii")

enum class LoopMode(val label: String) { Off("Off"), All("All"), One("One") }

data class Track(val title: String, val path: String)

/**
 * Plays the songs in [MUSIC_FOLDER]. Its state is Compose state, so screens update on their own.
 * The loop setting is saved on the TV, so it stays as you left it.
 */
class MusicPlayer(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var tracks by mutableStateOf(emptyList<Track>())
        private set

    var currentIndex by mutableStateOf<Int?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    // Starts out looping one song, so the first track (which plays at startup) repeats until it's changed.
    var loopMode by mutableStateOf(
        LoopMode.entries.firstOrNull { it.name == prefs.getString(LOOP_MODE_KEY, null) } ?: LoopMode.One
    )
        private set
    var shuffle by mutableStateOf(false)
        private set
    // Silences the music without pausing it, so it carries on where it is when unmuted.
    var isMuted by mutableStateOf(false)
        private set

    private var player: MediaPlayer? = null

    val positionMs: Int get() = player?.currentPosition ?: 0
    val durationMs: Int get() = player?.duration ?: 0

    /** Reads the song list from the folder. Called again once the TV grants permission to read music. */
    fun loadTracks() {
        tracks = MUSIC_FOLDER.listFiles().orEmpty()
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .sortedBy { it.name }
            // Drops the file's own number ("02. "), since the list numbers songs by where they fall.
            .map { Track(title = it.nameWithoutExtension.replace(LEADING_NUMBER, ""), path = it.path) }
    }

    fun play(index: Int) {
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(tracks[index].path)
            isLooping = loopMode == LoopMode.One
            applyVolume()
            setOnCompletionListener { onTrackFinished() }
            prepare()
            start()
        }
        currentIndex = index
        isPlaying = true
    }

    fun togglePlayPause() {
        val current = player
        when {
            current == null -> if (tracks.isNotEmpty()) play(currentIndex ?: 0)
            isPlaying -> current.pause().also { isPlaying = false }
            else -> current.start().also { isPlaying = true }
        }
    }

    fun next() {
        if (tracks.isEmpty()) return
        play(nextIndex())
    }

    /** Like a CD player: restarts the track if it's a few seconds in, otherwise goes to the one before. */
    fun previous() {
        if (tracks.isEmpty()) return
        val current = currentIndex
        if (current == null || positionMs > RESTART_THRESHOLD_MS) {
            play(current ?: 0)
        } else {
            play((current - 1 + tracks.size) % tracks.size)
        }
    }

    fun cycleLoopMode() {
        loopMode = LoopMode.entries[(loopMode.ordinal + 1) % LoopMode.entries.size]
        player?.isLooping = loopMode == LoopMode.One
        prefs.edit().putString(LOOP_MODE_KEY, loopMode.name).apply()
    }

    fun toggleShuffle() {
        shuffle = !shuffle
    }

    fun toggleMute() {
        isMuted = !isMuted
        player?.applyVolume()
    }

    private fun MediaPlayer.applyVolume() {
        val volume = if (isMuted) 0f else 1f
        setVolume(volume, volume)
    }

    fun pause() {
        if (isPlaying) togglePlayPause()
    }

    /** Carries on from where it paused, or starts a song if nothing is loaded (say, after Android closed the app). */
    fun resume() {
        if (isPlaying || tracks.isEmpty()) return
        if (player == null) play(currentIndex?.takeIf { it in tracks.indices } ?: tracks.indices.random())
        else togglePlayPause()
    }

    fun release() {
        player?.release()
        player = null
        isPlaying = false
    }

    // Loop One never gets here: MediaPlayer repeats the track itself.
    private fun onTrackFinished() {
        val atLastTrack = currentIndex == tracks.lastIndex
        if (loopMode == LoopMode.Off && atLastTrack && !shuffle) {
            isPlaying = false
        } else {
            next()
        }
    }

    private fun nextIndex(): Int {
        val current = currentIndex ?: return 0
        if (shuffle && tracks.size > 1) {
            return (tracks.indices - current).random()
        }
        return (current + 1) % tracks.size
    }
}
