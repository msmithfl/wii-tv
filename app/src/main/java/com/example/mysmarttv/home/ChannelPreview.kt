package com.example.mysmarttv.home

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.ui.theme.WiiBlue
import com.example.mysmarttv.ui.theme.WiiClockText
import com.example.mysmarttv.ui.theme.WiiDateText
import com.example.mysmarttv.ui.theme.WiiSurface
import com.example.mysmarttv.ui.theme.WiiTileBorder
import com.example.mysmarttv.ui.theme.wiiBackdrop

// How long the card takes to grow out of its spot on the dashboard to fill the screen, and to shrink back into it.
private const val GROW_MS = 380
private const val SHRINK_MS = 320

// With no card to grow out of (say it's on the other page), it grows from this share of the screen, in the middle.
private const val FALLBACK_SIZE = 0.3f

// How long one channel's banner takes to slide away and the next one's to slide in.
private const val SLIDE_MS = 350

// How much bigger a channel arrow grows while selected.
private const val ARROW_GROW = 1.4f

private val PillShape = RoundedCornerShape(50)

// The remote's OK button, which different remotes send as different keys.
private val OkKeys = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A)

/**
 * The Wii's channel start screen: the channel's banner filling a big card, with "TiiV Menu" to go back and
 * "Start" to open it. Shown when a dashboard card is picked, unless it's turned off in Settings.
 * Left past "TiiV Menu" goes to the dashboard's previous channel, and right past "Start" to the next.
 */
@Composable
fun ChannelPreview(
    channel: Channel,
    // Where the picked card sits on the dashboard, to grow out of and shrink back into.
    cardBounds: Rect?,
    sounds: Sounds,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    // 0 is the card in its spot on the dashboard, 1 is filling the screen.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(GROW_MS, easing = FastOutSlowInEasing)) }
    // Going back shrinks the card into its spot first, then closes. Presses are ignored while it does.
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close = {
        if (!closing) {
            closing = true
            sounds.playChannelClose()
            scope.launch {
                grow.animateTo(0f, tween(SHRINK_MS, easing = FastOutSlowInEasing))
                onBack()
            }
        }
    }
    BackHandler(onBack = close)
    // The whole screen takes the remote's presses itself and draws what's selected, so Start is always the one
    // selected when it opens. The four things to pick sit in a square, moved round with the arrows on the remote:
    // the two channel arrows on top, TiiV Menu and Start below.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var selected by remember { mutableStateOf(Pick.Start) }
    // Which way the last change of channel went, so the banners slide the matching way: 1 for next, -1 for previous.
    var direction by remember { mutableIntStateOf(1) }
    // OK acts when it's let go, and only if it was also pressed here, so the press that opened this screen doesn't
    // carry over and press Start.
    var okDown by remember { mutableStateOf(false) }
    val previous = {
        direction = -1
        sounds.playCardMove()
        onPrevious()
    }
    val next = {
        direction = 1
        sounds.playCardMove()
        onNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // The dashboard darkens to black behind the card as it grows.
            .drawBehind { drawRect(Color.Black.copy(alpha = grow.value)) }
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                val key = event.nativeKeyEvent
                val down = key.action == KeyEvent.ACTION_DOWN
                if (closing) return@onPreviewKeyEvent true
                val move = when (key.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> selected.copy(row = 0)
                    KeyEvent.KEYCODE_DPAD_DOWN -> selected.copy(row = 1)
                    KeyEvent.KEYCODE_DPAD_LEFT -> selected.copy(column = 0)
                    KeyEvent.KEYCODE_DPAD_RIGHT -> selected.copy(column = 1)
                    else -> null
                }
                when {
                    move != null -> {
                        // Stops at the edges of the square, except that pressing on past an arrow, the way it points,
                        // uses it like OK does.
                        if (down) {
                            when {
                                move != selected -> {
                                    sounds.playCardMove()
                                    selected = move
                                }
                                selected == Pick.Previous && key.keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> previous()
                                selected == Pick.Next && key.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> next()
                            }
                        }
                        true
                    }
                    key.keyCode in OkKeys -> {
                        if (down && key.repeatCount == 0) {
                            okDown = true
                        } else if (!down && okDown) {
                            okDown = false
                            when (selected) {
                                Pick.Previous -> previous()
                                Pick.Next -> next()
                                Pick.Menu -> close()
                                else -> {
                                    sounds.playAppSelect()
                                    onStart()
                                }
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            // Moves and stretches the full-screen card onto the dashboard card's spot, by however far it has left to grow.
            .graphicsLayer {
                val padX = 16.dp.toPx()
                val padY = 10.dp.toPx()
                val screenWidth = size.width + padX * 2
                val screenHeight = size.height + padY * 2
                val onScreen = cardBounds != null && cardBounds.right > 0f && cardBounds.left < screenWidth
                val from = if (onScreen) cardBounds!! else Rect(
                    left = screenWidth * (1 - FALLBACK_SIZE) / 2,
                    top = screenHeight * (1 - FALLBACK_SIZE) / 2,
                    right = screenWidth * (1 + FALLBACK_SIZE) / 2,
                    bottom = screenHeight * (1 + FALLBACK_SIZE) / 2,
                )
                val p = grow.value
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = lerp(from.width / size.width, 1f, p)
                scaleY = lerp(from.height / size.height, 1f, p)
                translationX = lerp(from.left - padX, 0f, p)
                translationY = lerp(from.top - padY, 0f, p)
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // No white card behind the banner: where the rounded edge is smoothed, it would show through as a thin
                // white line round dark banners.
                .clip(TileShape),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds(),
            ) {
                AnimatedContent(
                    targetState = channel,
                    transitionSpec = {
                        val tween = tween<IntOffset>(SLIDE_MS, easing = FastOutSlowInEasing)
                        slideInHorizontally(tween) { it * direction } togetherWith
                            slideOutHorizontally(tween) { -it * direction }
                    },
                    label = "channel slide",
                ) { shown ->
                    PreviewBanner(shown)
                }
                // Arrows at the sides to step to the dashboard's previous and next channels, as on the Wii. They grow while selected.
                ChannelArrow(
                    pointsRight = false,
                    active = selected == Pick.Previous,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp),
                )
                ChannelArrow(
                    pointsRight = true,
                    active = selected == Pick.Next,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp),
                )
            }
            // The strip along the bottom holding the two buttons, lined like the dashboard's background, as on the Wii.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .wiiBackdrop(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                    PreviewButton("TiiV Menu", selected = selected == Pick.Menu)
                    PreviewButton("Start", selected = selected == Pick.Start)
                }
            }
        }
    }
}

