package com.freevibe.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreatorProfileRepositoryTest {

    @Test
    fun `aggregateCreatorStats totals uploads votes and follow state`() {
        val uploads = listOf(
            upload("s1", "creator_a", "Alex", "sound", votes = 3),
            upload("w1", "creator_a", "Alex", "wallpaper", votes = 5),
            upload("s2", "creator_b", "Blair", "sound", votes = -2),
        )

        val stats = aggregateCreatorStats(
            uploads = uploads,
            currentUserIds = setOf("creator_a"),
            followedCreatorIds = setOf("creator_a", "creator_b"),
        )

        val alex = stats.first { it.creatorId == "creator_a" }
        assertEquals(1, alex.soundUploads)
        assertEquals(1, alex.wallpaperUploads)
        assertEquals(8, alex.totalVotes)
        assertFalse(alex.isFollowed)

        val blair = stats.first { it.creatorId == "creator_b" }
        assertEquals(1, blair.soundUploads)
        assertEquals(0, blair.totalVotes)
        assertTrue(blair.isFollowed)
    }

    @Test
    fun `aggregateCreatorStats sorts by votes then upload count`() {
        val stats = aggregateCreatorStats(
            uploads = listOf(
                upload("a1", "a", "A", "sound", votes = 4),
                upload("b1", "b", "B", "wallpaper", votes = 4),
                upload("b2", "b", "B", "sound", votes = 0),
                upload("c1", "c", "C", "sound", votes = 9),
            ),
        )

        assertEquals(listOf("c", "b", "a"), stats.map { it.creatorId })
    }

    private fun upload(
        id: String,
        creatorId: String,
        label: String,
        type: String,
        votes: Int,
    ) = CreatorUploadRef(
        id = id,
        stableKey = "stable_$id",
        contentType = type,
        title = id,
        creatorId = creatorId,
        creatorLabel = label,
        thumbnailUrl = "",
        votes = votes,
        uploadedAt = 0L,
    )
}
