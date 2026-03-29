package com.freevibe.service

import com.freevibe.data.model.Wallpaper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class BatchDownloadState(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val isRunning: Boolean = false,
    val currentItem: String = "",
) {
    val progress: Float get() = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
    val isComplete: Boolean get() = completedCount + failedCount >= totalCount && totalCount > 0
}

@Singleton
class BatchDownloadService @Inject constructor(
    private val downloadManager: DownloadManager,
) {
    private val _state = MutableStateFlow(BatchDownloadState())
    val state = _state.asStateFlow()

    private var scope: CoroutineScope? = null

    fun downloadBatch(wallpapers: List<Wallpaper>, concurrency: Int = 3) {
        if (_state.value.isRunning) return

        _state.value = BatchDownloadState(totalCount = wallpapers.size, isRunning = true)
        scope?.cancel()
        val newScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = newScope
        newScope.launch {
            try {
                val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)

                wallpapers.map { wp ->
                    async {
                        semaphore.acquire()
                        try {
                            _state.update { it.copy(currentItem = wp.id) }
                            val ext = guessBatchExtension(wp.fileType)
                            try {
                                downloadManager.downloadWallpaper(
                                    id = "batch_${wp.id}",
                                    url = wp.fullUrl,
                                    fileName = "Aura_${wp.id}.$ext",
                                ).onSuccess {
                                    _state.update { it.copy(completedCount = it.completedCount + 1) }
                                }.onFailure {
                                    _state.update { it.copy(failedCount = it.failedCount + 1) }
                                }
                            } catch (_: Exception) {
                                _state.update { it.copy(failedCount = it.failedCount + 1) }
                            }
                        } finally {
                            semaphore.release()
                        }
                    }
                }.awaitAll()
            } finally {
                _state.update { it.copy(isRunning = false) }
            }
        }
    }

    fun cancel() {
        scope?.cancel()
        scope = null
        _state.update { it.copy(isRunning = false) }
    }

    fun reset() {
        _state.value = BatchDownloadState()
    }

    private fun guessBatchExtension(fileType: String): String = when {
        fileType.contains("png", true) -> "png"
        fileType.contains("webp", true) -> "webp"
        fileType.contains("gif", true) -> "gif"
        fileType.contains("jpeg", true) || fileType.contains("jpg", true) -> "jpg"
        else -> "jpg"
    }
}
