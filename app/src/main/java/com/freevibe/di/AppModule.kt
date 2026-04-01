package com.freevibe.di

import android.content.Context
import androidx.room.Room
import com.freevibe.data.local.CollectionDao
import com.freevibe.data.local.DatabaseMigrations
import com.freevibe.data.local.DownloadDao
import com.freevibe.data.local.FavoriteDao
import com.freevibe.data.local.FreeVibeDatabase
import com.freevibe.data.local.SearchHistoryDao
import com.freevibe.data.local.WallpaperCacheDao
import com.freevibe.data.local.WallpaperHistoryDao
import com.freevibe.data.remote.bing.BingDailyApi
import com.freevibe.data.remote.freesound.FreesoundV2Api
import com.freevibe.data.remote.weather.OpenMeteoApi
import com.freevibe.data.remote.pexels.PexelsApi
import com.freevibe.data.remote.picsum.PicsumApi
import com.freevibe.data.remote.pixabay.PixabayApi
import com.freevibe.data.remote.reddit.RedditApi
import com.freevibe.data.remote.freesound.FreesoundApi
import com.freevibe.data.remote.soundcloud.SoundCloudApi
import com.freevibe.data.remote.wallhaven.WallhavenApi
import com.squareup.moshi.Moshi
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
        .writeTimeout(60, TimeUnit.SECONDS)
        .apply {
            if (com.freevibe.BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Aura/${com.freevibe.BuildConfig.VERSION_NAME} (Android; Open Source)")
                .build()
            chain.proceed(request)
        }
        .build()

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
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
    fun provideFreesoundV2Api(client: OkHttpClient, moshi: Moshi): FreesoundV2Api =
        Retrofit.Builder()
            .baseUrl(FreesoundV2Api.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FreesoundV2Api::class.java)

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
    fun providePexelsApi(client: OkHttpClient, moshi: Moshi): PexelsApi =
        Retrofit.Builder()
            .baseUrl(PexelsApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PexelsApi::class.java)

    @Provides
    @Singleton
    fun providePixabayApi(client: OkHttpClient, moshi: Moshi): PixabayApi =
        Retrofit.Builder()
            .baseUrl(PixabayApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(PixabayApi::class.java)

    @Provides
    @Singleton
    fun provideFreesoundApi(client: OkHttpClient, moshi: Moshi): FreesoundApi =
        Retrofit.Builder()
            .baseUrl("https://api.openverse.org/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FreesoundApi::class.java)

    @Provides
    @Singleton
    fun provideOpenMeteoApi(client: OkHttpClient, moshi: Moshi): OpenMeteoApi =
        Retrofit.Builder()
            .baseUrl(OpenMeteoApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoApi::class.java)

    @Provides
    @Singleton
    fun provideSoundCloudApi(client: OkHttpClient, moshi: Moshi): SoundCloudApi =
        Retrofit.Builder()
            .baseUrl(SoundCloudApi.BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(SoundCloudApi::class.java)

    // -- Database --

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FreeVibeDatabase =
        Room.databaseBuilder(context, FreeVibeDatabase::class.java, "freevibe.db")
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
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
    fun provideCollectionDao(db: FreeVibeDatabase): CollectionDao = db.collectionDao()
}
