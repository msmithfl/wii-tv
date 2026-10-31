package com.example.mysmarttv.screensaver

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.home.ClockFont
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val timeFormat = DateTimeFormatter.ofPattern("h:mm")
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d")

// Dim on black, so it's easy on the eyes in a dark room.
private val ScreensaverText = Color(0xFF6E747C)

// How long the clock rests in one spot, and how long it takes to drift to the next.
private const val MOVE_EVERY_MS = 20_000L
private const val DRIFT_MS = 4_000

// Roughly how much room the clock takes, so it never drifts off the edge.
private val ClockWidth = 380.dp
private val ClockHeight = 150.dp

/** A black screen with a dim clock that drifts to a new spot every 20 seconds, so nothing burns in. */
@Composable
fun Screensaver() {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000)
        }
    }
    // Where the clock sits, as a share of the free space across and down (0 = left/top, 1 = right/bottom).
    var spotX by remember { mutableFloatStateOf(0.5f) }
    var spotY by remember { mutableFloatStateOf(0.5f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(MOVE_EVERY_MS)
            spotX = Random.nextFloat()
            spotY = Random.nextFloat()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val x by animateDpAsState((maxWidth - ClockWidth) * spotX, tween(DRIFT_MS), label = "clock x")
        val y by animateDpAsState((maxHeight - ClockHeight) * spotY, tween(DRIFT_MS), label = "clock y")
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(x, y),
        ) {
            Text(text = now.format(timeFormat), fontSize = 96.sp, fontFamily = ClockFont, color = ScreensaverText)
            Text(text = now.format(dateFormat), fontSize = 24.sp, fontWeight = FontWeight.Medium, color = ScreensaverText)
        }
    }
}
