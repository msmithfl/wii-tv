package com.example.mysmarttv.museum

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.home.TileShape
import com.example.mysmarttv.home.TileSurface
import com.example.mysmarttv.weather.ShadowedText

// Shown behind the picture while it loads, and around the edges of the card's title.
private val CardRed = Color(0xFF4A1E25)

/**
 * The Museum Channel's dashboard tile: the day's artwork filling the card, with the channel's name across the top.
 * Pressing it opens the Museum Channel.
 */
@Composable
fun MuseumTile(
    store: MuseumStore,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val artwork = store.artworkOfTheDay
    TileSurface(onClick = onClick, onLongClick = onLongClick, onFocused = onFocused, modifier = modifier) {
        Box(Modifier.fillMaxSize().clip(TileShape).background(CardRed)) {
            artwork?.let {
                ArtworkImage(it.smallImageUrl, Modifier.fillMaxSize(), maxSize = 800, contentScale = ContentScale.Crop)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(CardRed, CardRed.copy(alpha = 0.85f), Color.Transparent)))
                    .padding(top = 6.dp, bottom = 12.dp),
            ) {
                Text("Museum Channel", style = ShadowedText, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}
