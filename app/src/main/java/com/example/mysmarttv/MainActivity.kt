package com.example.mysmarttv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.mysmarttv.home.AppPickerScreen
import com.example.mysmarttv.home.ChannelStore
import com.example.mysmarttv.home.HomeScreen
import com.example.mysmarttv.home.MUSIC_BUTTON
import com.example.mysmarttv.home.SETTINGS_BUTTON
import com.example.mysmarttv.home.Sounds
import com.example.mysmarttv.music.MusicPlayer
import com.example.mysmarttv.music.MusicScreen
import com.example.mysmarttv.museum.Artwork
import com.example.mysmarttv.museum.MuseumScreen
import com.example.mysmarttv.museum.MuseumStore
import com.example.mysmarttv.news.NewsScreen
import com.example.mysmarttv.news.NewsStore
import com.example.mysmarttv.screensaver.Screensaver
import com.example.mysmarttv.screensaver.ScreensaverPictures
import com.example.mysmarttv.settings.AppSettings
import com.example.mysmarttv.settings.SettingsScreen
import com.example.mysmarttv.ui.theme.MySmartTVTheme
import com.example.mysmarttv.weather.WeatherScreen
import com.example.mysmarttv.weather.WeatherStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val KEY_RESUME_MUSIC = "resume_music"

// The remote's OK button, which different remotes send as different keys.
private val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A)

class MainActivity : ComponentActivity() {
    private lateinit var sounds: Sounds
    private lateinit var channels: ChannelStore
    private lateinit var appSettings: AppSettings
    private val music = MusicPlayer()
    private val weather = WeatherStore()
    private lateinit var news: NewsStore
    private val screensaverPictures = ScreensaverPictures { appSettings.screensaverPictures }
    private val museum = MuseumStore()

    // Set when OK is held on a painting in the screensaver, to open it in the Museum Channel.
    private var artworkToOpen by mutableStateOf<Artwork?>(null)
    // After that hold, the rest of the press (its repeats and release) is ignored, so it doesn't also act on the screen.
    private var ignoreUntilRelease = false

    // Counts Home button presses while the app is already open, so the screens can close back to the dashboard.
    private var homePresses by mutableIntStateOf(0)

    // When a remote button was last pressed, for timing the screensaver.
    private var lastInputAt = SystemClock.uptimeMillis()
    private var screensaverShowing by mutableStateOf(false)
    private var screensaverStartedAt = 0L

    // Set once the screensaver has run for the "Sleep after" time, so the app stops holding the screen on.
    private var tvMaySleep by mutableStateOf(false)

    // Whether music was playing when you left for another app, so it can start again when you come back.
    private var resumeMusic = false

