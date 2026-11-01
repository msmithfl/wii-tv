package com.example.mysmarttv.news

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.mysmarttv.settings.AppSettings
import com.example.mysmarttv.settings.NEWS_TEXT_SIZES
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// The News Channel's own colors. They stay the same in light and dark mode, like the Wii's.
private val Paper = Brush.verticalGradient(listOf(Color(0xFFE6E6E1), Color(0xFFCDCEC8)))
private val BarFill = Brush.verticalGradient(listOf(Color(0xFF1C221F), Color(0xFF34423A), Color(0xFF4F665A)))
private val BarEdge = Color(0xFF9CC0AA)
private val BarDivider = Color(0xFF6F8C7B)
private val BandGreen = Brush.horizontalGradient(listOf(Color(0xFF365A48), Color(0xFF4A6E5B), Color(0x004A6E5B)))
private val StripeDark = Color(0xFF2F5A45)
private val StripeLight = Color(0xFF4F7D66)
private val BannerWhite = Color(0xFFF3F3EF)
private val RowWhite = Color(0xFFEFEFEB)
private val RowSelected = Color(0xFF93CBD1)
private val TitleUnderline = Brush.horizontalGradient(listOf(Color(0xFF5FB3C2), Color(0xFF9ED3DA), Color(0x009ED3DA)))
private val Ink = Color(0xFF151515)
private val SoftInk = Color(0xFF555852)

/**
 * The News Channel, styled like the Wii's: pick a section, then a headline, then read the story.
 * Back steps out one level at a time, and from the section list returns to the dashboard.
 */
@Composable
fun NewsScreen(store: NewsStore, settings: AppSettings, sounds: Sounds, onBack: () -> Unit) {
    // -1 means nothing is open at that level.
    var sectionIndex by rememberSaveable { mutableIntStateOf(-1) }
    var storyIndex by rememberSaveable { mutableIntStateOf(-1) }
    // Where the selection was, so stepping back lands on the row you came from.
    var lastSection by rememberSaveable { mutableIntStateOf(0) }
    var lastStory by rememberSaveable { mutableIntStateOf(0) }

    BackHandler {
        when {
            storyIndex >= 0 -> {
                sounds.playBack()
                storyIndex = -1
            }
            sectionIndex >= 0 -> {
                sounds.playBack()
                sectionIndex = -1
            }
            else -> onBack()
        }
    }

    val section = NewsSection.listed.getOrNull(sectionIndex)
    val stories = when (section) {
        null -> emptyList()
        NewsSection.Today -> store.todaysHeadlines
        else -> store.stories[section].orEmpty()
    }
    Column(Modifier.fillMaxSize().background(Paper)) {
        when {
            section == null -> SectionList(store, settings, sounds, startAt = lastSection) {
                sounds.playAppSelect()
                lastSection = it
                lastStory = 0
                sectionIndex = it
            }
            storyIndex in stories.indices -> StoryPage(stories, storyIndex, settings, sounds) { storyIndex = it }
            else -> HeadlineList(section, stories, emptyMessage(store), sounds, startAt = lastStory) {
                sounds.playAppSelect()
                lastStory = it
                storyIndex = it
            }
        }
    }
}

