package com.freevibe.ui.screens.sounds

internal enum class SoundCollectionTone { MINIMAL, CALM, RETRO, NATURE, PUNCHY, MELODIC }

internal data class SoundCollectionSpec(
    val title: String,
    val subtitle: String,
    val query: String,
    val tone: SoundCollectionTone,
)

internal fun soundCollectionsFor(tab: SoundTab): List<SoundCollectionSpec> = when (tab) {
    SoundTab.RINGTONES -> listOf(
        SoundCollectionSpec(
            title = "Minimal Rings",
            subtitle = "Clean tones under 20s",
            query = "minimal clean ringtone tone",
            tone = SoundCollectionTone.MINIMAL,
        ),
        SoundCollectionSpec(
            title = "Soft Chimes",
            subtitle = "Gentle melodic calls",
            query = "soft chime ringtone melody",
            tone = SoundCollectionTone.CALM,
        ),
        SoundCollectionSpec(
            title = "Retro Phones",
            subtitle = "Classic ring energy",
            query = "retro phone ringtone bell",
            tone = SoundCollectionTone.RETRO,
        ),
        SoundCollectionSpec(
            title = "Nature Calls",
            subtitle = "Organic wakeable tones",
            query = "nature bird water ringtone",
            tone = SoundCollectionTone.NATURE,
        ),
        SoundCollectionSpec(
            title = "Pulse Rings",
            subtitle = "Modern electronic loops",
            query = "electronic pulse ringtone",
            tone = SoundCollectionTone.MELODIC,
        ),
    )
    SoundTab.NOTIFICATIONS -> listOf(
        SoundCollectionSpec(
            title = "Tiny UI",
            subtitle = "Short taps and clicks",
            query = "short ui notification click",
            tone = SoundCollectionTone.MINIMAL,
        ),
        SoundCollectionSpec(
            title = "Glass Pings",
            subtitle = "Bright clean alerts",
            query = "glass ping notification",
            tone = SoundCollectionTone.MELODIC,
        ),
        SoundCollectionSpec(
            title = "Calm Alerts",
            subtitle = "Soft low-friction cues",
            query = "calm soft notification chime",
            tone = SoundCollectionTone.CALM,
        ),
        SoundCollectionSpec(
            title = "Nature Drops",
            subtitle = "Water and wood accents",
            query = "water drop wood notification",
            tone = SoundCollectionTone.NATURE,
        ),
        SoundCollectionSpec(
            title = "Punchy Beeps",
            subtitle = "Clear attention cues",
            query = "punchy beep alert notification",
            tone = SoundCollectionTone.PUNCHY,
        ),
    )
    SoundTab.ALARMS -> listOf(
        SoundCollectionSpec(
            title = "Gentle Wake",
            subtitle = "Warm morning ramps",
            query = "gentle morning alarm chime",
            tone = SoundCollectionTone.CALM,
        ),
        SoundCollectionSpec(
            title = "Classic Bells",
            subtitle = "Reliable alarm shapes",
            query = "classic alarm bell ring",
            tone = SoundCollectionTone.RETRO,
        ),
        SoundCollectionSpec(
            title = "Nature Morning",
            subtitle = "Birds and daylight cues",
            query = "nature morning alarm birds",
            tone = SoundCollectionTone.NATURE,
        ),
        SoundCollectionSpec(
            title = "Deep Pulse",
            subtitle = "Focused wake pressure",
            query = "deep pulse alarm tone",
            tone = SoundCollectionTone.PUNCHY,
        ),
        SoundCollectionSpec(
            title = "Bright Rise",
            subtitle = "Musical wake loops",
            query = "bright melodic wake alarm",
            tone = SoundCollectionTone.MELODIC,
        ),
    )
    SoundTab.YOUTUBE,
    SoundTab.COMMUNITY,
    SoundTab.SEARCH -> emptyList()
}
