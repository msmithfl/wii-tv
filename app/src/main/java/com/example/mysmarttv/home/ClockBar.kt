package com.example.mysmarttv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.R
import com.example.mysmarttv.ui.theme.WiiBarBottom
import com.example.mysmarttv.ui.theme.WiiBarEdge
import com.example.mysmarttv.ui.theme.WiiBarTop
import com.example.mysmarttv.ui.theme.WiiButtonFace
import com.example.mysmarttv.ui.theme.WiiClockText
import com.example.mysmarttv.ui.theme.WiiEmptyBottom
import com.example.mysmarttv.ui.theme.WiiFont
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("h:mm")
private val amPmFormat = DateTimeFormatter.ofPattern("a")
private val dateFormat = DateTimeFormatter.ofPattern("EEE M/d")

// The Wii-style digital font for the time. Also used by the screensaver.
internal val ClockFont = FontFamily(Font(R.font.wii_digital))

// How far down the bar the blue divider dips in the middle: just below the bottom of the time's digits.
private val MiddleTop = 64.dp

// Where the bar's raised corners start, measured down from the top of the bar.
private val CornerTop = 12.dp

// The round corner buttons, and how far below the corner's top edge they sit.
private val ButtonSize = 92.dp
private val IconSize = 40.dp
private val ButtonGap = 25.dp

/** Grey bar along the bottom: raised corners holding round buttons, with the clock and date in the middle. */
@Composable
fun ClockBar(
    onSettingsClick: () -> Unit,
    onMusicClick: () -> Unit,
    onMusicLongClick: () -> Unit,
    musicMuted: Boolean,
    onButtonFocused: () -> Unit,
    settingsFocus: FocusRequester,
    musicFocus: FocusRequester,
    settingsUp: FocusRequester,
    musicUp: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000)
        }
    }

    // Drawing code can't read theme colors itself, so grab them here.
    val barFill = Brush.verticalGradient(listOf(WiiBarTop, WiiBarBottom))
    val barEdge = WiiBarEdge

    Box(
        modifier = modifier.drawBehind {
            drawPath(barPath(closed = true), barFill)
            // Soft shadow under the divider: wide faint strokes nudged down, stacked to fade out.
            val edge = barPath(closed = false)
            val shadowSteps = 16
            for (step in 0 until shadowSteps) {
                // Each step is a little lower and a little fainter, so it fades out gently.
                val alpha = 0.018f * (1f - step.toFloat() / shadowSteps)
                translate(top = (4 + step * 2.25f).dp.toPx()) {
                    drawPath(edge, Color.Black.copy(alpha = alpha), style = Stroke(width = 10.dp.toPx()))
                }
            }
            drawPath(edge, barEdge, style = Stroke(width = 2.dp.toPx()))
        }
    ) {
        // Time sits right at the top of the bar, above the blue divider; date just below the divider.
        TimeWithAmPm(
            time = now.format(timeFormat),
            amPm = now.format(amPmFormat),
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Text(
            text = now.format(dateFormat),
            fontSize = 36.sp,
            fontWeight = FontWeight.SemiBold,
            color = WiiClockText,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = MiddleTop + 4.dp),
        )

        // Opens MySmartTV's own settings. It carries the app's name, the way the Wii's left button said "Wii".
        CornerButton(
            label = "TiiV",
            onClick = onSettingsClick,
            onFocused = onButtonFocused,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 28.dp, top = CornerTop + ButtonGap)
                .focusRequester(settingsFocus)
                // Nothing is left of it; without this, left would reach the off-screen cards and slide the page.
                .focusProperties {
                    left = FocusRequester.Cancel
                    up = settingsUp
                },
        )
        // Press to open the music screen; hold to mute or unmute. The note is crossed out while muted.
        CornerButton(
            icon = R.drawable.eighth_note,
            onClick = onMusicClick,
            onLongClick = onMusicLongClick,
            crossedOut = musicMuted,
            onFocused = onButtonFocused,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 28.dp, top = CornerTop + ButtonGap)
                .focusRequester(musicFocus)
                .focusProperties {
                    right = FocusRequester.Cancel
                    up = musicUp
                },
        )
    }
}

