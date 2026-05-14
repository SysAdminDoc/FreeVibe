package com.freevibe.service

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityAudioRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    fun start(): Result<Unit> = runCatching {
        check(recorder == null) { "Recording is already in progress" }

        val directory = File(context.cacheDir, "sounds/community_recordings").apply { mkdirs() }
        val file = File(directory, "community_${System.currentTimeMillis()}.m4a")
        val activeRecorder = createRecorder()
        try {
            activeRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            activeRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            activeRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            activeRecorder.setAudioEncodingBitRate(128_000)
            activeRecorder.setAudioSamplingRate(44_100)
            activeRecorder.setOutputFile(file.absolutePath)
            activeRecorder.setMaxDuration(MAX_RECORDING_MS)
            activeRecorder.prepare()
            activeRecorder.start()
        } catch (e: Exception) {
            runCatching { activeRecorder.reset() }
            runCatching { activeRecorder.release() }
            file.delete()
            throw e
        }

        outputFile = file
        recorder = activeRecorder
    }

    fun stop(): Result<Uri> {
        val activeRecorder = recorder ?: return Result.failure(IllegalStateException("No recording in progress"))
        val file = outputFile
        if (file == null) {
            release()
            return Result.failure(IllegalStateException("Recording output unavailable"))
        }

        return try {
            activeRecorder.stop()
            release()
            if (!file.exists() || file.length() <= MIN_RECORDING_BYTES) {
                file.delete()
                Result.failure(IllegalArgumentException("Recording was too short"))
            } else {
                Result.success(
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file,
                    ),
                )
            }
        } catch (e: RuntimeException) {
            release()
            file.delete()
            Result.failure(IllegalArgumentException("Recording was too short"))
        } catch (e: Exception) {
            release()
            file.delete()
            Result.failure(e)
        }
    }

    fun cancel() {
        val file = outputFile
        release()
        file?.delete()
    }

    private fun release() {
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }

    private companion object {
        const val MAX_RECORDING_MS = 60_000
        const val MIN_RECORDING_BYTES = 512L
    }
}
