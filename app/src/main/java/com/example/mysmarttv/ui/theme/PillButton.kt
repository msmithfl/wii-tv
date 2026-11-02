package com.example.mysmarttv.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val PillShape = RoundedCornerShape(50)

/** Rounded white button that fills light blue when selected. Used for the music controls and dialogs. */
@Composable
fun PillButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillSurface(onClick, modifier) {
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = WiiDateText,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 18.dp),
        )
    }
}

/** The same button with an icon from res/drawable in place of words, like the music player's play button. */
@Composable
fun IconPillButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PillSurface(onClick, modifier.width(84.dp)) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(WiiDateText),
            modifier = Modifier
                .align(Alignment.Center)
                .size(30.dp),
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PillSurface(onClick: () -> Unit, modifier: Modifier, content: @Composable BoxScope.() -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = ClickableSurfaceDefaults.shape(PillShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = WiiSurface,
            focusedContainerColor = WiiFocusFill,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(2.dp, WiiTileBorder), shape = PillShape),
            focusedBorder = Border(BorderStroke(3.dp, WiiBlue), shape = PillShape),
        ),
        content = content,
    )
}
