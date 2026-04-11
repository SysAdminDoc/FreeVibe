package com.freevibe.data.remote

import com.freevibe.data.model.*
import com.freevibe.data.remote.reddit.RedditPost
import org.junit.Assert.*
import org.junit.Test

class MappersTest {

    // ── RedditPost.toWallpaper ──

    @Test
    fun `RedditPost toWallpaper maps id with rd_ prefix`() {
        val post = RedditPost(id = "abc123", title = "Sunset [3840x2160]", url = "https://i.redd.it/sunset.jpg",
            subreddit = "wallpapers", author = "testuser", permalink = "/r/wallpapers/comments/abc123/sunset/")
        val wp = post.toWallpaper()
        assertEquals("rd_abc123", wp.id)
        assertEquals(ContentSource.REDDIT, wp.source)
        assertEquals(3840, wp.width)
        assertEquals(2160, wp.height)
        assertEquals("wallpapers", wp.category)
        assertEquals("testuser", wp.uploaderName)
        assertEquals("https://www.reddit.com/r/wallpapers/comments/abc123/sunset/", wp.sourcePageUrl)
    }

    @Test
    fun `RedditPost toWallpaper with no resolution in title gives 0x0`() {
        val post = RedditPost(id = "xyz", title = "Just a photo", url = "https://i.redd.it/photo.jpg")
        val wp = post.toWallpaper()
        assertEquals(0, wp.width)
        assertEquals(0, wp.height)
    }

    // ── Wallpaper.toFavoriteEntity ──

    @Test
    fun `Wallpaper toFavoriteEntity maps all fields`() {
        val wp = Wallpaper(
            id = "wh_123", source = ContentSource.WALLHAVEN,
            thumbnailUrl = "https://thumb.jpg", fullUrl = "https://full.jpg",
            width = 1920, height = 1080, category = "nature",
            tags = listOf("forest", "green"), colors = listOf("#ff0000"),
            fileSize = 500_000, fileType = "image/jpeg",
            sourcePageUrl = "https://wallhaven.cc/w/123",
            uploaderName = "artist", views = 100, favorites = 50,
        )
        val entity = wp.toFavoriteEntity()
        assertEquals("wh_123", entity.id)
        assertEquals("WALLHAVEN", entity.source)
        assertEquals("WALLPAPER", entity.type)
        assertEquals("forest ||| green", entity.tags)
        assertEquals("#ff0000", entity.colors)
        assertEquals(500_000L, entity.fileSize)
        assertEquals(100L, entity.views)
        assertEquals(50L, entity.favoritesCount)
    }

    @Test
    fun `Wallpaper toFavoriteEntity nulls out empty strings and zero values`() {
        val wp = Wallpaper(
            id = "test", source = ContentSource.WALLHAVEN,
            thumbnailUrl = "t", fullUrl = "f", width = 0, height = 0,
        )
        val entity = wp.toFavoriteEntity()
        assertNull(entity.tags)
        assertNull(entity.colors)
        assertNull(entity.category)
        assertNull(entity.uploaderName)
        assertNull(entity.sourcePageUrl)
        assertNull(entity.fileSize)
        assertNull(entity.fileType)
        assertNull(entity.views)
        assertNull(entity.favoritesCount)
    }

    // ── FavoriteEntity.toWallpaper ──

    @Test
    fun `FavoriteEntity toWallpaper round-trips correctly`() {
        val original = Wallpaper(
            id = "wh_99", source = ContentSource.WALLHAVEN,
            thumbnailUrl = "https://t.jpg", fullUrl = "https://f.jpg",
            width = 2560, height = 1440, category = "anime",
            tags = listOf("art", "digital"), colors = listOf("#000", "#fff"),
            fileSize = 1_000_000, fileType = "image/png",
            sourcePageUrl = "https://wallhaven.cc/w/99",
            uploaderName = "creator", views = 42, favorites = 7,
        )
        val restored = original.toFavoriteEntity().toWallpaper()
        assertEquals(original.id, restored.id)
        assertEquals(original.source, restored.source)
        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(original.tags, restored.tags)
        assertEquals(original.colors, restored.colors)
        assertEquals(original.category, restored.category)
        assertEquals(original.uploaderName, restored.uploaderName)
        assertEquals(original.fileSize, restored.fileSize)
        assertEquals(original.views, restored.views)
        assertEquals(original.favorites, restored.favorites)
    }