@Composable
private fun ColumnScope.SectionList(
    store: NewsStore,
    settings: AppSettings,
    sounds: Sounds,
    startAt: Int,
    onOpen: (Int) -> Unit,
) {
    val rows = remember { List(NewsSection.listed.size) { FocusRequester() } }
    // Opens scrolled to the row being returned to, so it's on screen to be selected.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startAt)
    val onRowFocused = quietFirstFocus(sounds)
    // Up from the top of the list reaches this: whether the dashboard card scrolls headlines.
    BarRow(
        top = true,
        middle = clockTime(),
        right = "Card Headlines: ${if (settings.newsCardHeadlines) "On" else "Off"}",
        onRightClick = {
            sounds.playAppSelect()
            settings.toggleNewsCardHeadlines()
        },
        onButtonFocused = onRowFocused,
    )
    Spacer(Modifier.height(22.dp))
    UpdatedBanner(store.updatedAt)
    Spacer(Modifier.height(22.dp))
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier.fillMaxWidth().height(64.dp).background(BandGreen),
    ) {
        Text("Select a Section", fontSize = 28.sp, color = Color.White, modifier = Modifier.padding(start = 40.dp))
    }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
        itemsIndexed(NewsSection.listed) { index, section ->
            ListRow(
                onClick = { onOpen(index) },
                onFocused = onRowFocused,
                modifier = Modifier.width(620.dp).height(64.dp).focusRequester(rows[index]),
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

@Composable
private fun ColumnScope.HeadlineList(
    section: NewsSection,
    stories: List<Story>,
    emptyMessage: String,
    sounds: Sounds,
    startAt: Int,
    onOpen: (Int) -> Unit,
) {
    val rows = remember(stories.size) { List(stories.size) { FocusRequester() } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startAt)
    val onRowFocused = quietFirstFocus(sounds)
    BarRow(top = true, middle = clockTime())
    SectionTitle(section.title)
    if (stories.isEmpty()) {
        Message(emptyMessage, Modifier.weight(1f))
    } else {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 12.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(stories) { index, story ->
                ListRow(
                    onClick = { onOpen(index) },
                    onFocused = onRowFocused,
                    modifier = Modifier.fillMaxWidth().height(112.dp).focusRequester(rows[index]),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize().padding(start = 20.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        // A globe for world news and a newspaper for the rest, like the Wii's.
                        Text(if ((story.section ?: section) == NewsSection.International) "🌐" else "📰", fontSize = 24.sp)
                        Text(
                            text = story.title,
                            fontSize = 26.sp,
                            color = Ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                        )
                        // Some stories come without a picture; those just leave the space empty.
                        if (story.imageUrl != null) {
                            NewsImage(story.imageUrl, Modifier.size(width = 170.dp, height = 96.dp))
                        }
                    }
                }
            }
        }
        LaunchedEffect(Unit) { rows[startAt.coerceIn(rows.indices)].requestFocus() }
    }
    BarRow(top = false, left = "Back", middle = "▼")
}

/**
 * One story, laid out like a newspaper page. Up and down scroll it; left and right step to the previous or next
 * story. Pressing up at the top of the story reaches the Text Zoom buttons in the top bar.
 */
@Composable
private fun ColumnScope.StoryPage(
    stories: List<Story>,
    index: Int,
    settings: AppSettings,
    sounds: Sounds,
    onGoTo: (Int) -> Unit,
) {
    val story = stories[index]
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val page = remember { FocusRequester() }
    val zoom = NEWS_TEXT_SIZES[settings.newsTextSize]
    LaunchedEffect(index) { scroll.scrollTo(0) }

    BarRow(
        top = true,
        left = "Text Zoom  ⊖",
        middle = clockTime(),
        right = "Text Zoom  ⊕",
        onLeftClick = {
            sounds.playAppSelect()
            settings.changeNewsTextSize(-1)
        },
        onRightClick = {
            sounds.playAppSelect()
            settings.changeNewsTextSize(+1)
        },
        onButtonFocused = sounds::playCardMove,
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> if (index > 0) {
                        sounds.playCardMove()
                        onGoTo(index - 1)
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> if (index < stories.lastIndex) {
                        sounds.playCardMove()
                        onGoTo(index + 1)
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> scope.launch { scroll.animateScrollBy(ScrollStep) }
                    // Already at the top, up moves on to the zoom buttons instead.
                    KeyEvent.KEYCODE_DPAD_UP -> if (scroll.value == 0) {
                        return@onKeyEvent false
                    } else {
                        scope.launch { scroll.animateScrollBy(-ScrollStep) }
                    }
                    else -> return@onKeyEvent false
                }
                true
            }
            // Comes after the key handling above, so the page itself takes the remote's arrow presses.
            .focusRequester(page)
            .focusable()
            .verticalScroll(scroll)
            .padding(horizontal = 56.dp, vertical = 22.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.background(Color.White).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(story.source, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink, letterSpacing = 2.sp)
            }
            Spacer(Modifier.weight(1f))
            story.published?.let { Text("Updated ${ago(it.toInstant())} ago", fontSize = 22.sp, color = SoftInk) }
        }
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(6.dp).background(TitleUnderline))
        Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(2.dp).background(Ink))
        Text(
            text = story.title,
            fontSize = 34.sp * zoom,
            lineHeight = 42.sp * zoom,
            fontWeight = FontWeight.Medium,
            color = Ink,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
        )
        // The full text is fetched from the story's web page. Until it arrives, or if it can't be had, the
        // feed's summary stands in.
        val article by produceState<List<String>?>(null, story.link) {
            value = story.link?.let { runCatching { NewsClient.fetchArticle(it) }.getOrNull() }.orEmpty()
        }
        val paragraphs = article?.ifEmpty { null } ?: listOf(story.summary).filter { it.isNotBlank() }
        // The first couple of paragraphs sit beside the picture, the rest run the full width below it.
        val besidePicture = if (story.imageUrl != null) 2 else 0
        Row {
            Column(
                verticalArrangement = Arrangement.spacedBy(ParagraphGap),
                modifier = Modifier.weight(1f).padding(end = if (besidePicture > 0) 32.dp else 0.dp),
            ) {
                paragraphs.take(besidePicture.coerceAtLeast(1)).forEach { Paragraph(it, zoom) }
                if (article == null) Text("Loading the full story…", fontSize = 22.sp, color = SoftInk)
                // Some sites (often Hacker News links) block apps or only build their text in a browser.
                if (article != null && paragraphs.isEmpty()) {
                    Text("Couldn't load this story's text. The site may only work in a web browser.", fontSize = 22.sp, color = SoftInk)
                }
                // NPR's radio pieces are only written up as a summary, so say so rather than look cut short.
                if (article != null && paragraphs.size <= 1 && story.source == "NPR") {
                    Text("This story aired on the radio, so NPR only has a short summary.", fontSize = 22.sp, color = SoftInk)
                }
            }
            if (story.imageUrl != null) {
                NewsImage(story.imageUrl, Modifier.size(width = 360.dp, height = 220.dp))
            }
        }
        paragraphs.drop(besidePicture.coerceAtLeast(1)).forEach {
            Spacer(Modifier.height(ParagraphGap))
            Paragraph(it, zoom)
        }
    }
    BarRow(
        top = false,
        left = "Back",
        middle = "${index + 1} / ${stories.size}",
        right = if (index < stories.lastIndex) "Next ▶" else "",
    )
    LaunchedEffect(Unit) { page.requestFocus() }
}

