package com.example.mysmarttv.weather

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayNameStyle
import java.util.Locale

// White text on the blue panel gets a soft drop shadow so it stands off the gradient.
internal val ShadowedText = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(2f, 3f), 4f))

private val HighYellow = Color(0xFFFFC61A)
private val LowCyan = Color(0xFF8AF3F0)
private val ColumnHeader = Color(0xFF0A2C7A)
private val ColumnBody = Brush.verticalGradient(listOf(Color(0xFF1C7FE0), Color(0xFF0A3FAE)))
private val ColumnEdge = Color(0xFF5EB8F5)
private val WindBand = Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF071A6E)))

private val hourFormat = DateTimeFormatter.ofPattern("h a")

/** Right now: the temperature in white, what it's like, and the wind. */
@Composable
internal fun CurrentPage(current: CurrentWeather) {
    BigReading(
        temperature = { BigTemperature(current.temperature, Color.White) },
        description = current.condition.description,
        symbol = current.condition.symbol,
        wind = "${current.windDirection}  ${current.windMph} mph",
    )
}

/** One day's forecast: the high in yellow with the low in cyan beside it. */
@Composable
internal fun DayPage(day: DailyForecast) {
    BigReading(
        temperature = {
            Row(verticalAlignment = Alignment.Bottom) {
                BigTemperature(day.high, HighYellow)
                Text(
                    text = "${day.low}°",
                    style = ShadowedText,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LowCyan,
                    modifier = Modifier.padding(start = 16.dp, bottom = 20.dp),
                )
            }
        },
        description = if (day.rainChance > 0) "${day.condition.description}\n${day.rainChance}% chance of rain" else day.condition.description,
        symbol = day.condition.symbol,
        wind = "${day.windDirection}  ${day.windMph} mph",
    )
}

/** Five columns, one per day: name, picture, high and low. Weekend names are yellow. */
@Composable
internal fun FiveDayPage(days: List<DailyForecast>) {
    ForecastColumns {
        days.forEach { day ->
            val weekend = day.date.dayOfWeek == DayOfWeek.SATURDAY || day.date.dayOfWeek == DayOfWeek.SUNDAY
            ForecastColumn(
                label = day.date.dayOfWeek.getDisplayName(DayNameStyle.SHORT, Locale.US).uppercase(),
                labelColor = if (weekend) HighYellow else Color.White,
                labelSize = 32.sp,
                symbol = day.condition.symbol,
                symbolSize = 60.sp,
                values = listOf("${day.high}" to HighYellow, "${day.low}" to LowCyan),
                valueSize = 44.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The next 12 hours as narrow columns. */
@Composable
internal fun HourlyPage(hours: List<HourlyForecast>) {
    ForecastColumns(spacing = 6.dp) {
        hours.forEach { hour ->
            ForecastColumn(
                label = hour.time.format(hourFormat),
                labelColor = Color.White,
                labelSize = 17.sp,
                symbol = hour.condition.symbol,
                symbolSize = 34.sp,
                values = listOf("${hour.temperature}°" to HighYellow),
                valueSize = 28.sp,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Left half: temperature and description. Right half: a big weather picture, with the wind under it. */
@Composable
private fun BigReading(
    temperature: @Composable () -> Unit,
    description: String,
    symbol: String,
    wind: String,
) {
    Box(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize().padding(bottom = 40.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                temperature()
                Text(
                    text = description,
                    style = ShadowedText,
                    fontSize = 30.sp,
                    lineHeight = 38.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) {
                Text(text = symbol, fontSize = 110.sp)
            }
        }
        // Sits on a dark band just above the red "as of" tag.
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 28.dp)
                .width(420.dp)
                .background(WindBand),
        ) {
            Text(
                text = wind,
                style = ShadowedText,
                fontSize = 30.sp,
                color = Color.White,
                modifier = Modifier.padding(end = 40.dp),
            )
        }
    }
}

@Composable
private fun BigTemperature(value: Int, color: Color) {
    Text(
        text = "$value°",
        style = ShadowedText,
        fontSize = 96.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

@Composable
private fun ForecastColumns(spacing: Dp = 10.dp, content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(spacing),
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 40.dp, end = 40.dp, top = 10.dp, bottom = 36.dp),
        content = content,
    )
}

@Composable
private fun ForecastColumn(
    label: String,
    labelColor: Color,
    labelSize: TextUnit,
    symbol: String,
    symbolSize: TextUnit,
    values: List<Pair<String, Color>>,
    valueSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, ColumnEdge)
            .background(ColumnBody),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(ColumnHeader)
                .padding(vertical = 4.dp),
        ) {
            Text(text = label, style = ShadowedText, fontSize = labelSize, fontWeight = FontWeight.Bold, color = labelColor)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.weight(1f),
        ) {
            Text(text = symbol, fontSize = symbolSize)
            values.forEach { (text, color) ->
                Text(text = text, style = ShadowedText, fontSize = valueSize, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}
