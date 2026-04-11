package com.freevibe.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    // v1→2: Added wallpaper_cache + wallpaper_history tables
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallpaper_cache` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `category` TEXT NOT NULL DEFAULT '', `tags` TEXT NOT NULL DEFAULT '', `fileSize` INTEGER NOT NULL DEFAULT 0, `fileType` TEXT NOT NULL DEFAULT '', `uploaderName` TEXT NOT NULL DEFAULT '', `cacheKey` TEXT NOT NULL DEFAULT '', `cachedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallpaper_history` (`historyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `wallpaperId` TEXT NOT NULL, `source` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `width` INTEGER NOT NULL DEFAULT 0, `height` INTEGER NOT NULL DEFAULT 0, `target` TEXT NOT NULL DEFAULT 'BOTH', `appliedAt` INTEGER NOT NULL DEFAULT 0)")
        }
    }

    // v2→3: Added ia_audio_cache table
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `ia_audio_cache` (`identifier` TEXT NOT NULL, `audioUrl` TEXT NOT NULL, `duration` REAL NOT NULL DEFAULT 0.0, `fileSize` INTEGER NOT NULL DEFAULT 0, `cachedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`identifier`))")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallpaper_collections` (`collectionId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallpaper_collection_items` (`collectionId` INTEGER NOT NULL, `wallpaperId` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `source` TEXT NOT NULL, `width` INTEGER NOT NULL DEFAULT 0, `height` INTEGER NOT NULL DEFAULT 0, `addedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`collectionId`, `wallpaperId`))")
        }
    }

    // v4→5: Composite PK for search_history (query+type) and wallpaper_cache (id+cacheKey)
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `search_history_new` (`query` TEXT NOT NULL, `type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`query`, `type`))")
            db.execSQL("INSERT OR IGNORE INTO `search_history_new` SELECT `query`, `type`, `timestamp` FROM `search_history`")
            db.execSQL("DROP TABLE `search_history`")
            db.execSQL("ALTER TABLE `search_history_new` RENAME TO `search_history`")

            db.execSQL("CREATE TABLE IF NOT EXISTS `wallpaper_cache_new` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `category` TEXT NOT NULL DEFAULT '', `tags` TEXT NOT NULL DEFAULT '', `fileSize` INTEGER NOT NULL DEFAULT 0, `fileType` TEXT NOT NULL DEFAULT '', `uploaderName` TEXT NOT NULL DEFAULT '', `cacheKey` TEXT NOT NULL DEFAULT '', `cachedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`, `cacheKey`))")
            db.execSQL("INSERT OR IGNORE INTO `wallpaper_cache_new` SELECT `id`, `source`, `thumbnailUrl`, `fullUrl`, `width`, `height`, `category`, `tags`, `fileSize`, `fileType`, `uploaderName`, `cacheKey`, `cachedAt` FROM `wallpaper_cache`")
            db.execSQL("DROP TABLE `wallpaper_cache`")
            db.execSQL("ALTER TABLE `wallpaper_cache_new` RENAME TO `wallpaper_cache`")
        }
    }

    // v5→6: Add ForeignKey + index on wallpaper_collection_items.collectionId
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallpaper_collection_items_new` (`collectionId` INTEGER NOT NULL, `wallpaperId` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `source` TEXT NOT NULL, `width` INTEGER NOT NULL DEFAULT 0, `height` INTEGER NOT NULL DEFAULT 0, `addedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`collectionId`, `wallpaperId`), FOREIGN KEY(`collectionId`) REFERENCES `wallpaper_collections`(`collectionId`) ON DELETE CASCADE)")
            db.execSQL("INSERT OR IGNORE INTO `wallpaper_collection_items_new` SELECT `collectionId`, `wallpaperId`, `thumbnailUrl`, `fullUrl`, `source`, `width`, `height`, `addedAt` FROM `wallpaper_collection_items`")
            db.execSQL("DROP TABLE `wallpaper_collection_items`")
            db.execSQL("ALTER TABLE `wallpaper_collection_items_new` RENAME TO `wallpaper_collection_items`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_collection_items_collectionId` ON `wallpaper_collection_items` (`collectionId`)")
        }
    }

    // v6→7: Drop ia_audio_cache table (Internet Archive removed)
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `ia_audio_cache`")
        }
    }

    // v7→8: Add metadata columns to favorites table
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `tags` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `colors` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `category` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `uploaderName` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `sourcePageUrl` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `fileSize` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `fileType` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `views` INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE `favorites` ADD COLUMN `favoritesCount` INTEGER DEFAULT NULL")
        }
    }

    // v8→9: Add colors, sourcePageUrl, views, favorites columns to wallpaper_cache
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `wallpaper_cache` ADD COLUMN `colors` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `wallpaper_cache` ADD COLUMN `sourcePageUrl` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `wallpaper_cache` ADD COLUMN `views` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `wallpaper_cache` ADD COLUMN `favorites` INTEGER NOT NULL DEFAULT 0")
        }
    }

    // v9→10: Add missing indices on favorites.type, downloads.type, wallpaper_cache.cacheKey
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_type` ON `favorites` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_type` ON `downloads` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_cache_cacheKey` ON `wallpaper_cache` (`cacheKey`)")
        }
    }

    // v10→11: Add performance indexes on addedAt, downloadedAt, appliedAt
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_addedAt` ON `favorites` (`addedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_type_addedAt` ON `favorites` (`type`, `addedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_downloadedAt` ON `downloads` (`downloadedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_history_appliedAt` ON `wallpaper_history` (`appliedAt`)")
        }
    }

    // v11→12: Composite favorites identity (id + source + type)
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `favorites_new` (" +
                    "`id` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "`type` TEXT NOT NULL, " +
                    "`thumbnailUrl` TEXT NOT NULL, " +
                    "`fullUrl` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`width` INTEGER NOT NULL, " +
                    "`height` INTEGER NOT NULL, " +
                    "`duration` REAL NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, " +
                    "`offlinePath` TEXT NOT NULL, " +
                    "`tags` TEXT, " +
                    "`colors` TEXT, " +
                    "`category` TEXT, " +
                    "`uploaderName` TEXT, " +
                    "`sourcePageUrl` TEXT, " +
                    "`fileSize` INTEGER, " +
                    "`fileType` TEXT, " +
                    "`views` INTEGER, " +
                    "`favoritesCount` INTEGER, " +
                    "PRIMARY KEY(`id`, `source`, `type`))"
            )
            db.execSQL(
                "INSERT OR REPLACE INTO `favorites_new` " +
                    "SELECT `id`, `source`, `type`, `thumbnailUrl`, `fullUrl`, `name`, `width`, `height`, `duration`, `addedAt`, `offlinePath`, `tags`, `colors`, `category`, `uploaderName`, `sourcePageUrl`, `fileSize`, `fileType`, `views`, `favoritesCount` " +
                    "FROM `favorites`"
            )
            db.execSQL("DROP TABLE `favorites`")
            db.execSQL("ALTER TABLE `favorites_new` RENAME TO `favorites`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_type` ON `favorites` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_addedAt` ON `favorites` (`addedAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_type_addedAt` ON `favorites` (`type`, `addedAt`)")
        }
    }

    // v12→13: Collections become source-aware so duplicate ids from different providers can coexist
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wallpaper_collection_items_new` (" +
                    "`collectionId` INTEGER NOT NULL, " +
                    "`wallpaperId` TEXT NOT NULL, " +
                    "`thumbnailUrl` TEXT NOT NULL, " +
                    "`fullUrl` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "`width` INTEGER NOT NULL DEFAULT 0, " +
                    "`height` INTEGER NOT NULL DEFAULT 0, " +
                    "`addedAt` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`collectionId`, `wallpaperId`, `source`), " +
                    "FOREIGN KEY(`collectionId`) REFERENCES `wallpaper_collections`(`collectionId`) ON DELETE CASCADE)"
            )
            db.execSQL(
                "INSERT OR IGNORE INTO `wallpaper_collection_items_new` " +
                    "SELECT `collectionId`, `wallpaperId`, `thumbnailUrl`, `fullUrl`, `source`, `width`, `height`, `addedAt` " +
                    "FROM `wallpaper_collection_items`"
            )
            db.execSQL("DROP TABLE `wallpaper_collection_items`")
            db.execSQL("ALTER TABLE `wallpaper_collection_items_new` RENAME TO `wallpaper_collection_items`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_collection_items_collectionId` ON `wallpaper_collection_items` (`collectionId`)")
        }
    }

    // v13→14: wallpaper_cache becomes source-aware so mixed-source discover results can cache duplicate raw ids safely
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `wallpaper_cache_new` (" +
                    "`id` TEXT NOT NULL, " +
                    "`source` TEXT NOT NULL, " +
                    "`thumbnailUrl` TEXT NOT NULL, " +
                    "`fullUrl` TEXT NOT NULL, " +
                    "`width` INTEGER NOT NULL, " +
                    "`height` INTEGER NOT NULL, " +
                    "`category` TEXT NOT NULL DEFAULT '', " +
                    "`tags` TEXT NOT NULL DEFAULT '', " +
                    "`fileSize` INTEGER NOT NULL DEFAULT 0, " +
                    "`fileType` TEXT NOT NULL DEFAULT '', " +
                    "`uploaderName` TEXT NOT NULL DEFAULT '', " +
                    "`colors` TEXT NOT NULL DEFAULT '', " +
                    "`sourcePageUrl` TEXT NOT NULL DEFAULT '', " +
                    "`views` INTEGER NOT NULL DEFAULT 0, " +
                    "`favorites` INTEGER NOT NULL DEFAULT 0, " +
                    "`cacheKey` TEXT NOT NULL DEFAULT '', " +
                    "`cachedAt` INTEGER NOT NULL DEFAULT 0, " +
                    "PRIMARY KEY(`id`, `source`, `cacheKey`))"
            )
            db.execSQL(
                "INSERT OR REPLACE INTO `wallpaper_cache_new` " +
                    "SELECT `id`, `source`, `thumbnailUrl`, `fullUrl`, `width`, `height`, `category`, `tags`, `fileSize`, `fileType`, `uploaderName`, `colors`, `sourcePageUrl`, `views`, `favorites`, `cacheKey`, `cachedAt` " +
                    "FROM `wallpaper_cache`"
            )
            db.execSQL("DROP TABLE `wallpaper_cache`")
            db.execSQL("ALTER TABLE `wallpaper_cache_new` RENAME TO `wallpaper_cache`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallpaper_cache_cacheKey` ON `wallpaper_cache` (`cacheKey`)")
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
    )
}
