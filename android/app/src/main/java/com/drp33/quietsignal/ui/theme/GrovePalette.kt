package com.drp33.quietsignal.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Design tokens for the "Grove" experience. Botanical is the default look; the
 * Clean/Storybook variants and a runtime switcher come later (see the integration
 * plan). For now these mirror the Botanical palette and the per-memory accent
 * colours, so every Grove screen pulls from one source instead of re-declaring
 * hex values. [GroveTree] keeps its own private foliage palette for the canvas.
 */
object GrovePalette {
    val bg = Color(0xFFF1ECE0)
    val surface = Color(0xFFFBF8F0)
    val ink = Color(0xFF2C2A20)
    val inkSoft = Color(0xFF6E695A)
    val accent = Color(0xFFB5683E)

    // Foliage (Botanical), shared with the canvas tree's look.
    val foliage = listOf(Color(0xFF5E8A5C), Color(0xFF477049), Color(0xFF7BA46F))
    val trunkTop = Color(0xFF8A7250)
    val trunkBot = Color(0xFF6B563A)

    // Soil gradient for the roots descent (light topsoil → deep subsoil).
    val soilTop = Color(0xFF6E5A40)
    val soilBottom = Color(0xFF3A2E20)
    val rootColor = Color(0xFF7A6346)

    // Calm safety strip dots — never red on the Grove screens.
    val safeGreen = Color(0xFF5E8A5C)
    val quietAmber = Color(0xFFCBA14A)

    /** Accent for a memory type: voice / photo / water / note. */
    fun accentFor(type: String): Color = when (type) {
        "voice" -> Color(0xFF8579B0)
        "photo" -> Color(0xFFC26B4E)
        "water" -> Color(0xFF5E93AE)
        "note" -> Color(0xFF869B5A)
        else -> accent
    }

    /** Emoji glyph for a memory type. */
    fun glyphFor(type: String): String = when (type) {
        "voice" -> "🎤"
        "photo" -> "📸"
        "water" -> "💛"
        "note" -> "📝"
        else -> "🌱"
    }

    /** Past-tense verb for a memory type, e.g. "Sadie <verb>". */
    fun verbFor(type: String): String = when (type) {
        "voice" -> "left a voice note"
        "photo" -> "shared a photo"
        "water" -> "watered the grove"
        "note" -> "wrote a note"
        else -> "added a moment"
    }
}
