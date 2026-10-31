package com.example.mysmarttv.weather

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.R
import com.example.mysmarttv.home.TileShape
import com.example.mysmarttv.home.TileSurface
import com.example.mysmarttv.home.rememberBuiltInBanner

/**
 * The Forecast Channel's dashboard tile: its banner, with the weather right now drawn in the space
 * under the title that's part of the image. Pressing it opens the full forecast.
 */
@Composable
fun ForecastTile(
    store: WeatherStore,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val weather = store.weather
    TileSurface(onClick = onClick, onLongClick = onLongClick, onFocused = onFocused, modifier = modifier) {
        Image(
            bitmap = rememberBuiltInBanner(R.drawable.forecast_app),
            contentDescription = "Forecast Channel",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(TileShape),
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(TitleShare))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .weight(1f - TitleShare)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                if (weather == null) {
                    Text(
                        text = if (store.failed) "Weather unavailable" else "Loading…",
                        style = ShadowedText,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                } else {
                    Text(text = weather.current.condition.symbol, fontSize = 32.sp)
                    TempOverCondition(
                        temperature = weather.current.temperature.toString(),
                        condition = weather.current.condition.description,
                    )
                }
            }
        }
    }
}

// How much of the card, from the top, is kept clear for the title in forecast_app.jpg. The weather is centered
// in the space below. Lower it to move the weather up, raise it to move it down.
private const val TitleShare = 0.27f

/**
 * The temperature centered over the conditions. The ° hangs off to the right of the number and doesn't
 * count toward the centering, so the digits themselves sit in the middle.
 */
@Composable
private fun TempOverCondition(temperature: String, condition: String) {
    Layout(
        content = {
            Text(text = temperature, style = ShadowedText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(text = "°", style = ShadowedText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(
                text = condition,
                style = ShadowedText,
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val number = measurables[0].measure(loose)
        val degree = measurables[1].measure(loose)
        val conditionText = measurables[2].measure(loose)
        val width = maxOf(number.width, conditionText.width)
        layout(width, number.height + conditionText.height) {
            val numberLeft = (width - number.width) / 2
            number.place(numberLeft, 0)
            degree.place(numberLeft + number.width, 0)
            conditionText.place((width - conditionText.width) / 2, number.height)
        }
    }
}
