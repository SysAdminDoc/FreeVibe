package com.freevibe.ui.screens.wallpapers

import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.Wallpaper
import kotlin.math.max

enum class WallpaperDiscoverFilter { FOR_YOU, AMOLED, HIGH_RES, PORTRAIT, ICON_SAFE }

internal data class WallpaperQualityHints(
    val resolutionLabel: String,
    val orientationLabel: String,
    val isAmoled: Boolean,
    val isIconSafe: Boolean,
)

internal fun rankWallpapers(
    wallpapers: List<Wallpaper>,
    filter: WallpaperDiscoverFilter,
    preferredResolution: String = "",
    userStyles: List<String> = emptyList(),
): List<Wallpaper> {
    val candidatePool = if (filter == WallpaperDiscoverFilter.FOR_YOU) {
        wallpapers
    } else {
        wallpapers.filter { it.matchesDiscoverFilter(filter) }
    }
    val deduped = dedupeWallpapers(
        wallpapers = candidatePool,
        filter = filter,
        preferredResolution = preferredResolution,
        userStyles = userStyles,
    )
    val rankedBase = deduped.ifEmpty {
        dedupeWallpapers(
            wallpapers = wallpapers,
            filter = WallpaperDiscoverFilter.FOR_YOU,
            preferredResolution = preferredResolution,
            userStyles = userStyles,
        )
    }
    val scored = rankedBase
        .map { wallpaper ->
            wallpaper to wallpaperQualityScore(
                wallpaper = wallpaper,
                filter = filter,
                preferredResolution = preferredResolution,
                userStyles = userStyles,
            )
        }
        .sortedByDescending { it.second }
    return applyWallpaperQualityFloor(scored).map { it.first }
}

private fun dedupeWallpapers(
    wallpapers: List<Wallpaper>,
    filter: WallpaperDiscoverFilter,
    preferredResolution: String,
    userStyles: List<String>,
): List<Wallpaper> = wallpapers
    .groupBy(::wallpaperKey)
    .values
    .mapNotNull { variants ->
        variants.maxByOrNull { wallpaper ->
            wallpaperQualityScore(
                wallpaper = wallpaper,
                filter = filter,
                preferredResolution = preferredResolution,
                userStyles = userStyles,
            )
        }
    }

internal fun Wallpaper.qualityHints(): WallpaperQualityHints {
    val pixels = width.toLong() * height.toLong()
    val resolutionLabel = when {
        pixels >= 7_000_000L || max(width, height) >= 3600 -> "4K+"
        pixels >= 3_400_000L || max(width, height) >= 2500 -> "QHD"
        pixels >= 1_900_000L || max(width, height) >= 1900 -> "FHD"
        pixels > 0L -> "HD"
        else -> "Ready"
    }
    val orientationLabel = when {
        width <= 0 || height <= 0 -> "Phone"
        height >= width -> "Portrait"
        else -> "Wide"
    }
    return WallpaperQualityHints(
        resolutionLabel = resolutionLabel,
        orientationLabel = orientationLabel,
        isAmoled = isAmoledFriendly(),
        isIconSafe = isIconSafe(),
    )
}

internal fun Wallpaper.matchesDiscoverFilter(filter: WallpaperDiscoverFilter): Boolean = when (filter) {
    WallpaperDiscoverFilter.FOR_YOU -> true
    WallpaperDiscoverFilter.AMOLED -> isAmoledFriendly()
    WallpaperDiscoverFilter.HIGH_RES -> hasHighResolution()
    WallpaperDiscoverFilter.PORTRAIT -> isPortraitPreferred()
    WallpaperDiscoverFilter.ICON_SAFE -> isIconSafe()
}

