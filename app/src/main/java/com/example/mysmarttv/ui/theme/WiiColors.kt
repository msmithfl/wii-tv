package com.example.mysmarttv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Every color the screens use, so light and dark mode can swap the whole set at once. */
data class WiiPalette(
    val background: Color,
    val stripe: Color,
    val surface: Color,
    val blue: Color,
    val focusFill: Color,
    val tileBorder: Color,
    val emptyTop: Color,
    val emptyBottom: Color,
    // A see-through tint laid over the static in empty cards.
    val emptyTint: Color,
    val barTop: Color,
    val barBottom: Color,
    val barEdge: Color,
    val clockText: Color,
    val dateText: Color,
    val buttonFace: Color,
    val buttonRim: Color,
)

val LightPalette = WiiPalette(
    background = Color(0xFFFAFAFA),
    stripe = Color(0xFFF0F0F0),
    surface = Color(0xFFFFFFFF),
    blue = Color(0xFF34BEED),
    focusFill = Color(0xFFD9F3FC),
    tileBorder = Color(0xFFCFCFCF),
    emptyTop = Color(0xFFF2F2F2),
    emptyBottom = Color(0xFFDDDDDD),
    emptyTint = Color(0x33C3D3E4),
    barTop = Color(0xFFEFEFEF),
    barBottom = Color(0xFFD9D9D9),
    barEdge = Color(0xFF5CC8EE),
    clockText = Color(0xFF9A9A9A),
    dateText = Color(0xFF5E5E5E),
    buttonFace = Color(0xFFFFFFFF),
    buttonRim = Color(0xFFBDBDBD),
)

val DarkPalette = WiiPalette(
    background = Color(0xFF1C1F24),
    stripe = Color(0xFF23272D),
    surface = Color(0xFF2B2F35),
    blue = Color(0xFF34BEED),
    focusFill = Color(0xFF1E4757),
    tileBorder = Color(0xFF3E444C),
    emptyTop = Color(0xFF2A2E34),
    emptyBottom = Color(0xFF212429),
    emptyTint = Color(0x1A7F9AB8),
    barTop = Color(0xFF2A2E34),
    barBottom = Color(0xFF17191D),
    barEdge = Color(0xFF3FB4DE),
    clockText = Color(0xFFA3AAB2),
    dateText = Color(0xFFDDE2E7),
    buttonFace = Color(0xFF3A3F46),
    buttonRim = Color(0xFF4B5159),
)

val LocalWiiPalette = staticCompositionLocalOf { LightPalette }

// Shortcuts so screens can write WiiBlue instead of LocalWiiPalette.current.blue.
val WiiBackground: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.background
val WiiStripe: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.stripe
val WiiSurface: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.surface
val WiiBlue: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.blue
val WiiFocusFill: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.focusFill
val WiiTileBorder: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.tileBorder
val WiiEmptyTop: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.emptyTop
val WiiEmptyBottom: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.emptyBottom
val WiiEmptyTint: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.emptyTint
val WiiBarTop: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.barTop
val WiiBarBottom: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.barBottom
val WiiBarEdge: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.barEdge
val WiiClockText: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.clockText
val WiiDateText: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.dateText
val WiiButtonFace: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.buttonFace
val WiiButtonRim: Color @Composable @ReadOnlyComposable get() = LocalWiiPalette.current.buttonRim
