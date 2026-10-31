package com.example.mysmarttv.home

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.example.mysmarttv.R

/** Menu sound effects. SoundPool keeps them in memory so they play with no delay. */
class Sounds(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private var startup = 0
    private var startupLoaded = false
    private var startupRequested = false
    private var afterStartup: (() -> Unit)? = null
    private val cardMove: Int
    private val appSelect: Int
    private val openMenu: Int
    private val goBack: Int

    // SoundPool can't tell us when a sound ends, so read the startup sound's length up front.
    private val startupDurationMs: Long = context.resources.openRawResourceFd(R.raw.startup).use { file ->
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.fileDescriptor, file.startOffset, file.length)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }
    private val handler = Handler(Looper.getMainLooper())

    init {
        // Sounds load in the background, so the startup sound waits until it's ready.
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (sampleId == startup && status == 0) {
                startupLoaded = true
                if (startupRequested) startStartup()
            }
        }
        startup = pool.load(context, R.raw.startup, 1)
        cardMove = pool.load(context, R.raw.card_move, 1)
        appSelect = pool.load(context, R.raw.app_select, 1)
        openMenu = pool.load(context, R.raw.open_menu, 1)
        goBack = pool.load(context, R.raw.go_back, 1)
    }

    /** Plays the startup sound, then runs [then] once it has finished. */
    fun playStartup(then: () -> Unit = {}) {
        afterStartup = then
        if (startupLoaded) startStartup() else startupRequested = true
    }

    fun playCardMove() = play(cardMove)

    fun playAppSelect() = play(appSelect)

    /** Opening the settings or music screen. */
    fun playOpenMenu() = play(openMenu)

    /** Any step back: leaving a screen, or closing the remove dialog without removing. */
    fun playBack() = play(goBack)

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }

    private fun startStartup() {
        play(startup)
        afterStartup?.let { handler.postDelayed(it, startupDurationMs) }
        afterStartup = null
    }

    private fun play(soundId: Int) {
        pool.play(soundId, 1f, 1f, 1, 0, 1f)
    }
}
