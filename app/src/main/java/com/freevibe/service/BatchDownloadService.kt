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

    private var job: Job? = null

    fun downloadBatch(wallpapers: List<Wallpaper>, concurrency: Int = 3) {
        if (_state.value.isRunning) return

        _state.value = BatchDownloadState(totalCount = wallpapers.size, isRunning = true)
        job = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val semaphore = kotlinx.coroutines.sync.Semaphore(concurrency)

            wallpapers.mapIndexed { index, wp ->
                async {
                    semaphore.acquire()
                    try {
                        _state.update { it.copy(currentItem = wp.id) }
                        val ext = wp.fileType.substringAfterLast("/", "jpg").substringAfterLast(".", "jpg")
                        try {
                            downloadManager.downloadWallpaper(
                                id = "batch_${wp.id}",
                                url = wp.fullUrl,
                                fileName = "FreeVibe_${wp.id}.$ext",
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

            _state.update { it.copy(isRunning = false) }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.update { it.copy(isRunning = false) }
    }

    fun reset() {
        _state.value = BatchDownloadState()
    }
}
