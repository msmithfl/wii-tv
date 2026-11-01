package com.example.mysmarttv.museum

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.mysmarttv.home.Sounds
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// The Museum Channel's own colors: cream gallery walls and deep red bars with a gold rim.
// They stay the same in light and dark mode, like the News Channel's.
private val Wall = Brush.verticalGradient(listOf(Color(0xFFEFE9DF), Color(0xFFD9D0C2)))
private val BarFill = Brush.verticalGradient(listOf(Color(0xFF2A1216), Color(0xFF471D24), Color(0xFF66303A)))
private val BarEdge = Color(0xFFD2B06A)
private val BarDivider = Color(0xFF8A5A50)
private val BandRed = Brush.horizontalGradient(listOf(Color(0xFF5A222B), Color(0xFF7A3440), Color(0x007A3440)))
private val Gold = Color(0xFFB8913F)
private val TitleUnderline = Brush.horizontalGradient(listOf(Gold, Color(0xFFE2C887), Color(0x00E2C887)))
private val RowWhite = Color(0xFFF6F2EA)
private val RowSelected = Color(0xFF93CBD1)
private val Mat = Color(0xFF2B2624)
private val Ink = Color(0xFF1E1714)
private val SoftInk = Color(0xFF5E5650)

/**
 * The Museum Channel, built like the News Channel: pick a gallery, then a piece, then see it up close.
 * Back steps out one level at a time, and from the gallery list returns to the dashboard.
 * [startArtwork] opens straight to that piece, as when holding OK on a painting in the screensaver.
 */
@Composable
fun MuseumScreen(store: MuseumStore, sounds: Sounds, startArtwork: Artwork? = null, onBack: () -> Unit) {
    // -1 means nothing is open at that level.
    var sectionIndex by rememberSaveable { mutableIntStateOf(-1) }
    var pieceIndex by rememberSaveable { mutableIntStateOf(-1) }
    // Where the selection was, so stepping back lands on the row or frame you came from.
    var lastSection by rememberSaveable { mutableIntStateOf(0) }
    var lastPiece by rememberSaveable { mutableIntStateOf(0) }
    // A piece opened on its own, outside any gallery.
    var single by remember { mutableStateOf(startArtwork) }
    var fullScreen by remember { mutableStateOf(false) }

    BackHandler {
        sounds.playBack()
        when {
            fullScreen -> fullScreen = false
            single != null -> single = null
            pieceIndex >= 0 -> pieceIndex = -1
            sectionIndex >= 0 -> sectionIndex = -1
            else -> onBack()
        }
    }

    val section = MuseumSection.entries.getOrNull(sectionIndex)
    val pieces = single?.let { listOf(it) } ?: section?.let { store.galleries[it] }.orEmpty()
    val piece = if (single != null) 0 else pieceIndex
    // Opening a gallery loads it, if it isn't loaded yet today.
    LaunchedEffect(section) { section?.let { store.load(it) } }

    if (fullScreen && piece in pieces.indices) {
        FullScreenView(pieces[piece])
        return
    }
    Column(Modifier.fillMaxSize().background(Wall)) {
        when {
            piece in pieces.indices -> ArtworkPage(pieces, piece, sounds, onFullScreen = {
                sounds.playAppSelect()
                fullScreen = true
            }) { pieceIndex = it }
            section == null -> SectionList(store, sounds, startAt = lastSection, onOpenToday = {
                sounds.playAppSelect()
                lastSection = 0
                lastPiece = 0
                sectionIndex = 0
                pieceIndex = 0
            }) {
                sounds.playAppSelect()
                lastSection = it
                lastPiece = 0
                sectionIndex = it
            }
            else -> GalleryGrid(
                section = section,
                pieces = pieces,
                emptyMessage = if (section in store.failed) {
                    "Couldn't reach the museum.\nCheck the TV's internet connection."
                } else {
                    "Loading…"
                },
                sounds = sounds,
                startAt = lastPiece,
            ) {
                sounds.playAppSelect()
                lastPiece = it
                pieceIndex = it
            }
        }
    }
}

