package com.example.mysmarttv.weather

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.mysmarttv.home.Sounds
import com.example.mysmarttv.ui.theme.WiiBarEdge
import com.example.mysmarttv.ui.theme.WiiButtonFace
import com.example.mysmarttv.ui.theme.WiiDateText
import com.example.mysmarttv.ui.theme.WiiEmptyBottom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// The forecast screen's own colors. They stay the same in light and dark mode, like a TV weather broadcast.
private val HeaderDarkGreen = Brush.horizontalGradient(listOf(Color(0xFF0B6E1C), Color(0xFF13932A)))
private val HeaderPillGreen = Brush.verticalGradient(listOf(Color(0xFF2EE06A), Color(0xFF0DB443)))
private val HeaderPillEdge = Color(0xFF0A3FA8)
private val TopStripe = Color(0xFF1E9BE0)
private val PanelBlue = Brush.horizontalGradient(listOf(Color(0xFF13A6EE), Color(0xFF0B62CC), Color(0xFF071890)))
private val SelectedCell = Color(0xFF2AB4EA)
private val AsOfRed = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFFB0103A), Color(0xFFC8102E)))

private val PillShape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp)
private val asOfFormat = DateTimeFormatter.ofPattern("h:mm a, M/d")

/** The pages, in the order the ▲ and ▼ buttons step through them. Stepping past either end wraps around. */
private enum class WeatherPage(val buttonLabel: String) {
    Current("Current"),
    Today("Today"),
    Tomorrow("Tomorrow"),
    FiveDay("5-Day Forecast"),
    Hourly("12-Hour Forecast"),
}

/**
 * The full forecast, styled like a TV weather report: a green title strip, a blue panel, and button bars
 * above and below. ▲ and ▼ flip between pages; Home or Back returns to the dashboard.
 */
@Composable
fun WeatherScreen(store: WeatherStore, sounds: Sounds, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val weather = store.weather
    var pageIndex by rememberSaveable { mutableIntStateOf(0) }
    val pages = WeatherPage.entries
    val page = pages[pageIndex]
    val previous = pages[(pageIndex - 1 + pages.size) % pages.size]
    val next = pages[(pageIndex + 1) % pages.size]
    val nextButton = remember { FocusRequester() }
    // The next-page button is selected when the screen opens; only play the move sound for real moves.
    var firstFocus by remember { mutableStateOf(true) }
    val onCellFocused: () -> Unit = {
        if (firstFocus) firstFocus = false else sounds.playCardMove()
    }

    Column(modifier = Modifier.fillMaxSize().background(WiiEmptyBottom)) {
        ButtonBar {
            BlankCell()
            BarCell(
                label = "▲   ${previous.buttonLabel}",
                onClick = {
                    sounds.playAppSelect()
                    pageIndex = previous.ordinal
                },
                onFocused = onCellFocused,
            )
            BlankCell()
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(TopStripe))
        TitleStrip(
            title = weather?.let { pageTitle(page, it) } ?: "FORECAST",
            place = weather?.place?.substringBefore(",") ?: "",
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(PanelBlue),
        ) {
            if (weather == null) {
                PanelMessage(if (store.failed) "Couldn't get the weather.\nCheck the TV's internet connection." else "Loading…")
            } else {
                when (page) {
                    WeatherPage.Current -> CurrentPage(weather.current)
                    WeatherPage.Today -> DayPage(weather.daily[0])
                    WeatherPage.Tomorrow -> DayPage(weather.daily[1])
                    WeatherPage.FiveDay -> FiveDayPage(weather.daily.take(5))
                    WeatherPage.Hourly -> HourlyPage(weather.hourly)
                }
                AsOfTag(weather.current, Modifier.align(Alignment.BottomEnd))
            }
        }
        ButtonBar {
            BarCell(
                label = "Home",
                // Leaving plays the back sound, which onBack takes care of.
                onClick = onBack,
                onFocused = onCellFocused,
            )
            BarCell(
                label = "▼   ${next.buttonLabel}",
                onClick = {
                    sounds.playAppSelect()
                    pageIndex = next.ordinal
                },
                onFocused = onCellFocused,
                modifier = Modifier.focusRequester(nextButton),
            )
            BlankCell()
        }
    }

    LaunchedEffect(Unit) { nextButton.requestFocus() }
}

/** What the green strip says: the weekday for current conditions, TODAY, tomorrow's weekday, or the forecast name. */
private fun pageTitle(page: WeatherPage, weather: Weather): String = when (page) {
    WeatherPage.Current -> weekday(weather.current.asOf.toLocalDate())
    WeatherPage.Today -> "TODAY"
    WeatherPage.Tomorrow -> weekday(weather.daily[1].date)
    WeatherPage.FiveDay -> "5-DAY\nFORECAST"
    WeatherPage.Hourly -> "12-HOUR\nFORECAST"
}

private fun weekday(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US).uppercase()

/** A row of three grey buttons, split by thin dividers. */
@Composable
private fun ButtonBar(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Brush.verticalGradient(listOf(WiiButtonFace, WiiEmptyBottom))),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        content()
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RowScope.BarCell(label: String, onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .weight(1f)
            .fillMaxHeight()
            .onFocusChanged { if (it.hasFocus) onFocused() }
            .cellDivider(),
        shape = ClickableSurfaceDefaults.shape(RectangleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = WiiDateText,
            focusedContainerColor = SelectedCell,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            text = label,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun RowScope.BlankCell() {
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .cellDivider()
    )
}

// A thin line down each side, so the bar reads as separate buttons.
@Composable
private fun Modifier.cellDivider(): Modifier = border(1.dp, WiiBarEdge.copy(alpha = 0.5f))

/** Dark green block with the page title, then a bright green pill with the place name. */
@Composable
private fun TitleStrip(title: String, place: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(HeaderDarkGreen),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.width(250.dp)) {
            Text(
                text = title,
                style = ShadowedText,
                fontSize = if ('\n' in title) 26.sp else 38.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .border(2.dp, HeaderPillEdge, PillShape)
                .background(HeaderPillGreen, PillShape),
        ) {
            Text(
                text = place,
                style = ShadowedText,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(end = 40.dp),
            )
        }
    }
}

/** The red tag in the panel's bottom corner saying when the reading was taken. */
@Composable
private fun AsOfTag(current: CurrentWeather, modifier: Modifier = Modifier) {
    // "11:15 PM" reads as "11:15 p.m.", the way a weather report writes it.
    val time = current.asOf.format(asOfFormat).replace("AM", "a.m.").replace("PM", "p.m.")
    Box(
        contentAlignment = Alignment.CenterEnd,
        modifier = modifier
            .width(420.dp)
            .height(28.dp)
            .background(AsOfRed),
    ) {
        Text(
            text = "As of $time",
            fontSize = 17.sp,
            color = Color.White,
            modifier = Modifier.padding(end = 40.dp),
        )
    }
}

@Composable
private fun PanelMessage(text: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text(text = text, style = ShadowedText, fontSize = 26.sp, color = Color.White, textAlign = TextAlign.Center)
    }
}
