package com.example.mysmarttv.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.mysmarttv.ui.theme.wiiBackdrop
import com.example.mysmarttv.weather.ForecastTile
import com.example.mysmarttv.weather.WeatherStore
import kotlinx.coroutines.delay

private const val COLUMNS = 4
private const val ROWS = 3
private const val PAGE_SIZE = COLUMNS * ROWS
private const val PAGE_COUNT = SLOT_COUNT / PAGE_SIZE

// Space either side of a page's four columns. The other page's nearest column peeks into it.
private val SideMargin = 80.dp
private val TileGap = 6.dp

// Wide cards, about twice as wide as they are tall.
private const val TILE_HEIGHT_RATIO = 0.5f

@Composable
fun HomeScreen(
    store: ChannelStore,
    sounds: Sounds,
    onOpenMusic: () -> Unit,
    musicMuted: Boolean,
    onToggleMute: () -> Unit,
    weather: WeatherStore,
    onOpenWeather: (slot: Int) -> Unit,
    onOpenNews: (slot: Int) -> Unit,
    onAddApp: (slot: Int) -> Unit,
    onOpenSettings: () -> Unit,
    startSlot: Int = 0,
) {
    // The dashboard is the TV's home screen, so there's nothing behind it for Back to go to.
    BackHandler {}
    val context = LocalContext.current
    val tileFocus = remember { List(SLOT_COUNT) { FocusRequester() } }
    val settingsFocus = remember { FocusRequester() }
    val musicFocus = remember { FocusRequester() }
    // Set after removing an app, so the selection stays on that slot instead of jumping away.
    var refocusSlot by remember { mutableIntStateOf(-1) }
    // The slot whose remove dialog is open, or -1.
    var removingSlot by remember { mutableIntStateOf(-1) }
    // The first tile gets focus on its own when the screen opens; only play the sound for real moves.
    var initialFocus by remember { mutableStateOf(true) }
    // Briefly true after the remove dialog closes, so the selection settling back on the slot stays quiet.
    var quietRefocus by remember { mutableStateOf(false) }
    val onTileFocused = {
        when {
            initialFocus -> initialFocus = false
            !quietRefocus -> sounds.playCardMove()
        }
    }
    // Follows the selection: moving onto a tile from the other page slides the strip over.
    var page by remember { mutableIntStateOf(startSlot / PAGE_SIZE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wiiBackdrop()
    ) {
        // Both pages sit side by side in one long strip. The current page's columns are centered,
        // with the other page's nearest column peeking into the margin.
        BoxWithConstraints(
            // Not clipped, so a selected card can grow past the bottom edge into the space above the bar.
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val tileWidth = (maxWidth - SideMargin * 2 - TileGap * (COLUMNS - 1)) / COLUMNS
            val tileHeight = tileWidth * TILE_HEIGHT_RATIO
            // Sliding by exactly one page lands the next page's columns in the same centered spot.
            val pageWidth = (tileWidth + TileGap) * COLUMNS
            val slide by animateDpAsState(pageWidth * page, label = "page slide")

            Column(
                verticalArrangement = Arrangement.spacedBy(TileGap, Alignment.Bottom),
                modifier = Modifier
                    .fillMaxHeight()
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .offset(x = SideMargin - slide),
            ) {
                repeat(ROWS) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                        repeat(COLUMNS * PAGE_COUNT) { stripColumn ->
                            val tilePage = stripColumn / COLUMNS
                            // Slots are saved page by page, so map the strip position back to that order.
                            val slot = tilePage * PAGE_SIZE + row * COLUMNS + stripColumn % COLUMNS
                            val channel = store.slots[slot]
                            val tileModifier = Modifier
                                .size(tileWidth, tileHeight)
                                .focusRequester(tileFocus[slot])
                                .focusProperties {
                                    // At the strip's outer edges: the bottom row steps down to the corner
                                    // button on that side; the rows above go nowhere.
                                    val bottomRow = row == ROWS - 1
                                    if (stripColumn == 0) {
                                        left = if (bottomRow) settingsFocus else FocusRequester.Cancel
                                    }
                                    if (stripColumn == COLUMNS * PAGE_COUNT - 1) {
                                        right = if (bottomRow) musicFocus else FocusRequester.Cancel
                                    }
                                }
                            val onFocused = {
                                page = tilePage
                                onTileFocused()
                            }
                            if (channel == null) {
                                EmptyTile(
                                    onClick = {
                                        sounds.playAppSelect()
                                        onAddApp(slot)
                                    },
                                    onFocused = onFocused,
                                    modifier = tileModifier,
                                )
                            } else if (channel.packageName == WEATHER_ID) {
                                ForecastTile(
                                    store = weather,
                                    onClick = {
                                        sounds.playAppSelect()
                                        onOpenWeather(slot)
                                    },
                                    onLongClick = { removingSlot = slot },
                                    onFocused = onFocused,
                                    modifier = tileModifier,
                                )
                            } else if (channel.packageName == NEWS_ID) {
                                ChannelTile(
                                    channel = channel,
                                    onClick = {
                                        sounds.playAppSelect()
                                        onOpenNews(slot)
                                    },
                                    onLongClick = { removingSlot = slot },
                                    onFocused = onFocused,
                                    modifier = tileModifier,
                                )
                            } else {
                                ChannelTile(
                                    channel = channel,
                                    onClick = {
                                        sounds.playAppSelect()
                                        launchApp(context, channel.packageName)
                                    },
                                    // Hold OK on the remote to ask about taking an app off the dashboard.
                                    onLongClick = { removingSlot = slot },
                                    onFocused = onFocused,
                                    modifier = tileModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
        ClockBar(
            onSettingsClick = {
                sounds.playOpenMenu()
                onOpenSettings()
            },
            onMusicClick = {
                sounds.playOpenMenu()
                onOpenMusic()
            },
            onMusicLongClick = {
                sounds.playAppSelect()
                onToggleMute()
            },
            musicMuted = musicMuted,
            onButtonFocused = { sounds.playCardMove() },
            settingsFocus = settingsFocus,
            musicFocus = musicFocus,
            // Up from a corner button goes to the bottom card on that side of the page you're on, rather than
            // whichever card happens to be nearest, which can be the next page's peeking card.
            settingsUp = tileFocus[page * PAGE_SIZE + (ROWS - 1) * COLUMNS],
            musicUp = tileFocus[page * PAGE_SIZE + ROWS * COLUMNS - 1],
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
        )
    }

    store.slots.getOrNull(removingSlot)?.let { channel ->
        val slot = removingSlot
        RemoveChannelDialog(
            channel = channel,
            onConfirm = {
                sounds.playAppSelect()
                store.remove(slot)
                removingSlot = -1
                quietRefocus = true
                refocusSlot = slot
            },
            onDismiss = {
                sounds.playBack()
                removingSlot = -1
                quietRefocus = true
                refocusSlot = slot
            },
            onButtonMove = sounds::playCardMove,
        )
    }

    LaunchedEffect(Unit) { tileFocus[startSlot].requestFocus() }
    LaunchedEffect(refocusSlot) {
        if (refocusSlot >= 0) {
            tileFocus[refocusSlot].requestFocus()
            refocusSlot = -1
        }
    }
    // Focus can bounce more than once while the dialog's window closes, so stay quiet a moment longer.
    LaunchedEffect(quietRefocus) {
        if (quietRefocus) {
            delay(500)
            quietRefocus = false
        }
    }
}