@Composable
private fun ColumnScope.SectionList(
    store: MuseumStore,
    sounds: Sounds,
    startAt: Int,
    onOpenToday: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    val rows = remember { List(MuseumSection.entries.size) { FocusRequester() } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startAt)
    val onRowFocused = quietFirstFocus(sounds)
    BarRow(top = true, middle = clockTime())
    Spacer(Modifier.height(20.dp))
    ArtworkOfTheDay(store.artworkOfTheDay, onClick = onOpenToday, onFocused = onRowFocused)
    Spacer(Modifier.height(20.dp))
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth().height(60.dp).background(BandRed),
    ) {
        Text("Select a Gallery", fontSize = 28.sp, color = Color.White, modifier = Modifier.padding(start = 40.dp))
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
        itemsIndexed(MuseumSection.entries) { index, section ->
            ListRow(
                onClick = { onOpen(index) },
                onFocused = onRowFocused,
                modifier = Modifier.width(620.dp).height(60.dp).focusRequester(rows[index]),
            ) {
                Text(
                    text = section.title,
                    fontSize = 28.sp,
                    color = Ink,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp),
                )
            }
        }
    }
    BarRow(top = false, left = "Home", middle = "▼")
    LaunchedEffect(Unit) { rows[startAt].requestFocus() }
}

