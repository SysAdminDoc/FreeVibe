package com.freevibe.ui.screens.sounds

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Sound
import kotlin.math.roundToInt

enum class SoundQualityFilter { BEST, CLEAN, SHORT, CALM, PUNCHY }

internal fun rankSounds(
    sounds: List<Sound>,
    tab: SoundTab,
    filter: SoundQualityFilter,
): List<Sound> {
    val candidatePool = if (filter == SoundQualityFilter.BEST) {
        sounds
    } else {
        sounds.filter { soundMatchesFilter(it, tab, filter) }
    }
    val deduped = dedupeSounds(candidatePool, tab, filter)
    val rankedBase = deduped.ifEmpty { dedupeSounds(sounds, tab, SoundQualityFilter.BEST) }
    val scored = rankedBase
        .map { sound -> sound to soundQualityScore(sound, tab, filter) }
        .sortedByDescending { it.second }
    return applySoundQualityFloor(scored).map { it.first }
}

internal fun soundQualityScore(
    sound: Sound,
    tab: SoundTab,
    filter: SoundQualityFilter = SoundQualityFilter.BEST,
): Int {
    val normalizedName = normalizedSoundTerms(sound.name)
    val normalizedTags = sound.tags.flatMap(::normalizedSoundTerms)
    var score = 45

    score += when (sound.source) {
        ContentSource.BUNDLED -> 25
        ContentSource.JAMENDO -> 18
        ContentSource.AUDIUS -> 17
        ContentSource.FREESOUND -> 16
        ContentSource.CCMIXTER -> 15
        ContentSource.SOUNDCLOUD -> 14
        ContentSource.YOUTUBE -> 12
        ContentSource.WIKIMEDIA -> 12
        ContentSource.COMMUNITY -> 10
        else -> 8
    }
    score += when (tab) {
        SoundTab.RINGTONES -> when {
            sound.duration in 10.0..28.0 -> 16
            sound.duration in 6.0..40.0 -> 8
            else -> -8
        }
        SoundTab.NOTIFICATIONS -> when {
            sound.duration in 0.4..4.5 -> 18
            sound.duration in 0.4..8.0 -> 10
            else -> -12
        }
        SoundTab.ALARMS -> when {
            sound.duration in 8.0..35.0 -> 14
            sound.duration in 5.0..60.0 -> 8
            else -> -8
        }
        SoundTab.YOUTUBE, SoundTab.COMMUNITY -> 4
        SoundTab.SEARCH -> when {
            sound.duration in 2.0..35.0 -> 8
            else -> 0
        }
    }
    if (sound.uploaderName.isNotBlank() && sound.uploaderName != "Unknown") score += 5
    if (sound.license.startsWith("CC")) score += 6
    if (sound.sampleRate >= 44_100) score += 4
    if (sound.fileSize in 50_000..6_000_000) score += 4
    if (sound.tags.size >= 3) score += 6
    if (sound.duration > 180.0) score -= 22
    if (normalizedName.any { it in GOOD_SOUND_TERMS }) score += 8
    if (normalizedTags.any { it in GOOD_SOUND_TERMS }) score += 8
    if (normalizedName.any { it in BAD_SOUND_TERMS }) score -= 18
    if (normalizedTags.any { it in BAD_SOUND_TERMS }) score -= 10
    if (normalizedName.any { it in LOW_SIGNAL_SOUND_TERMS }) score -= 20
    if (normalizedTags.any { it in LOW_SIGNAL_SOUND_TERMS }) score -= 12
    if (sound.name.length in 5..48) score += 4
    if (sound.name.contains("_") && !sound.name.contains(" ")) score -= 12
    if (sound.name.matches(LEADING_DIGITS_REGEX)) score -= 10
    score += when (filter) {
        SoundQualityFilter.BEST -> 0
        SoundQualityFilter.CLEAN -> if (soundLooksClean(sound)) 18 else -10
        SoundQualityFilter.SHORT -> if (sound.duration <= shortThreshold(tab)) 18 else -10
        SoundQualityFilter.CALM -> if (soundFeelsCalm(sound)) 18 else -8
        SoundQualityFilter.PUNCHY -> if (soundFeelsPunchy(sound)) 18 else -8
    }
    return score
}

internal fun soundBadges(sound: Sound, tab: SoundTab): List<String> {
    val badges = mutableListOf<String>()
    badges += when (tab) {
        SoundTab.RINGTONES -> "Ringtone-ready"
        SoundTab.NOTIFICATIONS -> "Notification-ready"
        SoundTab.ALARMS -> "Alarm-ready"
        SoundTab.YOUTUBE -> "Importable"
        SoundTab.COMMUNITY -> "Community"
        SoundTab.SEARCH -> "Quick pick"
    }
    if (soundLooksClean(sound)) badges += "Clean intro"
    if (soundFeelsCalm(sound)) badges += "Calm"
    if (soundFeelsPunchy(sound)) badges += "Punchy"
    if (sound.duration <= shortThreshold(tab)) badges += "Short"
    if (sound.license.startsWith("CC")) badges += sound.license
    return badges.distinct().take(3)
}

