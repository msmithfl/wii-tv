package com.example.mysmarttv.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.home.TileShape
import com.example.mysmarttv.home.TileSurface
import com.example.mysmarttv.home.rememberBuiltInBanner
import com.example.mysmarttv.weather.ShadowedText

/**
 * A gallery channel's dashboard tile: its banner picture, or the day's featured piece when it has none, with the
 * channel's name across the top. Pressing it opens the channel.
 */
@Composable
fun GalleryTile(
    store: GalleryStore,
    look: GalleryLook,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    TileSurface(onClick = onClick, onLongClick = onLongClick, onFocused = onFocused, modifier = modifier) {
        Box(Modifier.fillMaxSize().clip(TileShape).background(look.cardColor)) {
            if (look.banner != null) {
                Image(
                    bitmap = rememberBuiltInBanner(look.banner),
                    contentDescription = look.channelName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (look.hangFeatured) store.featured?.let { HungPicture(it.smallImageUrl) }
            } else {
                store.featured?.let {
                    ExhibitImage(it.smallImageUrl, Modifier.fillMaxSize(), maxSize = 800, contentScale = ContentScale.Crop)
                }
            }
            if (look.cardTitle) {
                Text(
                    text = look.channelName,
                    style = ShadowedText,
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .wrapContentWidth(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

// The hung picture's thin wooden frame and the pale mat inside it.
private val FrameWood = Color(0xFF4A3426)
private val FrameMat = Color(0xFFF3EFE7)

// How tall the hung picture is, as a share of the card, and how far below the middle it hangs, to sit in the
// middle of the wall in museum_app.jpg rather than the middle of the card.
private const val HUNG_HEIGHT = 0.44f
private val HungDrop = 3.dp

/**
 * A picture hanging in the middle of the card, in a frame the same shape as the picture, with a soft shadow on the
 * wall behind it. Nothing shows until the picture has downloaded.
 */
@Composable
private fun BoxScope.HungPicture(url: String) {
    val picture = rememberExhibitBitmap(url, maxSize = 800) ?: return
    Image(
        bitmap = picture,
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .align(Alignment.Center)
            .offset(y = HungDrop)
            .fillMaxHeight(HUNG_HEIGHT)
            .aspectRatio(picture.width.toFloat() / picture.height, matchHeightConstraintsFirst = true)
            .shadow(4.dp)
            .background(FrameWood)
            .padding(2.dp)
            .background(FrameMat)
            .padding(3.dp),
    )
}