    private val requestMusicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) music.loadTracks()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sounds = Sounds(this)
        channels = ChannelStore(this)
        appSettings = AppSettings(this)
        news = NewsStore(this)
        // Only on a fresh launch, not when Android rebuilds the screen.
        if (savedInstanceState == null) sounds.playStartup(then = ::playFirstSong)
        resumeMusic = savedInstanceState?.getBoolean(KEY_RESUME_MUSIC) ?: false

        // The songs live in the TV's Music folder, which needs permission to read.
        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            music.loadTracks()
        } else {
            requestMusicPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }

        // Fetches the forecast while the app is on screen: right away, then every half hour.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { weather.keepFresh() }
        }
        // The same for the news, every 20 minutes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { news.keepFresh() }
        }
        // The same for the Museum Channel's artwork of the day.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { museum.keepFresh() }
        }
        // Downloads the screensaver's first picture ahead, so it shows the moment the screensaver starts.
        lifecycleScope.launch { screensaverPictures.prepare() }

        // Shows the screensaver once no button has been pressed for the chosen time, while the app is on screen.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // Coming back from another app, or the TV waking from sleep, starts everything over.
                wake()
                while (true) {
                    delay(1_000)
                    val now = SystemClock.uptimeMillis()
                    val screensaverDelayMs = appSettings.screensaverMinutes * 60_000L
                    if (!screensaverShowing && screensaverDelayMs > 0 && now - lastInputAt >= screensaverDelayMs) {
                        screensaverShowing = true
                        screensaverStartedAt = now
                    }
                    // After the screensaver has run long enough, let go of the screen so Google's sleep can take over.
                    val sleepDelayMs = appSettings.sleepAfterMinutes * 60_000L
                    if (screensaverShowing && sleepDelayMs > 0 && now - screensaverStartedAt >= sleepDelayMs) {
                        tvMaySleep = true
                    }
                }
            }
        }

        setContent {
            // While our screensaver is on, keep Google's own screensaver and sleep from taking over first,
            // until the "Sleep after" time has passed.
            val holdScreenOn = appSettings.screensaverMinutes > 0 && !tvMaySleep
            LaunchedEffect(holdScreenOn) {
                if (holdScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            MySmartTVTheme(isInDarkTheme = appSettings.darkMode) {
                Box {
                    var showMusic by rememberSaveable { mutableStateOf(false) }
                    var showWeather by rememberSaveable { mutableStateOf(false) }
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    var showNews by rememberSaveable { mutableStateOf(false) }
                    var showMuseum by rememberSaveable { mutableStateOf(false) }
                    // A piece to open the Museum Channel straight to, from the screensaver. Each one counts as a
                    // fresh visit, so the channel starts over at that piece even if it was already open.
                    var museumStart by remember { mutableStateOf<Artwork?>(null) }
                    var museumVisit by remember { mutableIntStateOf(0) }
                    // The dashboard slot an app is being picked for, or -1 when not picking.
                    var pickingSlot by rememberSaveable { mutableIntStateOf(-1) }
                    // What's selected when the home screen comes back, so it returns to where you were: a slot,
                    // or the settings or music button, along with the page that was showing.
                    var homeSlot by rememberSaveable { mutableIntStateOf(0) }
                    var homePage by rememberSaveable { mutableIntStateOf(0) }
                    // Home always lands on the dashboard, from music, weather or the app picker.
                    LaunchedEffect(homePresses) {
                        showMusic = false
                        showWeather = false
                        showSettings = false
                        showNews = false
                        showMuseum = false
                        pickingSlot = -1
                    }
                    LaunchedEffect(artworkToOpen) {
                        val artwork = artworkToOpen ?: return@LaunchedEffect
                        showMusic = false
                        showWeather = false
                        showSettings = false
                        showNews = false
                        pickingSlot = -1
                        museumStart = artwork
                        museumVisit++
                        showMuseum = true
                        artworkToOpen = null
                    }
                    when {
                        showMusic -> MusicScreen(player = music, sounds = sounds, onBack = {
                            sounds.playBack()
                            showMusic = false
                        })
                        showSettings -> SettingsScreen(settings = appSettings, sounds = sounds, onBack = {
                            sounds.playBack()
                            showSettings = false
                        })
                        showWeather -> WeatherScreen(store = weather, sounds = sounds, onBack = {
                            sounds.playBack()
                            showWeather = false
                        })
                        showNews -> NewsScreen(store = news, settings = appSettings, sounds = sounds, onBack = {
                            sounds.playBack()
                            showNews = false
                        })
                        showMuseum -> key(museumVisit) {
                            MuseumScreen(store = museum, sounds = sounds, startArtwork = museumStart, onBack = {
                                sounds.playBack()
                                showMuseum = false
                                museumStart = null
                            })
                        }
                        pickingSlot >= 0 -> AppPickerScreen(
                            slot = pickingSlot,
                            store = channels,
                            sounds = sounds,
                            onDone = { pickingSlot = -1 },
                        )
                        else -> HomeScreen(
                            store = channels,
                            sounds = sounds,
                            onOpenMusic = { page ->
                                // Re-read the folder so newly copied songs show up.
                                music.loadTracks()
                                showMusic = true
                                homeSlot = MUSIC_BUTTON
                                homePage = page
                            },
                            musicMuted = music.isMuted,
                            onToggleMute = music::toggleMute,
                            weather = weather,
                            onOpenWeather = {
                                showWeather = true
                                homeSlot = it
                            },
                            news = news,
                            newsHeadlines = appSettings.newsCardHeadlines,
                            onOpenNews = {
                                showNews = true
                                homeSlot = it
                            },
                            museum = museum,
                            onOpenMuseum = {
                                showMuseum = true
                                homeSlot = it
                            },
                            onAddApp = {
                                pickingSlot = it
                                homeSlot = it
                            },
                            onOpenSettings = { page ->
                                showSettings = true
                                homeSlot = SETTINGS_BUTTON
                                homePage = page
                            },
                            startSlot = homeSlot,
                            startPage = homePage,
                        )
                    }
                    // Drawn over whatever screen is open, so waking it lands back where you were.
                    if (screensaverShowing) Screensaver(screensaverPictures)
                }
            }
        }
    }

    // Every remote press restarts the screensaver timer. While the screensaver is showing, a press only wakes it,
    // so it doesn't also act on the screen underneath. Holding OK on a painting opens it in the Museum Channel.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        lastInputAt = SystemClock.uptimeMillis()
        if (ignoreUntilRelease) {
            if (event.action == KeyEvent.ACTION_UP) ignoreUntilRelease = false
            return true
        }
        if (screensaverShowing) {
            val artwork = screensaverPictures.showing?.artwork
            // A held button repeats; the first repeat comes about half a second in.
            val heldOk = event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && event.keyCode in OK_KEYS
            if (artwork != null && heldOk) {
                artworkToOpen = artwork
                ignoreUntilRelease = true
                wake()
            } else if (event.action == KeyEvent.ACTION_UP) {
                wake()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun wake() {
        screensaverShowing = false
        tvMaySleep = false
        lastInputAt = SystemClock.uptimeMillis()
    }

    // As the home screen, the app stays open; pressing Home sends it here instead of starting it again.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            homePresses++
            wake()
        }
    }

    // Skipped if you've already left the app, picked a song yourself, or there are no songs yet.
    private fun playFirstSong() {
        val onScreen = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (onScreen && music.currentIndex == null && music.tracks.isNotEmpty()) music.play(0)
    }

    // Music keeps playing on the home screen, but stops when you open another app.
    // If it was playing when you left, it picks back up when you come back.
    override fun onStop() {
        super.onStop()
        resumeMusic = music.isPlaying
        music.pause()
    }

    override fun onStart() {
        super.onStart()
        if (resumeMusic) {
            resumeMusic = false
            music.resume()
        }
    }

    // Android may close the app while you're in another one; this remembers to start the music again.
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_RESUME_MUSIC, resumeMusic || music.isPlaying)
    }

    override fun onDestroy() {
        super.onDestroy()
        sounds.release()
        music.release()
    }
}
