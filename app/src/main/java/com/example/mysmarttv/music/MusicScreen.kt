package com.example.mysmarttv.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.mysmarttv.R
import com.example.mysmarttv.home.Sounds
import com.example.mysmarttv.ui.theme.PillButton
import com.example.mysmarttv.ui.theme.WiiBlue
import com.example.mysmarttv.ui.theme.WiiSurface
import com.example.mysmarttv.ui.theme.WiiClockText
import com.example.mysmarttv.ui.theme.WiiDateText
import com.example.mysmarttv.ui.theme.WiiEmptyBottom
import com.example.mysmarttv.ui.theme.WiiFocusFill
import com.example.mysmarttv.ui.theme.WiiTileBorder
import com.example.mysmarttv.ui.theme.wiiBackdrop
import kotlinx.coroutines.delay

private val RowShape = RoundedCornerShape(14.dp)
private val RowVerticalPadding = 2.dp
private val PanelShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)
private val CoverShape = RoundedCornerShape(12.dp)

@Composable
fun MusicScreen(player: MusicPlayer, sounds: Sounds, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val playButton = remember { FocusRequester() }
    // Play is selected when the screen opens; only click for real moves.
    var firstFocus by remember { mutableStateOf(true) }
    val clicks = MenuClicks(
        onMove = { if (firstFocus) firstFocus = false else sounds.playCardMove() },
        onSelect = sounds::playAppSelect,
    )
    // Pressing left from the controls lands on the playing song, scrolling the list to it if needed,
    // instead of whichever row happens to sit beside the buttons.
    val listState = rememberLazyListState()
    val playingRow = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val rowPaddingPx = with(LocalDensity.current) { RowVerticalPadding.toPx() }
    val leftToPlaying = Modifier.onPreviewKeyEvent { event ->
        val playing = player.currentIndex
        if (event.key != Key.DirectionLeft || playing == null || playing !in player.tracks.indices) {
            return@onPreviewKeyEvent false
        }
        if (event.type == KeyEventType.KeyDown) {
            scope.launch {
                // On a TV, a selected row settles 30% of the way down the list. If the song is off screen,
                // jump straight to that spot so it doesn't land at the top and then slide down.
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == playing }) {
                    val restingSpot = listState.layoutInfo.viewportSize.height * 0.3f - rowPaddingPx
                    listState.scrollToItem(playing, -restingSpot.toInt())
                }
                runCatching { playingRow.requestFocus() }
            }
        }
        true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wiiBackdrop()
            .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 22.dp),
    ) {
        Text(text = "Music", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = WiiDateText)
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (player.tracks.isEmpty()) {
                Text(
                    text = "No songs found. Copy music to the TV's ${MUSIC_FOLDER.path} folder.",
                    fontSize = 18.sp,
                    color = WiiClockText,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                )
            } else LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(player.tracks) { index, track ->
                    TrackRow(
                        number = index + 1,
                        track = track,
                        isCurrent = index == player.currentIndex,
                        onClick = clicks.select { player.play(index) },
                        modifier = clicks.moves.let {
                            if (index == player.currentIndex) it.focusRequester(playingRow) else it
                        },
                    )
                }
            }
            NowPlayingPanel(
                player = player,
                playButton = playButton,
                clicks = clicks,
                leftEdge = leftToPlaying,
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight(),
            )
        }
    }

    LaunchedEffect(Unit) { playButton.requestFocus() }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackRow(number: Int, track: Track, isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = RowVerticalPadding),
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = WiiSurface,
            focusedContainerColor = WiiFocusFill,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(2.dp, WiiTileBorder), shape = RowShape),
            focusedBorder = Border(BorderStroke(3.dp, WiiBlue), shape = RowShape),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "%02d. %s".format(number, track.title),
                fontSize = 18.sp,
                color = if (isCurrent) WiiBlue else WiiDateText,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isCurrent) {
                Text(text = "♪", fontSize = 20.sp, color = WiiBlue)
            }
        }
    }
}

@Composable
private fun NowPlayingPanel(
    player: MusicPlayer,
    playButton: FocusRequester,
    clicks: MenuClicks,
    // Goes on the leftmost button of each row, where pressing left leaves the controls.
    leftEdge: Modifier,
    modifier: Modifier = Modifier,
) {
    // MediaPlayer doesn't report its position as it goes, so check it twice a second.
    var positionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.positionMs
            durationMs = player.durationMs
            delay(500)
        }
    }

    val current = player.currentIndex?.let { player.tracks[it] }
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Column(
        modifier = modifier
            .clip(PanelShape)
            .background(WiiSurface)
            .border(2.dp, WiiTileBorder, PanelShape)
            .padding(24.dp),
    ) {
        Text(text = "Now Playing", fontSize = 16.sp, color = WiiClockText)
        Text(
            text = current?.title ?: "Nothing playing",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = WiiDateText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // The same cover for every song for now. It fills the space between the title and the controls.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 14.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.wii_music_album),
                contentDescription = "Album cover",
                contentScale = ContentScale.Fit,
                modifier = Modifier.clip(CoverShape),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(PillShape)
                .background(WiiEmptyBottom)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(WiiBlue)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(text = formatTime(positionMs), fontSize = 14.sp, color = WiiClockText, modifier = Modifier.weight(1f))
            Text(text = formatTime(durationMs), fontSize = 14.sp, color = WiiClockText)
        }
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            PillButton(label = "Prev", onClick = clicks.select(player::previous), modifier = leftEdge.then(clicks.moves))
            PillButton(
                label = if (player.isPlaying) "Pause" else "Play",
                onClick = clicks.select(player::togglePlayPause),
                modifier = clicks.moves.focusRequester(playButton),
            )
            PillButton(label = "Next", onClick = clicks.select(player::next), modifier = clicks.moves)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            PillButton(
                label = "Shuffle: ${if (player.shuffle) "On" else "Off"}",
                onClick = clicks.select(player::toggleShuffle),
                modifier = leftEdge.then(clicks.moves),
            )
            PillButton(
                label = "Loop: ${player.loopMode.label}",
                onClick = clicks.select(player::cycleLoopMode),
                modifier = clicks.moves,
            )
        }
    }
}

/** The menu's sounds: a click when the selection moves, and the select sound when a button is pressed. */
private class MenuClicks(private val onMove: () -> Unit, private val onSelect: () -> Unit) {
    val moves: Modifier = Modifier.onFocusChanged { if (it.hasFocus) onMove() }

    fun select(action: () -> Unit): () -> Unit = {
        onSelect()
        action()
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
