package com.freevibe.ui.screens.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.local.DownloadDao
import com.freevibe.service.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadDao: DownloadDao,
    private val downloadManager: DownloadManager,
) : ViewModel() {
    val allDownloads = downloadDao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val wallpaperDownloads = downloadDao.getByType("WALLPAPER").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val soundDownloads = downloadDao.getByType("SOUND").stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeDownloads = downloadManager.activeDownloads

    fun deleteDownload(id: String) = viewModelScope.launch { downloadManager.deleteDownload(id) }
    fun dismissActive(id: String) = downloadManager.clearCompleted(id)
}
