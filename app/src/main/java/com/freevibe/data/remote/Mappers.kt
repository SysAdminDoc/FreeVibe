package com.freevibe.data.remote

import com.freevibe.data.model.*
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.bing.BingImage
import com.freevibe.data.remote.internetarchive.IASearchDoc
import com.freevibe.data.remote.internetarchive.InternetArchiveApi
import com.freevibe.data.remote.nasa.ApodResponse
import com.freevibe.data.remote.picsum.PicsumApi
import com.freevibe.data.remote.picsum.PicsumPhoto
import com.freevibe.data.remote.reddit.RedditPost
import com.freevibe.data.remote.wallhaven.WallhavenWallpaper
import com.freevibe.data.remote.wikimedia.WikimediaPage

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

// -- Wikimedia Commons -> Wallpaper --

fun WikimediaPage.toWallpaper(): Wallpaper? {
    val info = imageInfo?.firstOrNull() ?: return null
    // Skip non-image files (SVG, PDF, etc.)
    val url = info.url
    if (!url.endsWith(".jpg", true) && !url.endsWith(".jpeg", true) &&
        !url.endsWith(".png", true) && !url.endsWith(".webp", true)
    ) return null

    // #6: Skip low-res images (icons, logos, diagrams)
    if (info.width < 1920 || info.height < 1080) return null

    val desc = info.extMetadata?.description?.value
        ?.replace(Regex("<[^>]*>"), "")?.trim() ?: ""
    val license = info.extMetadata?.license?.value ?: "CC"
    val cats = info.extMetadata?.categories?.value?.split("|") ?: emptyList()

    return Wallpaper(
        id = "wm_$pageId",
        source = ContentSource.WIKIMEDIA,
        thumbnailUrl = info.thumbUrl ?: url,
        fullUrl = url,
        width = info.width,
        height = info.height,
        category = cats.firstOrNull() ?: "commons",
        tags = cats.take(5),
        fileSize = info.size.toLong(),
        sourcePageUrl = info.descriptionUrl,
        uploaderName = info.user,
    )
}

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

// -- NASA APOD -> Wallpaper --

fun ApodResponse.toWallpaper() = Wallpaper(
    id = "nasa_$date",
    source = ContentSource.NASA,
    thumbnailUrl = thumbOrUrl,
    fullUrl = bestUrl,
    width = 0,
    height = 0,
    category = "astronomy",
    tags = listOf("nasa", "apod", "space", "astronomy"),
    sourcePageUrl = "https://apod.nasa.gov/apod/ap${date.replace("-", "").drop(2)}.html",
    uploaderName = copyright ?: "NASA",
)
