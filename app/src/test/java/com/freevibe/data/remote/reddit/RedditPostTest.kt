package com.freevibe.data.remote.reddit

import org.junit.Assert.*
import org.junit.Test

class RedditPostTest {

    private fun post(
        url: String = "",
        postHint: String? = null,
        title: String = "",
    ) = RedditPost(
        id = "test",
        title = title,
        url = url,
        postHint = postHint,
    )

    // ── isImage ──

    @Test
    fun `isImage true when postHint is image`() {
        assertTrue(post(url = "https://example.com/video.mp4", postHint = "image").isImage)
    }

    @Test
    fun `isImage true for jpg URL`() {
        assertTrue(post(url = "https://i.redd.it/photo.jpg").isImage)
    }

    @Test
    fun `isImage true for jpeg URL`() {
        assertTrue(post(url = "https://i.redd.it/photo.jpeg").isImage)
    }

    @Test
    fun `isImage true for png URL`() {
        assertTrue(post(url = "https://i.imgur.com/abc.png").isImage)
    }

    @Test
    fun `isImage true for webp URL`() {
        assertTrue(post(url = "https://i.redd.it/photo.webp").isImage)
    }

    @Test
    fun `isImage case insensitive`() {
        assertTrue(post(url = "https://i.redd.it/photo.JPG").isImage)
        assertTrue(post(url = "https://i.redd.it/photo.Png").isImage)
    }

    @Test
    fun `isImage strips query params before checking extension`() {
        assertTrue(post(url = "https://i.redd.it/photo.jpg?width=1080&crop=smart").isImage)
    }

    @Test
    fun `isImage strips fragment before checking extension`() {
        assertTrue(post(url = "https://i.redd.it/photo.png#section").isImage)
    }

    @Test
    fun `isImage false for non-image URLs`() {
        assertFalse(post(url = "https://v.redd.it/video123").isImage)
        assertFalse(post(url = "https://www.reddit.com/gallery/abc").isImage)
        assertFalse(post(url = "https://youtube.com/watch?v=123").isImage)
    }

    @Test
    fun `isImage false for gif`() {
        assertFalse(post(url = "https://i.redd.it/animation.gif").isImage)
    }

    // ── parsedResolution ──

    @Test
    fun `parsedResolution extracts bracketed resolution`() {
        val res = post(title = "Mountain sunset [3840x2160]").parsedResolution
        assertEquals(3840 to 2160, res)
    }

    @Test
    fun `parsedResolution handles X uppercase`() {
        val res = post(title = "Cityscape [1920X1080]").parsedResolution
        assertEquals(1920 to 1080, res)
    }

    @Test
    fun `parsedResolution handles multiplication sign`() {
        val res = post(title = "Beach [2560×1440]").parsedResolution
        assertEquals(2560 to 1440, res)
    }

    @Test
    fun `parsedResolution handles spaces around x`() {
        val res = post(title = "Forest [3840 x 2160]").parsedResolution
        assertEquals(3840 to 2160, res)
    }

    @Test
    fun `parsedResolution without brackets`() {
        val res = post(title = "Mountain 3840x2160 sunset").parsedResolution
        assertEquals(3840 to 2160, res)
    }

    @Test
    fun `parsedResolution null when no resolution in title`() {
        assertNull(post(title = "Just a wallpaper").parsedResolution)
    }

    @Test
    fun `parsedResolution null for too-short numbers`() {
        assertNull(post(title = "Photo [10x20]").parsedResolution)
    }

    // ── imageUrl ──

    @Test
    fun `imageUrl returns direct url for i_redd_it`() {
        val p = post(url = "https://i.redd.it/abc123.jpg")
        assertEquals("https://i.redd.it/abc123.jpg", p.imageUrl)
    }

    @Test
    fun `imageUrl returns direct url for i_imgur_com`() {
        val p = post(url = "https://i.imgur.com/xyz.png")
        assertEquals("https://i.imgur.com/xyz.png", p.imageUrl)
    }

    @Test
    fun `imageUrl falls back to url when no preview`() {
        val p = post(url = "https://example.com/image.jpg")
        assertEquals("https://example.com/image.jpg", p.imageUrl)
    }
}
