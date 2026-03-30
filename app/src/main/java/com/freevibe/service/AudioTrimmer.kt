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
        fadeInMs: Long = 0,
        fadeOutMs: Long = 0,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(endMs > startMs) { "End time must be after start time" }

            val outputDir = File(context.cacheDir, "trimmed")
            outputDir.mkdirs()
            val ext = inputPath.substringAfterLast(".", "mp3")
            val outputFile = File(outputDir, "${outputFileName.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.$ext")

            val extractor = MediaExtractor()
            var muxer: MediaMuxer? = null
            try {
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

                val muxerFormat = when (ext.lowercase()) {
                    "mp4", "m4a", "aac" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                    "webm", "ogg" -> MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM
                    else -> MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                }

                muxer = MediaMuxer(outputFile.absolutePath, muxerFormat)
                val muxerTrackIndex = muxer.addTrack(audioFormat)
                muxer.start()

                val startUs = startMs * 1000L
                val endUs = endMs * 1000L

                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

                val bufferSize = audioFormat.getInteger(
                    MediaFormat.KEY_MAX_INPUT_SIZE,
                    256 * 1024,
                ).coerceIn(8192, 1024 * 1024)
                val buffer = ByteBuffer.allocate(bufferSize)
                val bufferInfo = MediaCodec.BufferInfo()

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
            } finally {
                try { muxer?.release() } catch (_: Exception) {}
                try { extractor.release() } catch (_: Exception) {}
            }

            // Apply fade effects via FFmpeg (proper decode→fade→encode, no audio corruption)
            if (fadeInMs > 0 || fadeOutMs > 0) {
                applyFadeViaFfmpeg(outputFile, fadeInMs, fadeOutMs, endMs - startMs)
            }

            outputFile.absolutePath
        }
    }

    /**
     * Apply fade in/out using FFmpeg for proper audio processing.
     * Decodes, applies fade filter, re-encodes. No byte-level corruption.
     */
    private fun applyFadeViaFfmpeg(file: File, fadeInMs: Long, fadeOutMs: Long, totalDurationMs: Long) {
        val ffmpegInfo = getYtdlpFfmpeg() ?: return
        val (ffmpegPath, ldLibPath) = ffmpegInfo

        val tempOut = File(file.parentFile, "fade_${file.name}")
        try {
            val filters = mutableListOf<String>()
            if (fadeInMs > 0) {
                val fadeInSec = fadeInMs / 1000.0
                filters.add("afade=t=in:st=0:d=$fadeInSec")
            }
            if (fadeOutMs > 0) {
                val fadeOutSec = fadeOutMs / 1000.0
                val startSec = (totalDurationMs - fadeOutMs) / 1000.0
                filters.add("afade=t=out:st=$startSec:d=$fadeOutSec")
            }

            val cmd = mutableListOf(
                ffmpegPath.absolutePath,
                "-y",
                "-i", file.absolutePath,
                "-af", filters.joinToString(","),
                "-c:a", "libmp3lame",
                "-q:a", "2",
                tempOut.absolutePath,
            )

            val pb = ProcessBuilder(cmd).redirectErrorStream(true).directory(file.parentFile)
            if (ldLibPath.isNotEmpty()) pb.environment()["LD_LIBRARY_PATH"] = ldLibPath
            val process = pb.start()
            process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0 && tempOut.exists() && tempOut.length() > 1024) {
                tempOut.copyTo(file, overwrite = true)
            }
        } catch (e: Exception) {
            if (com.freevibe.BuildConfig.DEBUG) android.util.Log.e("AudioTrimmer", "FFmpeg fade failed: ${e.message}")
        } finally {
            try { tempOut.delete() } catch (_: Exception) {}
        }
    }

    /** Get FFmpeg binary and LD_LIBRARY_PATH from yt-dlp via reflection */
    private fun getYtdlpFfmpeg(): Pair<File, String>? {
        return try {
            val ytdl = com.yausername.youtubedl_android.YoutubeDL.getInstance()
            val cls = ytdl::class.java
            val ffmpegField = cls.getDeclaredField("ffmpegPath").apply { isAccessible = true }
            val ldField = cls.getDeclaredField("ENV_LD_LIBRARY_PATH").apply { isAccessible = true }
            // Try instance field first, then static
            val ffmpegPath = (ffmpegField.get(ytdl) ?: ffmpegField.get(null)) as? File ?: return null
            val ldPath = (ldField.get(ytdl) ?: ldField.get(null)) as? String ?: ""
            if (ffmpegPath.exists()) Pair(ffmpegPath, ldPath) else null
        } catch (_: Exception) { null }
    }

    /** Apply volume normalization via FFmpeg loudnorm filter */
    suspend fun normalize(inputPath: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val ffmpegInfo = getYtdlpFfmpeg() ?: throw Exception("FFmpeg not available")
            val (ffmpegPath, ldLibPath) = ffmpegInfo
            val input = File(inputPath)
            val output = File(input.parentFile, "norm_${input.name}")

            val cmd = listOf(
                ffmpegPath.absolutePath, "-y",
                "-i", inputPath,
                "-af", "loudnorm=I=-16:TP=-1.5:LRA=11",
                "-c:a", "libmp3lame", "-q:a", "2",
                output.absolutePath,
            )
            val pb = ProcessBuilder(cmd).redirectErrorStream(true).directory(input.parentFile)
            if (ldLibPath.isNotEmpty()) pb.environment()["LD_LIBRARY_PATH"] = ldLibPath
            val process = pb.start()
            process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.exists() && output.length() > 1024) {
                output.copyTo(input, overwrite = true)
                output.delete()
            } else {
                output.delete()
                throw Exception("Normalization failed (exit $exitCode)")
            }
            inputPath
        }
    }

    /** Convert audio format via FFmpeg */
    suspend fun convert(inputPath: String, targetFormat: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val ffmpegInfo = getYtdlpFfmpeg() ?: throw Exception("FFmpeg not available")
            val (ffmpegPath, ldLibPath) = ffmpegInfo
            val input = File(inputPath)
            val outputName = input.nameWithoutExtension + "." + targetFormat
            val output = File(input.parentFile, outputName)

            val codec = when (targetFormat.lowercase()) {
                "mp3" -> listOf("-c:a", "libmp3lame", "-q:a", "2")
                "ogg" -> listOf("-c:a", "libvorbis", "-q:a", "6")
                "wav" -> listOf("-c:a", "pcm_s16le")
                "flac" -> listOf("-c:a", "flac")
                "m4a", "aac" -> listOf("-c:a", "aac", "-b:a", "192k")
                else -> listOf("-c:a", "libmp3lame", "-q:a", "2")
            }

            val cmd = mutableListOf(ffmpegPath.absolutePath, "-y", "-i", inputPath) + codec + listOf(output.absolutePath)
            val pb = ProcessBuilder(cmd).redirectErrorStream(true).directory(input.parentFile)
            if (ldLibPath.isNotEmpty()) pb.environment()["LD_LIBRARY_PATH"] = ldLibPath
            val process = pb.start()
            process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (exitCode == 0 && output.exists() && output.length() > 100) {
                output.absolutePath
            } else {
                output.delete()
                throw Exception("Conversion failed (exit $exitCode)")
            }
        }
    }

    /** Clean up trimmed files cache */
    fun clearTrimCache() {
        File(context.cacheDir, "trimmed").deleteRecursively()
    }
}
