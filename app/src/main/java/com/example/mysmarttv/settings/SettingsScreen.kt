package com.example.mysmarttv.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.mysmarttv.home.Sounds
import com.example.mysmarttv.ui.theme.LocalWiiPalette
import com.example.mysmarttv.ui.theme.WiiBlue
import com.example.mysmarttv.ui.theme.WiiClockText
import com.example.mysmarttv.ui.theme.WiiDateText
import com.example.mysmarttv.ui.theme.WiiFocusFill
import com.example.mysmarttv.ui.theme.WiiSurface
import com.example.mysmarttv.ui.theme.WiiTileBorder
import com.example.mysmarttv.ui.theme.wiiBackdrop

private val RowShape = RoundedCornerShape(16.dp)

/** MySmartTV's own settings, separate from the TV's. Press OK on a row to step through its choices. */
@Composable
fun SettingsScreen(settings: AppSettings, sounds: Sounds, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val firstRow = remember { FocusRequester() }
    // The first row is selected when the screen opens; only play the move sound for real moves.
    var firstFocus by remember { mutableStateOf(true) }
    val onRowFocused: () -> Unit = {
        if (firstFocus) firstFocus = false else sounds.playCardMove()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wiiBackdrop()
            .padding(horizontal = 80.dp, vertical = 40.dp),
    ) {
        Text(text = "Settings", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = WiiDateText)
        Text(text = "Press OK to change a setting. Press Back to return.", fontSize = 16.sp, color = WiiClockText)
        Spacer(Modifier.height(28.dp))

        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.width(620.dp)) {
            SettingRow(
                label = "Theme",
                value = if (settings.darkMode) "Dark" else "Light",
                onClick = {
                    sounds.playAppSelect()
                    settings.toggleDarkMode()
                },
                onFocused = onRowFocused,
                modifier = Modifier.focusRequester(firstRow),
            )
            SettingRow(
                label = "Screensaver",
                value = screensaverLabel(settings.screensaverMinutes),
                onClick = {
                    sounds.playAppSelect()
                    settings.nextScreensaverDelay()
                },
                onFocused = onRowFocused,
            )
            SettingRow(
                label = "Screensaver pictures",
                value = settings.screensaverPictures.label,
                onClick = {
                    sounds.playAppSelect()
                    settings.nextScreensaverPictures()
                },
                onFocused = onRowFocused,
            )
            // How long the screensaver runs before the TV may go to sleep.
            SettingRow(
                label = "Sleep after screensaver",
                value = sleepLabel(settings.sleepAfterMinutes),
                onClick = {
                    sounds.playAppSelect()
                    settings.nextSleepDelay()
                },
                onFocused = onRowFocused,
            )
            // Google's own settings (Wi-Fi, accounts, apps, display), so they don't need a dashboard card.
            SettingRow(
                label = "TV Settings",
                value = "Open",
                onClick = {
                    sounds.playAppSelect()
                    // Opened fresh each time, so Back returns here instead of to pages left open last visit.
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                },
                onFocused = onRowFocused,
            )
        }
    }

    // Selects the Theme row when the screen opens, and again after a theme change rebuilds the rows,
    // quietly both times.
    LaunchedEffect(settings.darkMode) {
        firstFocus = true
        firstRow.requestFocus()
    }
}

private fun screensaverLabel(minutes: Int): String = when (minutes) {
    0 -> "Off"
    1 -> "After 1 minute"
    else -> "After $minutes minutes"
}

private fun sleepLabel(minutes: Int): String = when {
    minutes == 0 -> "Never"
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "1 hour"
    else -> "${minutes / 60} hours"
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The TV Surface holds on to its first border color, so rebuild it when light/dark mode changes.
    key(LocalWiiPalette.current) { Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.hasFocus) onFocused() },
        shape = ClickableSurfaceDefaults.shape(RowShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = WiiSurface,
            focusedContainerColor = WiiFocusFill,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(2.dp, WiiTileBorder), shape = RowShape),
            focusedBorder = Border(BorderStroke(3.dp, WiiBlue), shape = RowShape),
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Text(text = label, fontSize = 22.sp, color = WiiDateText, modifier = Modifier.weight(1f))
            Text(text = value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = WiiBlue)
        }
    } }
}
