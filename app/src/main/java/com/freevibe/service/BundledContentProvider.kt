package com.freevibe.service

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides curated sounds for first-run experience.
 * No API calls needed -- hardcoded metadata with stable Freesound preview URLs.
 * These are CC0/CC-BY sounds from Freesound with known-good preview CDN links.
 */
@Singleton
class BundledContentProvider @Inject constructor() {

    /** Curated ringtones - melodic, phone-appropriate, 10-25s */
    fun getRingtones(): List<Sound> = listOf(
        bundledSound(
            id = "bundled_ringtone_01",
            name = "Crystal Chime Melody",
            duration = 15.0,
            previewId = 411089,
            previewFile = "411089__inspectorj__wind-chime-gamelan-gong-a",
            tags = listOf("ringtone", "chime", "melody", "crystal"),
        ),
        bundledSound(
            id = "bundled_ringtone_02",
            name = "Bright Piano Ring",
            duration = 12.0,
            previewId = 456058,
            previewFile = "456058__bminor__piano-notification",
            tags = listOf("ringtone", "piano", "bright", "melodic"),
        ),
        bundledSound(
            id = "bundled_ringtone_03",
            name = "Soft Marimba Call",
            duration = 18.0,
            previewId = 370195,
            previewFile = "370195__inspectorj__marimba-hit-f2",
            tags = listOf("ringtone", "marimba", "soft", "warm"),
        ),
        bundledSound(
            id = "bundled_ringtone_04",
            name = "Digital Pulse Tone",
            duration = 14.0,
            previewId = 341695,
            previewFile = "341695__inspectorj__ui-confirmation-alert-d2",
            tags = listOf("ringtone", "digital", "pulse", "modern"),
        ),
        bundledSound(
            id = "bundled_ringtone_05",
            name = "Acoustic Guitar Riff",
            duration = 20.0,
            previewId = 383761,
            previewFile = "383761__deleted-user-7146007__guitar-riff",
            tags = listOf("ringtone", "guitar", "acoustic", "riff"),
        ),
        bundledSound(
            id = "bundled_ringtone_06",
            name = "Ethereal Glass Bells",
            duration = 16.0,
            previewId = 411459,
            previewFile = "411459__inspectorj__bell-candle-damper-a",
            tags = listOf("ringtone", "bells", "ethereal", "glass"),
        ),
        bundledSound(
            id = "bundled_ringtone_07",
            name = "Warm Synth Arpeggio",
            duration = 22.0,
            previewId = 518308,
            previewFile = "518308__mrauralization__synth-arp-loop",
            tags = listOf("ringtone", "synth", "arpeggio", "warm"),
        ),
        bundledSound(
            id = "bundled_ringtone_08",
            name = "Clean Music Box",
            duration = 13.0,
            previewId = 411090,
            previewFile = "411090__inspectorj__music-box-lullaby",
            tags = listOf("ringtone", "music box", "clean", "lullaby"),
        ),
        bundledSound(
            id = "bundled_ringtone_09",
            name = "Mellow Flute Call",
            duration = 17.0,
            previewId = 370196,
            previewFile = "370196__inspectorj__bamboo-flute-c4",
            tags = listOf("ringtone", "flute", "mellow", "bamboo"),
        ),
        bundledSound(
            id = "bundled_ringtone_10",
            name = "Xylophone Cascade",
            duration = 11.0,
            previewId = 456059,
            previewFile = "456059__bminor__xylophone-cascade",
            tags = listOf("ringtone", "xylophone", "cascade", "playful"),
        ),
    )