private fun wallpaperQualityScore(
    wallpaper: Wallpaper,
    filter: WallpaperDiscoverFilter,
    preferredResolution: String,
    userStyles: List<String>,
): Int {
    val pixels = wallpaper.width.toLong() * wallpaper.height.toLong()
    val normalizedTags = wallpaper.searchableTerms()
    val preferredPixels = preferredResolution.toPixelHint()
    var score = 40

    score += when (wallpaper.source) {
        ContentSource.WALLHAVEN -> 18
        ContentSource.BING -> 16
        ContentSource.PEXELS -> 15
        ContentSource.REDDIT -> 13
        ContentSource.PIXABAY -> 11
        else -> 8
    }
    score += when {
        pixels >= 7_000_000L -> 26
        pixels >= 3_400_000L -> 18
        pixels >= 1_900_000L -> 10
        pixels > 0L -> 3
        else -> 0
    }
    if (preferredPixels > 0L && pixels >= preferredPixels) score += 10
    if (wallpaper.isPortraitPreferred()) score += 8
    if (wallpaper.isAmoledFriendly()) score += 10
    if (wallpaper.isIconSafe()) score += 10
    if (pixels in 1L until 1_200_000L) score -= 12
    if (wallpaper.tags.size >= 3) score += 4
    if (wallpaper.favorites > 0) score += minOf(12, wallpaper.favorites / 60)
    if (wallpaper.views > 0) score += minOf(8, wallpaper.views / 400)
    if (normalizedTags.any { it in BUSY_WALLPAPER_TERMS }) score -= 14
    if (normalizedTags.any { it in CLEAN_WALLPAPER_TERMS }) score += 8
    if (normalizedTags.any { it in LOW_SIGNAL_WALLPAPER_TERMS }) score -= 18
    if (userStyles.isNotEmpty()) {
        val styleHits = normalizedTags.count { it in userStyles.toSet() }
        score += styleHits * 6
    }
    score += when (filter) {
        WallpaperDiscoverFilter.FOR_YOU -> 0
        WallpaperDiscoverFilter.AMOLED -> if (wallpaper.isAmoledFriendly()) 18 else -8
        WallpaperDiscoverFilter.HIGH_RES -> if (wallpaper.hasHighResolution()) 16 else -10
        WallpaperDiscoverFilter.PORTRAIT -> if (wallpaper.isPortraitPreferred()) 18 else -8
        WallpaperDiscoverFilter.ICON_SAFE -> if (wallpaper.isIconSafe()) 18 else -10
    }
    return score
}

private fun applyWallpaperQualityFloor(
    scored: List<Pair<Wallpaper, Int>>,
): List<Pair<Wallpaper, Int>> {
    if (scored.size < 5) return scored
    val topScore = scored.first().second
    val qualityFloor = max(54, topScore - 30)
    val curated = scored.filterIndexed { index, (_, score) ->
        index < 3 || score >= qualityFloor
    }
    return if (curated.size >= minOf(scored.size, 4)) curated else scored
}

private fun Wallpaper.isPortraitPreferred(): Boolean = height > 0 && height >= width

private fun Wallpaper.hasHighResolution(): Boolean =
    width >= 2160 || height >= 2160 || (width.toLong() * height.toLong()) >= 3_400_000L

private fun Wallpaper.isAmoledFriendly(): Boolean {
    val terms = searchableTerms()
    if (terms.any { it in AMOLED_TERMS }) return true
    return colors.any { color ->
        val clean = color.removePrefix("#")
        clean.length == 6 && runCatching { clean.toInt(16) }.getOrNull()?.let { rgb ->
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            (r + g + b) < 90
        } == true
    }
}

private fun Wallpaper.isIconSafe(): Boolean {
    val terms = searchableTerms()
    if (terms.any { it in BUSY_WALLPAPER_TERMS }) return false
    if (terms.any { it in CLEAN_WALLPAPER_TERMS }) return true
    return colors.size in 1..3 || category.contains("minimal", ignoreCase = true)
}

private fun Wallpaper.searchableTerms(): List<String> =
    buildList {
        addAll(tags.map { it.normalizeFeedTerm() })
        addAll(category.split(Regex("[^a-zA-Z0-9]+")).map { it.normalizeFeedTerm() })
        addAll(uploaderName.split(Regex("[^a-zA-Z0-9]+")).map { it.normalizeFeedTerm() })
    }.filter { it.isNotBlank() }

private fun wallpaperKey(wallpaper: Wallpaper): String {
    val stableUrl = wallpaper.fullUrl.ifBlank { wallpaper.thumbnailUrl }
        .substringBefore("?")
        .substringBefore("#")
        .lowercase()
    if (stableUrl.isNotBlank()) return stableUrl
    return listOf(
        wallpaper.id.lowercase(),
        wallpaper.width.toString(),
        wallpaper.height.toString(),
    ).joinToString("|")
}

private fun String.toPixelHint(): Long {
    val match = Regex("""(\d{3,5})\s*[xX]\s*(\d{3,5})""").find(this) ?: return 0L
    val first = match.groupValues[1].toLongOrNull() ?: return 0L
    val second = match.groupValues[2].toLongOrNull() ?: return 0L
    return first * second
}

private fun String.normalizeFeedTerm(): String = lowercase().trim()

private val AMOLED_TERMS = setOf(
    "amoled", "oled", "black", "dark", "night", "midnight", "shadow", "space", "neon",
)

private val CLEAN_WALLPAPER_TERMS = setOf(
    "minimal", "clean", "gradient", "abstract", "blur", "simple", "soft", "geometry",
)

private val BUSY_WALLPAPER_TERMS = setOf(
    "text", "quote", "poster", "collage", "character", "car", "vehicle", "face", "people",
)

private val LOW_SIGNAL_WALLPAPER_TERMS = setOf(
    "logo", "brand", "advert", "promo", "screenshot", "ui", "meme", "app", "engine",
)