/** The day's featured piece across the top of the gallery list. Pressing it opens Today's Gallery at that piece. */
@Composable
private fun ArtworkOfTheDay(artwork: Artwork?, onClick: () -> Unit, onFocused: () -> Unit) {
    ListRow(
        onClick = onClick,
        onFocused = onFocused,
        modifier = Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(170.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Box(Modifier.size(width = 240.dp, height = 142.dp).background(Mat)) {
                artwork?.let { ArtworkImage(it.smallImageUrl, Modifier.fillMaxSize().padding(6.dp), maxSize = 800) }
            }
            Column(Modifier.weight(1f).padding(start = 24.dp)) {
                Text("Artwork of the Day", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Gold, letterSpacing = 1.sp)
                Text(
                    text = artwork?.title ?: "Opening the museum…",
                    fontSize = 30.sp,
                    fontFamily = FontFamily.Serif,
                    color = Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (artwork != null) {
                    Text(
                        text = listOf(artwork.maker, artwork.date).filter { it.isNotEmpty() }.joinToString(", "),
                        fontSize = 20.sp,
                        color = SoftInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A gallery's pieces in framed rows of four, each with its title and who made it. */
@Composable
private fun ColumnScope.GalleryGrid(
    section: MuseumSection,
    pieces: List<Artwork>,
    emptyMessage: String,
    sounds: Sounds,
    startAt: Int,
    onOpen: (Int) -> Unit,
) {
    val frames = remember(pieces.size) { List(pieces.size) { FocusRequester() } }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = startAt)
    val onFrameFocused = quietFirstFocus(sounds)
    BarRow(top = true, middle = clockTime())
    SectionTitle(section.title)
    if (pieces.isEmpty()) {
        Message(emptyMessage, Modifier.weight(1f))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(pieces) { index, artwork ->
                ListRow(
                    onClick = { onOpen(index) },
                    onFocused = onFrameFocused,
                    modifier = Modifier.fillMaxWidth().height(250.dp).focusRequester(frames[index]),
                ) {
                    Column(Modifier.fillMaxSize().padding(10.dp)) {
                        Box(Modifier.weight(1f).fillMaxWidth().background(Mat)) {
                            ArtworkImage(artwork.smallImageUrl, Modifier.fillMaxSize().padding(6.dp), maxSize = 800)
                        }
                        Text(
                            text = artwork.title,
                            fontSize = 18.sp,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(artwork.maker, fontSize = 15.sp, color = SoftInk, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { frames[startAt.coerceIn(frames.indices)].requestFocus() }
    }
    BarRow(top = false, left = "Back", middle = "▼")
}

/**
 * One piece: its picture on the left, and its label on the right, like the card beside a painting in a museum.
 * Left and right step to the previous or next piece; OK shows the picture full screen.
 */
@Composable
private fun ColumnScope.ArtworkPage(
    pieces: List<Artwork>,
    index: Int,
    sounds: Sounds,
    onFullScreen: () -> Unit,
    onGoTo: (Int) -> Unit,
) {
    val artwork = pieces[index]
    val page = remember { FocusRequester() }
    BarRow(top = true, middle = clockTime())
    Row(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_UP) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (index > 0) {
                        sounds.playCardMove()
                        onGoTo(index - 1)
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (index < pieces.lastIndex) {
                        sounds.playCardMove()
                        onGoTo(index + 1)
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> onFullScreen()
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(page)
            .focusable()
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(0.58f).fillMaxHeight()) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Mat).border(2.dp, Gold)) {
                ArtworkImage(
                    url = artwork.imageUrl,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    placeholder = "Loading…",
                    placeholderColor = Color.White.copy(alpha = 0.6f),
                )
            }
            Text("Press OK to see it full screen", fontSize = 16.sp, color = SoftInk, modifier = Modifier.padding(top = 8.dp))
        }
        Column(Modifier.weight(0.42f).padding(start = 40.dp)) {
            Text(
                text = artwork.title,
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                color = Ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (artwork.maker.isNotEmpty()) {
                Text(artwork.maker, fontSize = 24.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
            }
            if (artwork.artistBio.isNotEmpty()) Text(artwork.artistBio, fontSize = 17.sp, color = SoftInk)
            Box(Modifier.padding(vertical = 16.dp).fillMaxWidth().height(4.dp).background(TitleUnderline))
            Fact("Date", artwork.date)
            Fact("Medium", artwork.medium)
            Fact("Size", artwork.dimensions)
            // The culture is already shown as the maker when there's no artist.
            if (artwork.artist.isNotEmpty()) Fact("Culture", artwork.culture)
            Fact("Department", artwork.department)
            Fact("On view", if (artwork.gallery.isNotEmpty()) "Gallery ${artwork.gallery}, The Met" else "Not on display right now")
            Spacer(Modifier.weight(1f))
            Text(artwork.creditLine, fontSize = 13.sp, color = SoftInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
    BarRow(
        top = false,
        left = "Back",
        middle = if (pieces.size > 1) "${index + 1} / ${pieces.size}" else "",
        right = if (index < pieces.lastIndex) "Next ▶" else "",
    )
    LaunchedEffect(Unit) { page.requestFocus() }
}

// One line of the label, like "Medium  Oil on canvas". Left out when the museum doesn't say.
@Composable
private fun Fact(label: String, value: String) {
    if (value.isEmpty()) return
    Row(Modifier.padding(bottom = 8.dp)) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Gold, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 18.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The picture alone, as large as the screen allows, on black. Back returns to the label. */
@Composable
private fun FullScreenView(artwork: Artwork) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        ArtworkImage(
            url = artwork.imageUrl,
            modifier = Modifier.fillMaxSize(),
            placeholder = "Loading…",
            placeholderColor = Color.White.copy(alpha = 0.6f),
        )
    }
}

/** The dark red bar along the top or bottom, split into three panels, with an optional label in each. */
@Composable
private fun BarRow(top: Boolean, left: String = "", middle: String = "", right: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(BarFill)
            .drawBehind {
                // A gold rim on the edge that faces the page.
                val y = if (top) size.height - 1.5.dp.toPx() else 1.5.dp.toPx()
                drawLine(BarEdge, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
            },
    ) {
        BarPanel(left, Modifier.weight(0.3f))
        BarDividerLine()
        BarPanel(middle, Modifier.weight(0.4f))
        BarDividerLine()
        BarPanel(right, Modifier.weight(0.3f))
    }
}

@Composable
private fun BarPanel(label: String, modifier: Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxHeight()) {
        Text(label, fontSize = 26.sp, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun RowScope.BarDividerLine() {
    Box(Modifier.width(3.dp).fillMaxHeight().background(BarDivider))
}

/** The gallery's name on a white band with a fading gold line under it. */
@Composable
private fun SectionTitle(title: String) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .height(76.dp)
            .background(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0.6f), Color.Transparent))),
    ) {
        Text(
            text = title,
            fontSize = 40.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium,
            color = Ink,
            modifier = Modifier.padding(start = 56.dp),
        )
    }
    Box(Modifier.fillMaxWidth().height(8.dp).background(TitleUnderline))
}

/** A selectable row or frame: off-white, turning blue-green when selected. It doesn't grow. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ListRow(
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = RowWhite,
            focusedContainerColor = RowSelected,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = content,
    )
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        Text(text, fontSize = 28.sp, color = SoftInk, textAlign = TextAlign.Center)
    }
}

// A list's first row is selected on its own when it opens; only play the move sound for real moves.
@Composable
private fun quietFirstFocus(sounds: Sounds): () -> Unit {
    var first by remember { mutableStateOf(true) }
    return { if (first) first = false else sounds.playCardMove() }
}

private val clockFormat = DateTimeFormatter.ofPattern("h:mm a")

/** The time right now, like the dashboard's clock ("8:35 PM"). Ticks along on its own. */
@Composable
private fun clockTime(): String {
    val now by produceState(LocalTime.now()) {
        while (true) {
            delay(1_000)
            value = LocalTime.now()
        }
    }
    return now.format(clockFormat)
}
