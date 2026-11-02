package com.example.mysmarttv.home

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.mysmarttv.R
import com.example.mysmarttv.ui.theme.WiiBlue
import com.example.mysmarttv.ui.theme.WiiClockText
import com.example.mysmarttv.ui.theme.WiiEmptyBottom
import com.example.mysmarttv.ui.theme.WiiEmptyTint
import com.example.mysmarttv.ui.theme.WiiEmptyTop
import com.example.mysmarttv.ui.theme.WiiSurface
import com.example.mysmarttv.ui.theme.WiiTileBorder

// Shaped like an old tube TV screen, with the edges bowing slightly outward, like the Wii's cards.
internal val TileShape = TubeShape()

// Banners for the channels built into MySmartTV, from res/drawable.
internal val builtInBanners = mapOf(
    WEATHER_ID to R.drawable.forecast_app,
    NEWS_ID to R.drawable.news_app,
    MUSEUM_ID to R.drawable.museum_app,
    SCIENCE_ID to R.drawable.science_app,
)

/**
 * The banner image for a channel's start screen, from res/drawable: MySmartTV's own channels' banners, or one dropped in for an
 * installed app, named banner_ plus its package name with the dots as underscores (banner_com_netflix_ninja.jpg).
 * Null if there's neither, so the app's own banner is used.
 */
@DrawableRes
internal fun bannerImage(context: Context, packageName: String): Int? {
    builtInBanners[packageName]?.let { return it }
    val name = "banner_" + packageName.lowercase().replace(Regex("[^a-z0-9]"), "_")
    // Looked up by name, since these are added without touching the code. res/raw/keep.xml keeps them in release builds.
    @Suppress("DiscouragedApi")
    return context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
}

/**
 * One of MySmartTV's own banner images, kept at its real pixel size and drawn with smooth scaling, so text
 * in it stays clean when it's shrunk down to card size. [smooth] false leaves out that smoothing, which softens the
 * picture when it's shown large, like on the start screen, and on the Forecast and News cards.
 */
@Composable
internal fun rememberBuiltInBanner(@DrawableRes id: Int, smooth: Boolean = true): ImageBitmap {
    val resources = LocalContext.current.resources
    return remember(id, smooth) {
        // Without inScaled = false, Android would first blow the image up to 1.5x for the TV's screen density.
        val options = BitmapFactory.Options().apply { inScaled = false }
        BitmapFactory.decodeResource(resources, id, options)
            // Mipmaps are pre-shrunk copies the TV blends between, instead of skipping pixels when shrinking.
            .apply { setHasMipMap(smooth) }
            .asImageBitmap()
    }
}

/** A tile showing an app's TV banner. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelTile(
    channel: Channel,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    // Drop-in banners are only for the start screen; the card keeps the app's own banner.
    val builtInBanner = builtInBanners[channel.packageName]
    val banner = remember(channel.packageName) {
        if (builtInBanner != null) null else loadBanner(context, channel.packageName)
    }

    TileSurface(onClick = onClick, onLongClick = onLongClick, onFocused = onFocused, modifier = modifier) {
        if (builtInBanner != null) {
            // MySmartTV's own channels take their banners from the app's own images.
            Image(
                bitmap = rememberBuiltInBanner(builtInBanner),
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(TileShape),
            )
        } else if (banner != null) {
            Image(
                bitmap = banner,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                // Fills the card edge to edge, with the card's own rounded corners, so it meets the border.
                modifier = Modifier
                    .fillMaxSize()
                    .clip(TileShape),
            )
        } else {
            Text(
                text = channel.name,
                color = WiiClockText,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** A blank slot showing TV static. Shows a + when selected; pressing it adds an app. */
@Composable
fun EmptyTile(onClick: () -> Unit, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val static = remember { if (SHOW_STATIC) sharedStatic(context) else null }
    val tint = WiiEmptyTint

    TileSurface(
        onClick = onClick,
        onFocused = onFocused,
        modifier = modifier.onFocusChanged { focused = it.hasFocus },
    ) {
        // The card's usual grey, with the static faded over it so it reads as a texture rather than noise,
        // and a faint grey-blue tint on top.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(WiiEmptyTop, WiiEmptyBottom)))
                .clipToBounds()
                .drawBehind {
                    if (static != null) drawCropped(static)
                    drawRect(tint)
                }
        )
        // Fades in and out at the same pace as the border, so the two arrive together.
        val plusAlpha by animateFloatAsState(
            targetValue = if (focused) 1f else 0f,
            animationSpec = tween(SelectFadeMs),
            label = "plus fade",
        )
        Text(
            text = "+",
            fontSize = 44.sp,
            fontWeight = FontWeight.Light,
            color = WiiBlue,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = plusAlpha },
        )
    }
}

