package com.freevibe.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.local.IAAudioCacheDao
import com.freevibe.data.local.SearchHistoryDao
import com.freevibe.data.local.WallpaperCacheDao
import com.freevibe.data.local.WallpaperHistoryDao
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.internetarchive.InternetArchiveApi
import com.freevibe.data.remote.nasa.NasaApodApi
import com.freevibe.data.remote.picsum.PicsumApi
import com.freevibe.data.remote.reddit.RedditApi
import com.freevibe.data.remote.wallhaven.WallhavenApi
import com.freevibe.data.remote.wikimedia.WikimediaApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // -- OkHttp --

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "FreeVibe/0.8.0 (Android; Open Source)")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    // -- API Services --

    @Provides
    @Singleton
    fun provideWallhavenApi(client: OkHttpClient, moshi: Moshi): WallhavenApi =
        Retrofit.Builder()
            .baseUrl("https://wallhaven.cc/api/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WallhavenApi::class.java)

    @Provides
    @Singleton
    fun providePicsumApi(client: OkHttpClient, moshi: Moshi): PicsumApi =
        Retrofit.Builder()
            .baseUrl(PicsumApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PicsumApi::class.java)

    @Provides
    @Singleton
    fun provideInternetArchiveApi(client: OkHttpClient, moshi: Moshi): InternetArchiveApi =
        Retrofit.Builder()
            .baseUrl(InternetArchiveApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(InternetArchiveApi::class.java)

    @Provides
    @Singleton
    fun provideRedditApi(client: OkHttpClient, moshi: Moshi): RedditApi =
        Retrofit.Builder()
            .baseUrl(RedditApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(RedditApi::class.java)

    @Provides
    @Singleton
    fun provideBingDailyApi(client: OkHttpClient, moshi: Moshi): BingDailyApi =
        Retrofit.Builder()
            .baseUrl(BingDailyApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(BingDailyApi::class.java)

    @Provides
    @Singleton
    fun provideWikimediaApi(client: OkHttpClient, moshi: Moshi): WikimediaApi =
        Retrofit.Builder()
            .baseUrl(WikimediaApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WikimediaApi::class.java)

    @Provides
    @Singleton
    fun provideNasaApi(client: OkHttpClient, moshi: Moshi): NasaApodApi =
        Retrofit.Builder()
            .baseUrl(NasaApodApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(NasaApodApi::class.java)

    // -- Database --

    // Migrations preserve user data (favorites, downloads, history) across schema changes.
    // v1→2: Added wallpaper_cache + wallpaper_history tables
    // v2→3: Added ia_audio_cache table
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallpaper_cache` (`id` TEXT NOT NULL, `source` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `category` TEXT NOT NULL DEFAULT '', `tags` TEXT NOT NULL DEFAULT '', `fileSize` INTEGER NOT NULL DEFAULT 0, `fileType` TEXT NOT NULL DEFAULT '', `uploaderName` TEXT NOT NULL DEFAULT '', `cacheKey` TEXT NOT NULL DEFAULT '', `cachedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallpaper_history` (`historyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `wallpaperId` TEXT NOT NULL, `source` TEXT NOT NULL, `thumbnailUrl` TEXT NOT NULL, `fullUrl` TEXT NOT NULL, `width` INTEGER NOT NULL DEFAULT 0, `height` INTEGER NOT NULL DEFAULT 0, `target` TEXT NOT NULL DEFAULT 'BOTH', `appliedAt` INTEGER NOT NULL DEFAULT 0)")
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `ia_audio_cache` (`identifier` TEXT NOT NULL, `audioUrl` TEXT NOT NULL, `duration` REAL NOT NULL DEFAULT 0.0, `fileSize` INTEGER NOT NULL DEFAULT 0, `cachedAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`identifier`))")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FreeVibeDatabase =
        Room.databaseBuilder(context, FreeVibeDatabase::class.java, "freevibe.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    fun provideFavoriteDao(db: FreeVibeDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideDownloadDao(db: FreeVibeDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideSearchHistoryDao(db: FreeVibeDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    fun provideWallpaperCacheDao(db: FreeVibeDatabase): WallpaperCacheDao = db.wallpaperCacheDao()

    @Provides
    fun provideWallpaperHistoryDao(db: FreeVibeDatabase): WallpaperHistoryDao = db.wallpaperHistoryDao()

    @Provides
    fun provideIAAudioCacheDao(db: FreeVibeDatabase): IAAudioCacheDao = db.iaAudioCacheDao()
}
