package com.example.mysmarttv.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.example.mysmarttv.ui.theme.PillButton
import com.example.mysmarttv.ui.theme.WiiBlue
import com.example.mysmarttv.ui.theme.WiiSurface
import com.example.mysmarttv.ui.theme.WiiClockText
import com.example.mysmarttv.ui.theme.WiiDateText

private val DialogShape = RoundedCornerShape(24.dp)
private val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** Asks before taking [channel] off the dashboard. Remove is selected, so pressing OK again confirms; Back cancels. */
@Composable
fun RemoveChannelDialog(
    channel: Channel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onButtonMove: () -> Unit,
) {
    val removeButton = remember { FocusRequester() }
    // While the dialog opens, Android may select Cancel first before we move it to Remove. Stay quiet
    // until Remove has been selected once; after that, every switch between the buttons clicks.
    var settled by remember { mutableStateOf(false) }
    val onCancelFocused: () -> Unit = { if (settled) onButtonMove() }
    val onRemoveFocused: () -> Unit = { if (settled) onButtonMove() else settled = true }
    // The dialog opens while OK is still held down. Ignore OK until it's pressed again,
    // so letting go of the long press doesn't count as choosing Remove.
    var okPressedAgain by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .onPreviewKeyEvent { event ->
                    if (event.key !in OkKeys) return@onPreviewKeyEvent false
                    // Holding a key sends repeated key-downs; only a fresh one counts.
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) {
                        okPressedAgain = true
                    }
                    !okPressedAgain
                }
                .width(460.dp)
                .clip(DialogShape)
                .background(WiiSurface)
                .border(3.dp, WiiBlue, DialogShape)
                .padding(28.dp),
        ) {
            Text(
                text = "Remove ${channel.name}?",
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = WiiDateText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "This takes it off the dashboard. The app stays installed on your TV.",
                fontSize = 17.sp,
                color = WiiClockText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PillButton(
                    label = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.onFocusChanged { if (it.hasFocus) onCancelFocused() },
                )
                PillButton(
                    label = "Remove",
                    onClick = onConfirm,
                    modifier = Modifier
                        .focusRequester(removeButton)
                        .onFocusChanged { if (it.hasFocus) onRemoveFocused() },
                )
            }
        }
    }

    LaunchedEffect(Unit) { removeButton.requestFocus() }
}