// One copy of the static, shared by every empty tile, so it's only loaded once. Once started it keeps
// animating on its own.
private var staticDrawable: AnimatedImageDrawable? = null

// Whether empty tiles show the static at all. Set back to true to bring it back.
private const val SHOW_STATIC = false

// How strongly the static shows over the grey card, from 0 (invisible) to 255 (full strength).
private const val STATIC_ALPHA = 40

private fun sharedStatic(context: Context): AnimatedImageDrawable =
    staticDrawable ?: (ImageDecoder.decodeDrawable(
        ImageDecoder.createSource(context.applicationContext.resources, R.raw.empty_card)
    ) as AnimatedImageDrawable).apply {
        repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        alpha = STATIC_ALPHA
        start()
        staticDrawable = this
    }

// Scales the animation to fill the tile, trimming whatever spills over the edges.
private fun DrawScope.drawCropped(drawable: Drawable) {
    val imageWidth = drawable.intrinsicWidth.toFloat()
    val imageHeight = drawable.intrinsicHeight.toFloat()
    val scale = maxOf(size.width / imageWidth, size.height / imageHeight)
    val left = ((size.width - imageWidth * scale) / 2).toInt()
    val top = ((size.height - imageHeight * scale) / 2).toInt()
    drawable.setBounds(left, top, left + (imageWidth * scale).toInt(), top + (imageHeight * scale).toInt())
    drawable.draw(drawContext.canvas.nativeCanvas)
}

/**
 * The selectable card every dashboard tile is drawn on. The bar's round corner buttons use it too, with a circle
 * for [shape], so they highlight the same way.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun TileSurface(
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = TileShape,
    content: @Composable BoxScope.() -> Unit,
) {
    // Selecting a card doesn't grow it, and its border stays the same thickness. The border just fades from
    // grey to glowing blue.
    var focused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (focused) WiiBlue else WiiTileBorder,
        animationSpec = tween(SelectFadeMs),
        label = "tile border color",
    )
    val glow by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = tween(SelectFadeMs),
        label = "tile glow",
    )
    val glowColor = WiiBlue

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            // A soft blue haze spreading evenly out from every side of the card. Unlike a shadow, it isn't
            // pushed downward by a light from above.
            .drawBehind {
                if (glow > 0f) {
                    val paint = Paint().apply { color = glowColor.copy(alpha = GlowStrength * glow) }
                    paint.asFrameworkPaint().maskFilter =
                        BlurMaskFilter(GlowSpread.toPx(), BlurMaskFilter.Blur.NORMAL)
                    drawIntoCanvas { it.drawOutline(shape.createOutline(size, layoutDirection, this), paint) }
                }
            }
            .onFocusChanged {
                focused = it.hasFocus
                if (it.hasFocus) onFocused()
            }
            // Drawn over the card's picture, so it shows all the way round.
            .border(2.dp, borderColor, shape)
            // A blue fade just inside the border, over the picture, getting fainter toward the middle.
            .drawWithContent {
                drawContent()
                if (glow > 0f) {
                    val edge = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithContent)) }
                    val paint = Paint().apply {
                        color = glowColor.copy(alpha = InnerGlowStrength * glow)
                        style = PaintingStyle.Stroke
                        // The stroke straddles the edge, so only its inner half shows inside the card.
                        strokeWidth = InnerGlowSpread.toPx() * 2
                    }
                    paint.asFrameworkPaint().maskFilter =
                        BlurMaskFilter(InnerGlowSpread.toPx(), BlurMaskFilter.Blur.NORMAL)
                    clipPath(edge) { drawIntoCanvas { it.drawPath(edge, paint) } }
                }
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = WiiSurface,
            focusedContainerColor = WiiSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        content = content,
    )
}

// How long a card's border takes to fade to blue when selected, and back when you move away.
internal const val SelectFadeMs = 200

// The selected card's glow: how far it spreads past the edges, and how strong it is, from 0 (none) to 1.
private val GlowSpread = 12.dp
private const val GlowStrength = 0.5f

// The fade inside the selected card's border: how far in it reaches, and how strong it is at the edge.
private val InnerGlowSpread = 8.dp
private const val InnerGlowStrength = 0.4f
