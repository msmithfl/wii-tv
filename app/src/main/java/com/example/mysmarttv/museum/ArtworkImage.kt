package com.example.mysmarttv.museum

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

// Recently shown pictures, so stepping back through a gallery doesn't download them again.
// Counted in kilobytes, so a few big full-size pictures don't crowd out every thumbnail.
private val cache = object : LruCache<String, ImageBitmap>(64 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4 / 1024
}

// Shrinks the picture until its longer side is under [maxSize], since some originals are many thousands of pixels.
private suspend fun download(url: String, maxSize: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val bytes = URL(url).openStream().use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
            ?.also { cache.put("$maxSize $url", it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

/**
 * A piece's picture, downloaded when first shown. Shown whole by default, the way it hangs on the wall.
 * [placeholder] shows until it arrives.
 */
@Composable
fun ArtworkImage(
    url: String,
    modifier: Modifier = Modifier,
    maxSize: Int = 1600,
    contentScale: ContentScale = ContentScale.Fit,
    placeholder: String = "",
    placeholderColor: Color = Color.Gray,
) {
    // Started over for each new picture, so stepping to the next piece never shows the last one's picture.
    key(url, maxSize) {
        val picture by produceState(cache.get("$maxSize $url")) {
            if (value == null) value = download(url, maxSize)
        }
        val shown = picture
        if (shown != null) {
            Image(bitmap = shown, contentDescription = null, contentScale = contentScale, modifier = modifier)
        } else {
            Box(contentAlignment = Alignment.Center, modifier = modifier) {
                Text(placeholder, fontSize = 18.sp, color = placeholderColor)
            }
        }
    }
}
