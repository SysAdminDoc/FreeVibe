package com.freevibe.ui.screens.favorites

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.favoriteIdentity
import com.freevibe.data.model.stableKey
import com.freevibe.data.remote.toWallpaper
import com.freevibe.data.remote.toSound
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.service.BatchDownloadService
import com.freevibe.service.FavoritesExporter
import com.freevibe.service.SelectedContentHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val favoritesRepo: FavoritesRepository,
    private val exporter: FavoritesExporter,
    private val selectedContent: SelectedContentHolder,
    private val batchDownloadService: BatchDownloadService,
) : ViewModel() {
    val wallpapers = favoritesRepo.getWallpapers().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    val sounds = favoritesRepo.getSounds().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    fun removeFavorite(entity: FavoriteEntity) = viewModelScope.launch { favoritesRepo.remove(entity.favoriteIdentity()) }
    fun restoreFavorite(entity: FavoriteEntity) = viewModelScope.launch { favoritesRepo.add(entity) }

    /** Convert FavoriteEntity to domain Wallpaper and populate shared holder with the visible list */
    fun selectWallpaper(fav: FavoriteEntity, visibleWallpapers: List<FavoriteEntity>) {
        selectedContent.selectWallpaper(
            fav.toWallpaper(),
            visibleWallpapers.map { it.toWallpaper() },
        )
    }

    /** Convert FavoriteEntity to domain Sound and populate shared holder */
    fun selectSound(fav: FavoriteEntity) {
        selectedContent.selectSound(fav.toSound())
    }

    fun exportFavorites(uri: Uri) = viewModelScope.launch {
        exporter.export(uri)
            .onSuccess { count -> _message.update { _ -> "Exported $count favorites" } }
            .onFailure { e -> _message.update { _ -> "Export failed: ${e.message}" } }
    }

    fun importFavorites(uri: Uri) = viewModelScope.launch {
        exporter.import(uri)
            .onSuccess { count -> _message.update { _ -> "Imported $count favorites" } }
            .onFailure { e -> _message.update { _ -> "Import failed: ${e.message}" } }
    }

    val batchState = batchDownloadService.state

    fun downloadAllWallpapers() {
        val wps = wallpapers.value.map { it.toWallpaper() }
        if (wps.isEmpty()) return
        batchDownloadService.downloadBatch(wps)
        _message.update { _ -> "Downloading ${wps.size} wallpapers..." }
    }

    // -- Bulk actions (selection mode) ---------------------------------------

    /** Remove every favorite whose stableKey is in [keys]. Returns the count deleted. */
    fun bulkDelete(keys: Set<String>) = viewModelScope.launch {
        if (keys.isEmpty()) return@launch
        val w = wallpapers.value.filter { it.stableKey() in keys }
        val s = sounds.value.filter { it.stableKey() in keys }
        (w + s).forEach { favoritesRepo.remove(it.favoriteIdentity()) }
        val total = w.size + s.size
        if (total > 0) _message.update { _ -> "Removed $total favorite${if (total == 1) "" else "s"}" }
    }

    /** Kick off a batch download for every selected wallpaper favorite. Sounds are ignored. */
    fun bulkDownload(keys: Set<String>) {
        if (keys.isEmpty()) return
        val wps = wallpapers.value.filter { it.stableKey() in keys }.map { it.toWallpaper() }
        if (wps.isEmpty()) {
            _message.update { _ -> "Select at least one wallpaper" }
            return
        }
        batchDownloadService.downloadBatch(wps)
        _message.update { _ -> "Downloading ${wps.size} wallpaper${if (wps.size == 1) "" else "s"}..." }
    }

    fun clearMessage() { _message.update { _ -> null } }
}