/**
 * The time, with AM/PM sitting on the same bottom line just to its right. Only the time counts
 * toward the width, so the time stays centered and AM/PM hangs off the side.
 */
@Composable
private fun TimeWithAmPm(time: String, amPm: String, modifier: Modifier = Modifier) {
    Layout(
        modifier = modifier,
        content = {
            Text(
                text = time,
                fontSize = 61.sp,
                fontFamily = ClockFont,
                color = WiiClockText,
                letterSpacing = 2.sp,
                lineHeight = 61.sp,
                // Drops the empty space the font keeps above the digits, so they start at the very top.
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
                ),
            )
            Text(
                text = amPm,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = WiiClockText,
            )
        },
    ) { measurables, constraints ->
        val timeText = measurables[0].measure(constraints)
        val amPmText = measurables[1].measure(constraints)
        val gap = 6.dp.roundToPx()
        layout(timeText.width, timeText.height) {
            timeText.place(0, 0)
            // Line up the bottoms of the letters with the bottoms of the digits.
            amPmText.place(timeText.width + gap, timeText[FirstBaseline] - amPmText[FirstBaseline])
        }
    }
}

/**
 * Round button in a corner of the bar, showing an [icon] or a word ([label]). When selected it highlights like the
 * dashboard's cards: the grey rim fades to glowing blue.
 */
@Composable
private fun CornerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    label: String? = null,
    onLongClick: (() -> Unit)? = null,
    crossedOut: Boolean = false,
    onFocused: () -> Unit = {},
) {
    TileSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocused = onFocused,
        shape = CircleShape,
        modifier = modifier.size(ButtonSize),
    ) {
        ButtonFace(icon, label, crossedOut, Modifier.fillMaxSize())
    }
}

@Composable
private fun ButtonFace(@DrawableRes icon: Int?, label: String?, crossedOut: Boolean, modifier: Modifier = Modifier) {
    val slashColor = WiiClockText
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(WiiButtonFace, WiiEmptyBottom))),
    ) {
        // The icons are drawn white and tinted here, so they follow light and dark mode.
        if (icon != null) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(WiiClockText),
                modifier = Modifier.size(IconSize),
            )
        }
        if (label != null) {
            Text(text = label, fontSize = 26.sp, fontFamily = WiiFont, fontWeight = FontWeight.Bold, color = WiiClockText)
        }
        if (crossedOut) {
            // A slash across the middle of the face, over the icon.
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawLine(
                            color = slashColor,
                            start = Offset(size.width * 0.3f, size.height * 0.7f),
                            end = Offset(size.width * 0.7f, size.height * 0.3f),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
            )
        }
    }
}

/** The bar's top edge, high at the corners and dipping lower across the middle. [closed] also runs it round the bottom so it can be filled. */
private fun DrawScope.barPath(closed: Boolean): Path {
    val w = size.width
    val h = size.height
    val cornerTop = CornerTop.toPx()
    val middleTop = MiddleTop.toPx()
    val flatEnd = 170.dp.toPx()
    val slopeEnd = 340.dp.toPx()
    // Control points halfway along the slope give a long, gentle S-curve.
    val ease = (slopeEnd - flatEnd) / 2
    return Path().apply {
        moveTo(0f, cornerTop)
        lineTo(flatEnd, cornerTop)
        cubicTo(flatEnd + ease, cornerTop, slopeEnd - ease, middleTop, slopeEnd, middleTop)
        lineTo(w - slopeEnd, middleTop)
        cubicTo(w - slopeEnd + ease, middleTop, w - flatEnd - ease, cornerTop, w - flatEnd, cornerTop)
        lineTo(w, cornerTop)
        if (closed) {
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
    }
}
