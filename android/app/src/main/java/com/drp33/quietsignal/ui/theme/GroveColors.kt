package com.drp33.quietsignal.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Grove "Botanical" palette — the single source of truth for the redesigned UI.
 * Ported from the Grove web prototype (botanical theme). Referenced directly by
 * the screens so the look is stable regardless of Material's dynamic-color.
 */
object Grove {
    // surfaces / background
    val Bg = Color(0xFFF1ECE0)
    val Bg2 = Color(0xFFE8E0CF)
    val Surface = Color(0xFFFBF8F0)
    val Surface2 = Color(0xFFF1EADB)

    // ink
    val Ink = Color(0xFF2C2A20)
    val InkSoft = Color(0xFF6E695A)
    val InkFaint = Color(0xFFA59E8B)
    val Line = Color(0x1F2C2A20) // rgba(44,42,32,0.12)

    // sky / ground
    val SkyTop = Color(0xFFEDE7D6)
    val SkyBot = Color(0xFFF4EFE2)
    val Ground = Color(0xFFDED3BC)
    val Soil = Color(0xFFCDB99B)
    val SoilLine = Color(0xFFB49C77)

    // foliage (used for forest hills / accents)
    val Foliage = Color(0xFF5E8A5C)
    val Foliage2 = Color(0xFF477049)
    val Foliage3 = Color(0xFF7BA46F)
    val FoliageRest = Color(0xFFA7AC8E)

    // accent + input channels
    val Accent = Color(0xFFB5683E)
    val Voice = Color(0xFF8579B0)
    val Photo = Color(0xFFC26B4E)
    val Water = Color(0xFF5E93AE)
    val Note = Color(0xFF869B5A)

    val CardRadius = 18
}
