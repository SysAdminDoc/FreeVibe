package com.freevibe.data.remote

import com.freevibe.data.model.*
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.bing.BingImage
import com.freevibe.data.remote.internetarchive.IASearchDoc
import com.freevibe.data.remote.internetarchive.InternetArchiveApi
import com.freevibe.data.remote.picsum.PicsumApi
import com.freevibe.data.remote.picsum.PicsumPhoto
import com.freevibe.data.remote.reddit.RedditPost
import com.freevibe.data.remote.freesound.FreesoundSound
import com.freevibe.data.remote.wallhaven.WallhavenWallpaper

// -- Wallhaven -> Wallpaper --

fun WallhavenWallpaper.toWallpaper() = Wallpaper(
    id = "wh_$id",
    source = ContentSource.WALLHAVEN,
    thumbnailUrl = thumbs.large.ifEmpty { thumbs.original },
    fullUrl = path,
    width = dimensionX,
    height = dimensionY,
    category = category,
    tags = tags?.map { it.name } ?: emptyList(),
    colors = colors,
    fileSize = fileSize,
    fileType = fileType,
    sourcePageUrl = url,
    views = views,
    favorites = favorites,
)

// -- Picsum -> Wallpaper --

fun PicsumPhoto.toWallpaper() = Wallpaper(
    id = "ps_$id",
    source = ContentSource.PICSUM,
    thumbnailUrl = PicsumApi.thumbUrl(id),
    fullUrl = PicsumApi.imageUrl(id),
    width = width,
    height = height,
    category = "photography",
    tags = listOf("unsplash", "photography"),
    sourcePageUrl = url,
    uploaderName = author,
)

// -- Bing Daily -> Wallpaper --

fun BingImage.toWallpaper() = Wallpaper(
    id = "bing_${startDate}_${urlbase.hashCode().toUInt()}",
    source = ContentSource.BING,
    thumbnailUrl = BingDailyApi.thumbUrl(urlbase),
    fullUrl = BingDailyApi.fullUrl(urlbase),
    width = 3840,  // UHD
    height = 2160,
    category = "daily",
    tags = listOf("bing", "daily", "curated"),
    sourcePageUrl = copyrightLink,
    uploaderName = copyright.substringAfter("(c) ", copyright)
        .substringAfter("(", copyright).substringBefore(")"),
)

// -- Internet Archive -> Sound --

fun IASearchDoc.toSound(
    audioUrl: String = "",
    duration: Double = 0.0,
    fileSize: Long = 0,
) = Sound(
    id = "ia_$identifier",
    source = ContentSource.INTERNET_ARCHIVE,
    name = title,
    description = description ?: "",
    previewUrl = audioUrl,
    downloadUrl = audioUrl,
    duration = duration,
    fileSize = fileSize,
    tags = emptyList(),
    license = licenseUrl ?: "Public Domain",
    uploaderName = creator ?: "Unknown",
    sourcePageUrl = InternetArchiveApi.detailsUrl(identifier),
)

// -- Domain -> FavoriteEntity --

fun Wallpaper.toFavoriteEntity() = FavoriteEntity(
    id = id,
    source = source.name,
    type = "WALLPAPER",
    thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl,
    width = width,
    height = height,
)

fun Sound.toFavoriteEntity() = FavoriteEntity(
    id = id,
    source = source.name,
    type = "SOUND",
    thumbnailUrl = "",
    fullUrl = downloadUrl,
    name = name,
    duration = duration,
)

// -- FavoriteEntity -> Domain --

fun FavoriteEntity.toWallpaper() = Wallpaper(
    id = id,
    source = ContentSource.valueOf(source),
    thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl,
    width = width,
    height = height,
)

fun FavoriteEntity.toSound() = Sound(
    id = id,
    source = ContentSource.valueOf(source),
    name = name,
    previewUrl = fullUrl,
    downloadUrl = fullUrl,
    duration = duration,
)

// -- Reddit Post -> Wallpaper --

fun RedditPost.toWallpaper(): Wallpaper {
    val res = parsedResolution
    return Wallpaper(
        id = "rd_$id",
        source = ContentSource.REDDIT,
        thumbnailUrl = thumbUrl,
        fullUrl = imageUrl,
        width = res?.first ?: 0,
        height = res?.second ?: 0,
        category = subreddit,
        tags = listOf(subreddit),
        sourcePageUrl = "https://www.reddit.com$permalink",
        uploaderName = author,
    )
}

// -- Freesound -> Sound --

fun FreesoundSound.toSound(): Sound? {
    val previewUrl = previews?.previewHqMp3 ?: previews?.previewLqMp3 ?: return null
    val shortLicense = when {
        license.contains("Creative Commons 0", ignoreCase = true) -> "CC0"
        license.contains("Attribution Noncommercial", ignoreCase = true) -> "CC-BY-NC"
        license.contains("Attribution", ignoreCase = true) -> "CC-BY"
        license.contains("Sampling+", ignoreCase = true) -> "Sampling+"
        else -> license.substringAfterLast("/").trimEnd('/').ifEmpty { "CC" }
    }
    return Sound(
        id = "fs_$id",
        source = ContentSource.FREESOUND,
        name = name,
        description = description.take(300),
        previewUrl = previewUrl,
        downloadUrl = previewUrl, // Preview MP3 (128kbps) — no OAuth needed
        duration = duration,
        sampleRate = samplerate.toInt(),
        fileType = "audio/mpeg",
        fileSize = filesize,
        tags = tags.take(10),
        license = shortLicense,
        uploaderName = username,
        sourcePageUrl = "https://freesound.org/people/$username/sounds/$id/",
    )
}

