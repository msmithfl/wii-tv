package com.example.mysmarttv.news

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.R
import com.example.mysmarttv.home.TileShape
import com.example.mysmarttv.home.TileSurface
import com.example.mysmarttv.home.rememberBuiltInBanner
import com.example.mysmarttv.weather.ShadowedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

// How much of the card, from the top, is kept clear for the title in news_app.jpg.
private const val TitleShare = 0.4f

private val Bullet = Color(0xFFF5D020)

// How fast the headlines slide.
private val TickerSpeed = 50.dp // per second

// Where the yellow dots stop, in from the card's left edge, and how long the headlines rest there.
private val RestInset = 14.dp
private const val RestAtDot = 1_500L // milliseconds

/**
 * The News Channel's dashboard tile: its banner, with the day's headlines scrolling across under the
 * title, like the Wii's. Pressing it opens the News Channel.
 */
@Composable
fun NewsTile(
    store: NewsStore,
    // Turned off from inside the News Channel; the card then shows just its banner.
    showHeadlines: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    // The day's picked headlines, the same all day. The same story can turn up in more than one section,
    // so each headline is only listed once.
    val headlines = store.todaysHeadlines.map { it.title }.distinct()
    TileSurface(onClick = onClick, onLongClick = onLongClick, onFocused = onFocused, modifier = modifier) {
        Image(
            bitmap = rememberBuiltInBanner(R.drawable.news_app, smooth = false),
            contentDescription = "News Channel",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(TileShape),
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(TitleShare))
            if (showHeadlines && headlines.isNotEmpty()) {
                HeadlineTicker(headlines, Modifier.weight(1f - TitleShare).fillMaxWidth())
            }
        }
    }
}

/**
 * Two headlines at a time, each on a dark strip, sliding in from the right with the yellow dot leading,
 * resting a moment once the dots reach the left side.
 * Both start together; when the shorter one has gone, it waits for the longer one, then the next two start.
 */
@Composable
private fun HeadlineTicker(headlines: List<String>, modifier: Modifier = Modifier) {
    var pair by remember { mutableIntStateOf(0) }
    // With only one headline there's nothing to pair it with, so it runs on its own.
    val shown = List(minOf(2, headlines.size)) { headlines[(pair * 2 + it) % headlines.size] }
    var stripWidth by remember { mutableIntStateOf(0) }
    val rowWidths = remember(pair) { mutableStateListOf(0, 0) }
    val travel = remember { Animatable(0f) }
    val speed = with(LocalDensity.current) { TickerSpeed.toPx() }
    val restInset = with(LocalDensity.current) { RestInset.toPx() }

    LaunchedEffect(pair) {
        // Waits until both headlines have been measured, so it knows how far the longer one has to go.
        val (width, longest) = snapshotFlow { stripWidth to rowWidths.max() }.first { (w, l) -> w > 0 && l > 0 }
        travel.snapTo(0f)
        // Slides in until the yellow dots reach the left side, rests there a moment, then carries on out.
        slideTo(travel, width - restInset, speed)
        delay(RestAtDot)
        slideTo(travel, (width + longest).toFloat(), speed)
        // Back off the right edge before swapping in the next pair. Otherwise a longer new headline would
        // briefly show its tail at the old, far-left spot.
        travel.snapTo(0f)
        pair++
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        modifier = modifier.padding(bottom = 10.dp),
    ) {
        shown.forEachIndexed { row, text ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clipToBounds()
                    .onSizeChanged { stripWidth = it.width }
                    .padding(vertical = 3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        // As wide as the whole headline, even past the card's edge, so it can slide through.
                        .wrapContentWidth(Alignment.Start, unbounded = true)
                        .onSizeChanged { rowWidths[row] = it.width }
                        // Starts just off the right edge and moves left until it's gone off the left.
                        .offset { IntOffset((stripWidth - travel.value).roundToInt(), 0) },
                ) {
                    Spacer(Modifier.size(10.dp).clip(CircleShape).background(Bullet))
                    Text(
                        text = text,
                        // The headlines keep the plain system font, which reads better small and moving.
                        style = ShadowedText.copy(fontFamily = FontFamily.Default),
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

// Moves the headlines to [target] at the ticker's steady speed.
private suspend fun slideTo(travel: Animatable<Float, *>, target: Float, speed: Float) {
    val distance = target - travel.value
    if (distance <= 0f) return
    travel.animateTo(target, tween((distance / speed * 1000).toInt(), easing = LinearEasing))
}