    @Test
    fun `FavoriteEntity toWallpaper handles unknown source gracefully`() {
        val entity = FavoriteEntity(
            id = "x", source = "DELETED_SOURCE", type = "WALLPAPER",
            thumbnailUrl = "t", fullUrl = "f",
        )
        val wp = entity.toWallpaper()
        assertEquals(ContentSource.WALLHAVEN, wp.source) // fallback
    }

    @Test
    fun `FavoriteEntity toWallpaper splits tags by delimiter`() {
        val entity = FavoriteEntity(
            id = "x", source = "WALLHAVEN", type = "WALLPAPER",
            thumbnailUrl = "t", fullUrl = "f",
            tags = "alpha ||| beta ||| gamma",
        )
        assertEquals(listOf("alpha", "beta", "gamma"), entity.toWallpaper().tags)
    }

    @Test
    fun `FavoriteEntity toWallpaper handles null tags`() {
        val entity = FavoriteEntity(
            id = "x", source = "WALLHAVEN", type = "WALLPAPER",
            thumbnailUrl = "t", fullUrl = "f", tags = null,
        )
        assertEquals(emptyList<String>(), entity.toWallpaper().tags)
    }

    // ── Sound.toFavoriteEntity + FavoriteEntity.toSound ──

    @Test
    fun `Sound round-trips through FavoriteEntity`() {
        val original = Sound(
            id = "fs_42", source = ContentSource.FREESOUND,
            name = "bird chirp", previewUrl = "https://preview.mp3",
            downloadUrl = "https://download.mp3", duration = 3.5,
            tags = listOf("nature", "bird"), uploaderName = "recorder",
            sourcePageUrl = "https://freesound.org/s/42",
            fileSize = 50_000, fileType = "audio/mp3",
        )
        val restored = original.toFavoriteEntity().toSound()
        assertEquals(original.id, restored.id)
        assertEquals(original.source, restored.source)
        assertEquals(original.name, restored.name)
        assertEquals(original.downloadUrl, restored.downloadUrl)
        assertEquals(original.duration, restored.duration, 0.001)
        assertEquals(original.tags, restored.tags)
        assertEquals(original.uploaderName, restored.uploaderName)
    }

    @Test
    fun `YouTube favorites store page urls and restore with fresh resolution fields`() {
        val original = Sound(
            id = "yt_focus12345",
            source = ContentSource.YOUTUBE,
            name = "Focus Loop",
            previewUrl = "https://redirect.googlevideo.com/stale-preview",
            downloadUrl = "https://redirect.googlevideo.com/stale-download",
            duration = 18.0,
            sourcePageUrl = "https://www.youtube.com/watch?v=focus12345",
        )

        val entity = original.toFavoriteEntity()
        val restored = entity.toSound()

        assertEquals(original.sourcePageUrl, entity.fullUrl)
        assertEquals("", restored.previewUrl)
        assertEquals("", restored.downloadUrl)
        assertEquals(original.sourcePageUrl, restored.sourcePageUrl)
    }

    @Test
    fun `FavoriteEntity toSound recovers YouTube page urls from legacy fullUrl`() {
        val entity = FavoriteEntity(
            id = "yt_focus12345",
            source = "YOUTUBE",
            type = "SOUND",
            thumbnailUrl = "",
            fullUrl = "https://youtu.be/focus12345",
            name = "Focus Loop",
        )

        val restored = entity.toSound()

        assertEquals(ContentSource.YOUTUBE, restored.source)
        assertEquals("https://youtu.be/focus12345", restored.sourcePageUrl)
        assertEquals("", restored.previewUrl)
        assertEquals("", restored.downloadUrl)
    }

    @Test
    fun `FavoriteEntity toSound handles unknown source with FREESOUND fallback`() {
        val entity = FavoriteEntity(
            id = "x", source = "NONEXISTENT", type = "SOUND",
            thumbnailUrl = "", fullUrl = "url",
        )
        assertEquals(ContentSource.FREESOUND, entity.toSound().source)
    }
}