internal fun soundFingerprint(sound: Sound): String {
    val bucket = (sound.duration * 2).roundToInt()
    return buildString {
        append(normalizedSoundTerms(sound.name).joinToString(" "))
        append('|')
        append(normalizedSoundTerms(sound.uploaderName).joinToString(" "))
        append('|')
        append(bucket)
    }
}

internal fun soundSourceLabel(source: ContentSource): String = when (source) {
    ContentSource.BUNDLED -> "Aura Picks"
    ContentSource.YOUTUBE -> "YouTube"
    ContentSource.FREESOUND -> "Freesound"
    ContentSource.JAMENDO -> "Jamendo"
    ContentSource.WIKIMEDIA -> "Wikimedia"
    ContentSource.AUDIUS -> "Audius"
    ContentSource.CCMIXTER -> "ccMixter"
    ContentSource.SOUNDCLOUD -> "SoundCloud"
    ContentSource.COMMUNITY -> "Community"
    else -> source.name.lowercase().replaceFirstChar { it.titlecase() }
    }

private fun applySoundQualityFloor(
    scored: List<Pair<Sound, Int>>,
): List<Pair<Sound, Int>> {
    if (scored.size < 5) return scored
    val topScore = scored.first().second
    val qualityFloor = maxOf(56, topScore - 30)
    val curated = scored.filterIndexed { index, (_, score) ->
        index < 3 || score >= qualityFloor
    }
    return if (curated.size >= minOf(scored.size, 4)) curated else scored
}

private fun dedupeSounds(
    sounds: List<Sound>,
    tab: SoundTab,
    filter: SoundQualityFilter,
): List<Sound> = sounds
    .groupBy(::soundFingerprint)
    .values
    .mapNotNull { variants ->
        variants.maxByOrNull { sound -> soundQualityScore(sound, tab, filter) }
    }

private fun soundMatchesFilter(sound: Sound, tab: SoundTab, filter: SoundQualityFilter): Boolean = when (filter) {
    SoundQualityFilter.BEST -> true
    SoundQualityFilter.CLEAN -> soundLooksClean(sound)
    SoundQualityFilter.SHORT -> sound.duration <= shortThreshold(tab)
    SoundQualityFilter.CALM -> soundFeelsCalm(sound)
    SoundQualityFilter.PUNCHY -> soundFeelsPunchy(sound)
}

private fun shortThreshold(tab: SoundTab): Double = when (tab) {
    SoundTab.NOTIFICATIONS -> 4.5
    SoundTab.RINGTONES -> 18.0
    SoundTab.ALARMS -> 20.0
    SoundTab.YOUTUBE, SoundTab.COMMUNITY -> 30.0
    SoundTab.SEARCH -> 12.0
}

private fun soundLooksClean(sound: Sound): Boolean {
    val terms = normalizedSoundTerms(sound.name) + sound.tags.flatMap(::normalizedSoundTerms)
    return terms.none { it in BAD_SOUND_TERMS } &&
        sound.name.length in 4..50 &&
        sound.duration > 0.3
}

private fun soundFeelsCalm(sound: Sound): Boolean {
    val terms = normalizedSoundTerms(sound.name) + sound.tags.flatMap(::normalizedSoundTerms)
    return terms.any { it in CALM_SOUND_TERMS } || sound.uploaderName.contains("ambient", ignoreCase = true)
}

private fun soundFeelsPunchy(sound: Sound): Boolean {
    val terms = normalizedSoundTerms(sound.name) + sound.tags.flatMap(::normalizedSoundTerms)
    return terms.any { it in PUNCHY_SOUND_TERMS }
}

private val LEADING_DIGITS_REGEX = Regex("^\\d+.*")
private val NON_ALNUM_REGEX = Regex("[^a-z0-9]+")

private fun normalizedSoundTerms(raw: String): List<String> =
    raw.lowercase()
        .split(NON_ALNUM_REGEX)
        .filter { it.isNotBlank() }

private val GOOD_SOUND_TERMS = setOf(
    "tone", "ringtone", "melody", "chime", "beep", "ding", "ping", "ambient", "alarm", "soft",
)

private val BAD_SOUND_TERMS = setOf(
    "lyrics", "official", "video", "mix", "compilation", "podcast", "reaction", "review", "tutorial", "live", "pack",
)

private val LOW_SIGNAL_SOUND_TERMS = setOf(
    "remix", "cover", "nightcore", "slowed", "reverb", "boosted", "viral", "status", "edit", "mashup",
)

private val CALM_SOUND_TERMS = setOf(
    "ambient", "calm", "soft", "gentle", "piano", "chime", "dreamy", "lofi", "warm", "synth",
)

private val PUNCHY_SOUND_TERMS = setOf(
    "alert", "alarm", "beep", "ping", "ringtone", "bass", "bright", "electro", "siren", "buzzer",
)
