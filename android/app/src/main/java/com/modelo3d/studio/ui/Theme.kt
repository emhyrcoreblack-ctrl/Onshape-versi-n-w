package com.modelo3d.studio.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object CadColors {
    val Ink = Color(0xFF0A0A0B)
    val Panel = Color(0xFF121214)
    val PanelHi = Color(0xFF1A1A1D)
    val GoldMain = Color(0xFFD9B26A)
    val GoldLight = Color(0xFFE9C987)
    val GoldDeep = Color(0xFFB98B3E)
    val GoldSoft = Color(0xFF5A4B2B)
    val GoldFill = Color(0x26D9B26A)
    val Text = Color(0xFFEDE6D6)
    val TextDim = Color(0xFFA89F8C)
    val OnGold = Color(0xFF1A1405)
}

val CadDark = darkColorScheme(
    primary = CadColors.GoldMain,
    onPrimary = CadColors.OnGold,
    primaryContainer = Color(0xFF3A2F17),
    onPrimaryContainer = CadColors.GoldLight,
    secondaryContainer = Color(0xFF242016),
    onSecondaryContainer = CadColors.Text,
    background = CadColors.Ink,
    onBackground = CadColors.Text,
    surface = CadColors.Panel,
    onSurface = CadColors.Text,
    surfaceVariant = Color(0xFF1C1C1F),
    onSurfaceVariant = CadColors.TextDim,
    outline = CadColors.GoldSoft,
    surfaceContainerLowest = Color(0xFF0E0E10),
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Color(0xFF17171A),
    surfaceContainerHigh = Color(0xFF1C1C20),
    surfaceContainerHighest = Color(0xFF222226)
)
