package com.freevibe.service

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioTrimmer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Trim audio file losslessly using MediaExtractor + MediaMuxer.
     * Returns path to trimmed output file.
     */
    suspend fun trim(
        inputPath: String,
        startMs: Long,
        endMs: Long,
        outputFileName: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(endMs > startMs) { "End time must be after start time" }

            val outputDir = File(context.cacheDir, "trimmed")
            outputDir.mkdirs()
            val ext = inputPath.substringAfterLast(".", "mp3")
            val outputFile = File(outputDir, "${outputFileName.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.$ext")

            val extractor = MediaExtractor()
            extractor.setDataSource(inputPath)

            // Find audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            require(audioTrackIndex >= 0 && audioFormat != null) { "No audio track found" }

            extractor.selectTrack(audioTrackIndex)

            // Determine muxer format from extension
            val muxerFormat = when (ext.lowercase()) {
                "mp4", "m4a", "aac" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                "webm", "ogg" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            }

            val muxer = MediaMuxer(outputFile.absolutePath, muxerFormat)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            // Seek to start position
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val bufferSize = audioFormat.getInteger(
                MediaFormat.KEY_MAX_INPUT_SIZE,
                256 * 1024,
            )
            val buffer = ByteBuffer.allocate(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy samples within range
            while (true) {
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val sampleTime = extractor.sampleTime
                if (sampleTime > endUs) break

                if (sampleTime >= startUs) {
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = sampleTime - startUs
                    bufferInfo.flags = extractor.sampleFlags

                    muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                }

                extractor.advance()
                buffer.clear()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            outputFile.absolutePath
        }
    }

    /** Clean up trimmed files cache */
    fun clearTrimCache() {
        File(context.cacheDir, "trimmed").deleteRecursively()
    }
}
