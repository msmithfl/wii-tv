package com.example.mysmarttv.screensaver

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.home.ClockFont
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("h:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d")

// How long each picture stays up, and how long one takes to fade into the next.
private const val SHOW_EACH_MS = 60_000L
private const val FADE_MS = 1_500

// How far each picture zooms in over its minute: 6% larger by the end.
private const val ZOOM_TO = 1.06f

// How long to wait before trying again when no picture could be downloaded, like when the internet is down.
private const val RETRY_MS = 30_000L

// A soft shadow so white text stays readable on a bright picture.
private val Shadowed = TextStyle(
    color = Color.White,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(0f, 2f), blurRadius = 8f),
)

/**
 * NASA space pictures or Met paintings, a new one every minute, with the clock in the top corner and the picture's
 * title and description along the bottom. Until the first picture arrives, or without internet, it's a black screen.
 */
@Composable
fun Screensaver(pictures: ScreensaverPictures) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000)
        }
    }
    var shown by remember { mutableStateOf<ScreensaverPicture?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            // The next picture downloads while the current one is up. If the screensaver closes first,
            // it stays downloaded for next time.
            val startedAt = SystemClock.uptimeMillis()
            pictures.prepare()
            if (shown != null) delay(SHOW_EACH_MS - (SystemClock.uptimeMillis() - startedAt))
            val next = pictures.take()
            if (next != null) shown = next else delay(RETRY_MS)
        }
    }
    // Lets the app know which picture is up, so holding OK can open it in the Science or Museum Channel.
    DisposableEffect(shown) {
        pictures.showing = shown
        onDispose { pictures.showing = null }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Crossfade(targetState = shown, animationSpec = tween(FADE_MS), label = "screensaver picture") { picture ->
            if (picture != null) {
                Box(Modifier.fillMaxSize()) {
                    // Each picture slowly zooms in while it's up, for a little movement. It starts as soon as the
                    // picture begins fading in, and runs a bit past its minute so it's still moving as it fades out.
                    val zoom = remember(picture) { Animatable(1f) }
                    LaunchedEffect(picture) {
                        zoom.animateTo(ZOOM_TO, tween((SHOW_EACH_MS + FADE_MS * 2).toInt(), easing = LinearEasing))
                    }
                    Image(
                        bitmap = picture.image,
                        contentDescription = picture.exhibit.title,
                        contentScale = if (picture.fillScreen) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoom.value
                                scaleY = zoom.value
                            },
                    )
                    if (picture.source == PictureSource.Chromecast) {
                        PhotoCredit(picture.exhibit.maker, Modifier.align(Alignment.BottomStart))
                    } else {
                        Caption(picture, Modifier.align(Alignment.BottomStart))
                    }
                }
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 48.dp),
        ) {
            Text(text = now.format(timeFormat), style = Shadowed, fontSize = 72.sp, fontFamily = ClockFont)
            Text(text = now.format(dateFormat), style = Shadowed, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// Just the photographer's name in the corner, the way the Chromecast showed its photos. Nothing if it isn't known.
@Composable
private fun PhotoCredit(photographer: String, modifier: Modifier = Modifier) {
    if (photographer.isEmpty()) return
    Text(
        text = "Photo by $photographer",
        style = Shadowed,
        fontSize = 16.sp,
        color = Color.White.copy(alpha = 0.85f),
        modifier = modifier.padding(start = 48.dp, bottom = 36.dp),
    )
}

// The title and description on a dark fade at the bottom of the picture.
@Composable
private fun Caption(picture: ScreensaverPicture, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(start = 48.dp, end = 48.dp, top = 72.dp, bottom = 36.dp),
    ) {
        val exhibit = picture.exhibit
        Text(text = exhibit.title, style = Shadowed, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (exhibit.summary.isNotEmpty()) {
            Text(
                text = exhibit.summary,
                style = Shadowed,
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .widthIn(max = 900.dp),
            )
        }
        Text(
            text = "Hold OK to learn more",
            style = Shadowed,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
