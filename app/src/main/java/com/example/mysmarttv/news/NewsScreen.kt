package com.example.mysmarttv.news

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.R

private val ShadowedText = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(2f, 3f), 4f))

/** Placeholder for the News Channel until it has real headlines. Back or Home returns to the dashboard. */
@Composable
fun NewsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(modifier = Modifier.fillMaxSize()) {
        // The channel's banner fills the screen, with its title near the top.
        Image(
            painter = painterResource(R.drawable.news_app),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp),
        ) {
            Text(
                text = "Coming soon",
                style = ShadowedText,
                fontSize = 40.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = "Press Back to return to the dashboard.",
                style = ShadowedText,
                fontSize = 20.sp,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}
