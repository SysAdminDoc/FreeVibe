package com.freevibe.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FreeVibeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate8To9_preservesCachedWallpaperAndBackfillsMetadataDefaults() {
        createVersion8Database()

        helper.runMigrationsAndValidate(TEST_DB, 9, true, DatabaseMigrations.MIGRATION_8_9).use { db ->
            db.query(
                """
                SELECT source, thumbnailUrl, colors, sourcePageUrl, views, favorites
                FROM wallpaper_cache
                WHERE id = 'wp-1' AND cacheKey = 'discover:nature'
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("pexels", cursor.getString(0))
                assertEquals("https://example.com/thumb.jpg", cursor.getString(1))
                assertEquals("", cursor.getString(2))
                assertEquals("", cursor.getString(3))
                assertEquals(0, cursor.getInt(4))
                assertEquals(0, cursor.getInt(5))
            }
        }
    }

    private fun createVersion8Database() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)

        val dbFile = context.getDatabasePath(TEST_DB).apply {
            parentFile?.mkdirs()
        }

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            createVersion8Schema(db)
            db.execSQL(
                """
                INSERT INTO wallpaper_cache (
                    id, source, thumbnailUrl, fullUrl, width, height, category, tags,
                    fileSize, fileType, uploaderName, cacheKey, cachedAt
                ) VALUES (
                    'wp-1', 'pexels', 'https://example.com/thumb.jpg', 'https://example.com/full.jpg',
                    1080, 1920, 'nature', 'forest,green', 512000, 'image/jpeg', 'Aura',
                    'discover:nature', 1700000000
                )
                """.trimIndent()
            )
            db.version = 8
        }
    }

    // Mirrors the v8 schema immediately before MIGRATION_8_9 adds wallpaper_cache metadata columns.
    private fun createVersion8Schema(db: SQLiteDatabase) {
        VERSION_8_SCHEMA.forEach(db::execSQL)
    }

    companion object {
        private const val TEST_DB = "room-migration-test.db"

        private val VERSION_8_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `favorites` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `type` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `name` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `duration` REAL NOT NULL, `addedAt` INTEGER NOT NULL, `offlinePath` TEXT NOT NULL, `tags` TEXT, `colors` TEXT, `category` TEXT, `uploaderName` TEXT, `sourcePageUrl` TEXT, `fileSize` INTEGER, `fileType` TEXT, `views` INTEGER, `favoritesCount` INTEGER, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_favorites_type` ON `favorites` (`type`)",
            "CREATE TABLE IF NOT EXISTS `downloads` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `type` TEXT NOT NULL, `localPath` TEXT NOT NULL, `name` TEXT NOT NULL, `downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE INDEX IF NOT EXISTS `index_downloads_type` ON `downloads` (`type`)",
            "CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`query`, `type`))",
            "CREATE TABLE IF NOT EXISTS `wallpaper_cache` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `category` TEXT NOT NULL, `tags` TEXT NOT NULL, `fileSize` INTEGER NOT NULL, `fileType` TEXT NOT NULL, `uploaderName` TEXT NOT NULL, `cacheKey` TEXT NOT NULL, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`id`, `cacheKey`))",
            "CREATE INDEX IF NOT EXISTS `index_wallpaper_cache_cacheKey` ON `wallpaper_cache` (`cacheKey`)",
            "CREATE TABLE IF NOT EXISTS `wallpaper_history` (`historyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `wallpaperId` TEXT NOT NULL, `source` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `target` TEXT NOT NULL, `appliedAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `wallpaper_collections` (`collectionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `wallpaper_collection_items` (`collectionId` INTEGER NOT NULL, `wallpaperId` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `source` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`collectionId`, `wallpaperId`), FOREIGN KEY(`collectionId`) REFERENCES `wallpaper_collections`(`collectionId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_wallpaper_collection_items_collectionId` ON `wallpaper_collection_items` (`collectionId`)",
        )
    }
}
