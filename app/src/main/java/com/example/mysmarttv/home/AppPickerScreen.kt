package com.example.mysmarttv.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import com.example.mysmarttv.ui.theme.PillButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.example.mysmarttv.ui.theme.WiiClockText
import com.example.mysmarttv.ui.theme.WiiDateText
import com.example.mysmarttv.ui.theme.wiiBackdrop

private const val PLAY_STORE = "com.android.vending"

/** Lists the TV's installed apps that aren't on the dashboard yet. Picking one puts it in [slot]. */
@Composable
fun AppPickerScreen(
    slot: Int,
    store: ChannelStore,
    sounds: Sounds,
    onDone: () -> Unit,
) {
    BackHandler {
        sounds.playBack()
        onDone()
    }
    val context = LocalContext.current
    // MySmartTV's own channels are offered first, then the installed apps. The Play Store has its own button above.
    val apps = remember {
        (BuiltInChannels + installedTvApps(context))
            .filter { it.packageName !in store.packageNames && it.packageName != PLAY_STORE }
    }
    val firstApp = remember { FocusRequester() }
    // When the page opens, Android first selects the Get More Apps button on its own, then the page moves the
    // selection to the first tile. Both happen by themselves, so stay quiet until the first tile has been
    // reached, and only play the sound for real moves after that.
    var settled by remember { mutableStateOf(false) }
    val onTileFocused = {
        if (settled) sounds.playCardMove() else settled = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wiiBackdrop()
            // The grid adds its own 12 at the sides, so the tiles still line up with the text 28 in from the edge.
            .padding(start = 16.dp, end = 16.dp, top = 22.dp),
    ) {
        val textInset = Modifier.padding(horizontal = 12.dp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = textInset) {
            Column(Modifier.weight(1f)) {
                Text(text = "Add a Channel", fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = WiiDateText)
                Text(text = "Pick an app for this slot. Press Back to cancel.", fontSize = 16.sp, color = WiiClockText)
            }
            // The Play Store lives here instead of taking up a slot on the dashboard.
            PillButton(
                label = "Get More Apps",
                onClick = {
                    sounds.playAppSelect()
                    launchApp(context, PLAY_STORE)
                },
                modifier = Modifier.onFocusChanged {
                    if (it.hasFocus) {
                        // With no tiles to move to, this button's first selection is the page opening.
                        if (settled) sounds.playCardMove() else if (apps.isEmpty()) settled = true
                    }
                },
            )
        }
        Spacer(Modifier.height(2.dp))

        if (apps.isEmpty()) {
            Text(text = "Everything is already on the dashboard.", fontSize = 18.sp, color = WiiClockText, modifier = textInset)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                // Room inside the scrolling area's edges, so a selected tile can grow and glow without being cut off.
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                itemsIndexed(apps) { index, app ->
                    ChannelTile(
                        channel = app,
                        onClick = {
                            sounds.playAppSelect()
                            store.set(slot, app.packageName)
                            onDone()
                        },
                        onFocused = onTileFocused,
                        modifier = Modifier
                            .height(110.dp)
                            .padding(4.dp)
                            .let { if (index == 0) it.focusRequester(firstApp) else it },
                    )
                }
            }
            LaunchedEffect(Unit) { firstApp.requestFocus() }
        }
    }
}
