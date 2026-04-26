package com.freevibe.service

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.freevibe.BuildConfig
import com.freevibe.data.model.Sound
import com.freevibe.data.model.stableKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class AudioPreviewCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val upstreamFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Aura/${BuildConfig.VERSION_NAME} (Android; Open Source)")
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
        .setAllowCrossProtocolRedirects(true)

    private val cache = SimpleCache(
        File(context.cacheDir, "audio-preview-cache"),
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
        StandaloneDatabaseProvider(context),
    )

    private val dataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    fun mediaSourceFactory(): DefaultMediaSourceFactory =
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, dataSourceFactory))

    suspend fun prebuffer(sound: Sound): Boolean {
        val url = sound.previewUrl.takeIf { it.isNotBlank() } ?: return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return true

        return withContext(Dispatchers.IO) {
            try {
                val dataSpec = DataSpec.Builder()
                    .setUri(Uri.parse(url))
                    .setKey(sound.stableKey())
                    .setPosition(0)
                    .setLength(PREBUFFER_BYTES)
                    .build()
                CacheWriter(dataSourceFactory.createDataSource(), dataSpec, null, null).cache()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        private const val PREBUFFER_BYTES = 192L * 1024L
        private const val MAX_CACHE_BYTES = 48L * 1024L * 1024L
    }
}