private val ParagraphGap = 20.dp

// How far one press of up or down scrolls a story, in pixels: a few lines of text.
private const val ScrollStep = 300f

// [zoom] is the reader's chosen text size, as a share of the largest.
@Composable
private fun Paragraph(text: String, zoom: Float) {
    Text(text = text, fontSize = 28.sp * zoom, lineHeight = 40.sp * zoom, fontFamily = FontFamily.Serif, color = Ink)
}

/**
 * The dark bar along the top or bottom, split into three panels, with an optional label in each.
 * With [onLeftClick] or [onRightClick], that side's panel is a button that can be selected and pressed.
 */
@Composable
private fun BarRow(
    top: Boolean,
    left: String = "",
    middle: String = "",
    right: String = "",
    onLeftClick: (() -> Unit)? = null,
    onRightClick: (() -> Unit)? = null,
    onButtonFocused: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(BarFill)
            .drawBehind {
                // A light rim on the edge that faces the page.
                val y = if (top) size.height - 1.5.dp.toPx() else 1.5.dp.toPx()
                drawLine(BarEdge, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
            },
    ) {
        if (onLeftClick != null) {
            BarButton(left, onLeftClick, onButtonFocused, Modifier.weight(0.3f))
        } else {
            BarPanel(left, Modifier.weight(0.3f))
        }
        BarDividerLine()
        BarPanel(middle, Modifier.weight(0.4f))
        BarDividerLine()
        if (onRightClick != null) {
            BarButton(right, onRightClick, onButtonFocused, Modifier.weight(0.3f))
        } else {
            BarPanel(right, Modifier.weight(0.3f))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BarButton(label: String, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().onFocusChanged { if (it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White.copy(alpha = 0.9f),
            focusedContainerColor = RowSelected,
            focusedContentColor = Ink,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(label, fontSize = 24.sp, modifier = Modifier.align(Alignment.Center))
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

/** The white banner saying how long ago the news was fetched, over a striped green band. */
@Composable
private fun UpdatedBanner(updatedAt: Instant?) {
    Column(Modifier.padding(horizontal = 40.dp).fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(80.dp).background(BannerWhite)) {
            Text(
                text = updatedAt?.let { "Updated ${ago(it)} ago" } ?: "Getting the news…",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = Ink,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .drawBehind {
                    // Thin horizontal stripes, like the Wii's banner.
                    drawRect(StripeDark)
                    val stripe = 3.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawRect(StripeLight, Offset(0f, y), Size(size.width, stripe / 2))
                        y += stripe
                    }
                },
        ) {
            Text("News Channel", fontSize = 18.sp, color = Color.White)
        }
    }
}

/** The section's name on a white band with a fading blue line under it. */
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
        Text(title, fontSize = 40.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.padding(start = 56.dp))
    }
    Box(Modifier.fillMaxWidth().height(8.dp).background(TitleUnderline))
}

/** A selectable row: off-white, turning blue-green when selected. It doesn't grow. */
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

/** How long ago something was, as hours:minutes, the way the Wii shows it ("01:05"). Ticks along each minute. */
@Composable
private fun ago(time: Instant): String {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    val minutes = Duration.between(time, now).toMinutes().coerceAtLeast(0)
    return "%02d:%02d".format(minutes / 60, minutes % 60)
}

// What an empty section says: still waiting, no internet at all, or just this one feed not answering.
private fun emptyMessage(store: NewsStore): String = when {
    store.failed -> "Couldn't get the news.\nCheck the TV's internet connection."
    store.updatedAt != null -> "Couldn't get this section right now.\nIt'll try again in a few minutes."
    else -> "Loading…"
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