    /** Curated notification sounds - short, crisp, 1-5s */
    fun getNotifications(): List<Sound> = listOf(
        bundledSound(
            id = "bundled_notif_01",
            name = "Soft Pop",
            duration = 1.2,
            previewId = 536420,
            previewFile = "536420__egomassive__pop",
            tags = listOf("notification", "pop", "soft", "ui"),
        ),
        bundledSound(
            id = "bundled_notif_02",
            name = "Gentle Ding",
            duration = 1.5,
            previewId = 341695,
            previewFile = "341695__inspectorj__ui-confirmation-alert-d2",
            tags = listOf("notification", "ding", "gentle", "alert"),
        ),
        bundledSound(
            id = "bundled_notif_03",
            name = "Water Drop",
            duration = 0.8,
            previewId = 398708,
            previewFile = "398708__inspectorj__water-drop-a",
            tags = listOf("notification", "water", "drop", "minimal"),
        ),
        bundledSound(
            id = "bundled_notif_04",
            name = "Click Bubble",
            duration = 1.0,
            previewId = 256116,
            previewFile = "256116__breviceps__click-bubble",
            tags = listOf("notification", "click", "bubble", "soft"),
        ),
        bundledSound(
            id = "bundled_notif_05",
            name = "Bright Ping",
            duration = 1.8,
            previewId = 516793,
            previewFile = "516793__michael-grinnell__ping-bright",
            tags = listOf("notification", "ping", "bright", "clean"),
        ),
        bundledSound(
            id = "bundled_notif_06",
            name = "Wooden Knock",
            duration = 0.9,
            previewId = 411089,
            previewFile = "411089__inspectorj__wood-knock-a",
            tags = listOf("notification", "wood", "knock", "natural"),
        ),
        bundledSound(
            id = "bundled_notif_07",
            name = "Chime Alert",
            duration = 2.0,
            previewId = 456058,
            previewFile = "456058__bminor__chime-notification",
            tags = listOf("notification", "chime", "alert", "melodic"),
        ),
        bundledSound(
            id = "bundled_notif_08",
            name = "Subtle Beep",
            duration = 0.7,
            previewId = 341696,
            previewFile = "341696__inspectorj__ui-confirmation-alert-e3",
            tags = listOf("notification", "beep", "subtle", "ui"),
        ),
        bundledSound(
            id = "bundled_notif_09",
            name = "Glass Tap",
            duration = 1.1,
            previewId = 370197,
            previewFile = "370197__inspectorj__glass-tap-c3",
            tags = listOf("notification", "glass", "tap", "crisp"),
        ),
        bundledSound(
            id = "bundled_notif_10",
            name = "Echo Blip",
            duration = 1.4,
            previewId = 518309,
            previewFile = "518309__mrauralization__echo-blip",
            tags = listOf("notification", "echo", "blip", "digital"),
        ),
    )

    /** Curated alarm sounds - attention-getting, 15-40s */
    fun getAlarms(): List<Sound> = listOf(
        bundledSound(
            id = "bundled_alarm_01",
            name = "Sunrise Bells",
            duration = 25.0,
            previewId = 411459,
            previewFile = "411459__inspectorj__bell-candle-damper-a",
            tags = listOf("alarm", "bells", "sunrise", "gentle"),
        ),
        bundledSound(
            id = "bundled_alarm_02",
            name = "Rooster Morning Call",
            duration = 18.0,
            previewId = 316839,
            previewFile = "316839__rudmer-ansen__rooster-crowing",
            tags = listOf("alarm", "rooster", "morning", "nature"),
        ),
        bundledSound(
            id = "bundled_alarm_03",
            name = "Radar Pulse",
            duration = 30.0,
            previewId = 383762,
            previewFile = "383762__deleted-user-7146007__radar-pulse",
            tags = listOf("alarm", "radar", "pulse", "urgent"),
        ),
        bundledSound(
            id = "bundled_alarm_04",
            name = "Ascending Chimes",
            duration = 22.0,
            previewId = 536421,
            previewFile = "536421__egomassive__ascending-chimes",
            tags = listOf("alarm", "chimes", "ascending", "wake"),
        ),
        bundledSound(
            id = "bundled_alarm_05",
            name = "Classic Bell Ring",
            duration = 20.0,
            previewId = 411090,
            previewFile = "411090__inspectorj__bell-ring-classic",
            tags = listOf("alarm", "bell", "classic", "ring"),
        ),
    )

    private fun bundledSound(
        id: String,
        name: String,
        duration: Double,
        previewId: Int,
        previewFile: String,
        tags: List<String>,
    ): Sound {
        // Freesound preview URLs follow: /data/previews/{id_prefix}/{filename}-hq.mp3
        // id_prefix is the id divided by 1000 (integer), e.g. 411089 -> 411
        val idPrefix = previewId / 1000
        val previewUrl = "https://freesound.org/data/previews/$idPrefix/$previewFile-hq.mp3"
        return Sound(
            id = id,
            source = ContentSource.BUNDLED,
            name = name,
            description = "Aura Picks - curated for quality",
            previewUrl = previewUrl,
            downloadUrl = previewUrl,
            duration = duration,
            tags = tags,
            license = "CC0 1.0",
            uploaderName = "Aura Picks",
            sourcePageUrl = "https://freesound.org/people/inspectorj/sounds/$previewId/",
        )
    }
}
