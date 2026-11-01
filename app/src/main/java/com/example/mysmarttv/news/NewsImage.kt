package com.example.mysmarttv.news

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

// Recently shown pictures, kept so scrolling back up or reopening a story doesn't download them again.
private val cache = LruCache<String, ImageBitmap>(60)

private suspend fun download(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        URL(url).openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }.getOrNull()?.also { cache.put(url, it) }
}

/** A story's picture, downloaded when first shown. A plain grey box stands in until it arrives, or if there isn't one. */
@Composable
fun NewsImage(url: String?, modifier: Modifier = Modifier) {
    val picture by produceState(url?.let { cache.get(it) }, url) {
        if (value == null && url != null) value = download(url)
    }
    val shown = picture
    if (shown != null) {
        Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(Color(0xFFB9BCB6)))
    }
}
