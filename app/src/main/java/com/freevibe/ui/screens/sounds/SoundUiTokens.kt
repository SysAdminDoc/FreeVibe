package com.freevibe.ui.screens.sounds

import androidx.compose.ui.graphics.Color
import com.freevibe.data.model.ContentSource

internal fun soundSourceTone(source: ContentSource): Pair<String, Color> = when (source) {
    ContentSource.BUNDLED -> "Aura Picks" to Color(0xFFFFB300)
    ContentSource.YOUTUBE -> "YouTube" to Color(0xFFFF0000)
    ContentSource.FREESOUND -> "Freesound" to Color(0xFF3DB2CE)
    ContentSource.JAMENDO -> "Jamendo" to Color(0xFF7E57C2)
    ContentSource.WIKIMEDIA -> "Wikimedia" to Color(0xFF006699)
    ContentSource.AUDIUS -> "Audius" to Color(0xFF00C2A8)
    ContentSource.CCMIXTER -> "ccMixter" to Color(0xFF8E24AA)
    ContentSource.SOUNDCLOUD -> "SoundCloud" to Color(0xFFFF5500)
    ContentSource.COMMUNITY -> "Community" to Color(0xFF4CAF50)
    else -> soundSourceLabel(source) to Color(0xFF607D8B)
}