// A spot in the start screen's square of things to pick: row 0 is the channel arrows, row 1 the buttons.
private data class Pick(val row: Int, val column: Int) {
    companion object {
        val Previous = Pick(0, 0)
        val Next = Pick(0, 1)
        val Menu = Pick(1, 0)
        val Start = Pick(1, 1)
    }
}

/** One of the two white buttons. Its grey rim turns blue while selected, like the corner buttons. */
@Composable
private fun PreviewButton(label: String, selected: Boolean) {
    val rim by animateColorAsState(
        targetValue = if (selected) WiiBlue else WiiTileBorder,
        animationSpec = tween(SelectFadeMs),
        label = "button rim",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(340.dp)
            .height(80.dp)
            .background(WiiSurface, PillShape)
            .border(if (selected) 3.dp else 2.dp, rim, PillShape),
    ) {
        Text(text = label, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = WiiDateText)
    }
}

/**
 * The channel's banner, filling the space edge to edge. The space is wider than the 16:9 banners (about 2.5 to 1),
 * so a strip is trimmed off the top and bottom.
 */
@Composable
private fun PreviewBanner(channel: Channel) {
    val context = LocalContext.current
    val builtInBanner = remember(channel.packageName) { bannerImage(context, channel.packageName) }
    val appBanner = remember(channel.packageName) {
        if (builtInBanner != null) null else loadBanner(context, channel.packageName)
    }
    val banner: ImageBitmap? = if (builtInBanner != null) rememberBuiltInBanner(builtInBanner, smooth = false) else appBanner

    // Only a channel with no picture gets the white card, behind its name.
    val fill = if (banner == null) WiiSurface else Color.Transparent
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().background(fill)) {
        if (banner != null) {
            Image(
                bitmap = banner,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(text = channel.name, fontSize = 64.sp, color = WiiClockText)
        }
    }
}

/** A light blue triangle with a white edge, pointing to the next or previous channel. Grows while [active]. */
@Composable
private fun ChannelArrow(pointsRight: Boolean, active: Boolean, modifier: Modifier = Modifier) {
    val blue = WiiBlue
    val grow by animateFloatAsState(
        targetValue = if (active) ARROW_GROW else 1f,
        animationSpec = tween(SelectFadeMs),
        label = "arrow grow",
    )
    Canvas(
        modifier
            .size(width = 24.dp, height = 42.dp)
            .graphicsLayer {
                scaleX = grow * if (pointsRight) 1f else -1f
                scaleY = grow
            }
    ) {
        val arrow = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2)
            lineTo(0f, size.height)
            close()
        }
        drawPath(arrow, Color.Black.copy(alpha = 0.25f), style = Stroke(width = 6.dp.toPx(), join = StrokeJoin.Round))
        drawPath(arrow, blue)
        drawPath(arrow, Color.White, style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round))
    }
}
