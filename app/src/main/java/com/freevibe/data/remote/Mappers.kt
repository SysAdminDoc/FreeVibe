package com.freevibe.data.remote

import com.freevibe.data.model.*
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.bing.BingImage
import com.freevibe.data.remote.pixabay.PixabayPhoto
import com.freevibe.data.remote.picsum.PicsumApi
import com.freevibe.data.remote.picsum.PicsumPhoto
import com.freevibe.data.remote.reddit.RedditPost
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

// -- Pixabay -> Wallpaper --

fun PixabayPhoto.toWallpaper() = Wallpaper(
    id = "pb_$id",
    source = ContentSource.PIXABAY,
    thumbnailUrl = webformatUrl,
    fullUrl = largeImageUrl,
    width = imageWidth,
    height = imageHeight,
    tags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
    fileSize = imageSize,
    sourcePageUrl = pageUrl,
    uploaderName = user,
    views = views,
    favorites = likes,
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
    source = try { ContentSource.valueOf(source) } catch (_: Exception) { ContentSource.WALLHAVEN },
    thumbnailUrl = thumbnailUrl,
    fullUrl = fullUrl,
    width = width,
    height = height,
)

fun FavoriteEntity.toSound() = Sound(
    id = id,
    source = try { ContentSource.valueOf(source) } catch (_: Exception) { ContentSource.INTERNET_ARCHIVE },
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

