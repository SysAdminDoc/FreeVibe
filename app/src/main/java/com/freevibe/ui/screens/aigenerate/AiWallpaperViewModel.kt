package com.freevibe.ui.screens.aigenerate

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freevibe.data.model.ContentSource
import com.freevibe.data.model.FavoriteEntity
import com.freevibe.data.model.Wallpaper
import com.freevibe.data.model.WallpaperTarget
import com.freevibe.data.repository.AiStyle
import com.freevibe.data.repository.AiWallpaperRepository
import com.freevibe.data.repository.FavoritesRepository
import com.freevibe.data.local.PreferencesManager
import com.freevibe.service.WallpaperApplier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AiWallpaperUiState(
    val prompt: String = "",
    val selectedStyle: AiStyle = AiStyle.PHOTOGRAPHIC,
    val isGenerating: Boolean = false,
    val result: Wallpaper? = null,
    val isApplying: Boolean = false,
    val applySuccess: String? = null,
    val isSaved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AiWallpaperViewModel @Inject constructor(
    private val repo: AiWallpaperRepository,
    private val favoritesRepo: FavoritesRepository,
    private val wallpaperApplier: WallpaperApplier,
    private val prefs: PreferencesManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AiWallpaperUiState())
    val state: StateFlow<AiWallpaperUiState> = _state.asStateFlow()

    // API key is read from DataStore so changes in Settings propagate live.
    val stabilityAiKey: StateFlow<String> = prefs.stabilityAiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setPrompt(p: String) {
        _state.update { it.copy(prompt = p.take(500)) }
    }

    fun setStyle(s: AiStyle) {
        _state.update { it.copy(selectedStyle = s) }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { prefs.setStabilityKey(key) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSuccess() {
        _state.update { it.copy(applySuccess = null) }
    }

    fun generate(apiKey: String) {
        val current = _state.value
        if (current.prompt.isBlank()) {
            _state.update { it.copy(error = "Describe your wallpaper to get started.") }
            return
        }
        if (apiKey.isBlank()) {
            _state.update { it.copy(error = "Enter your Stability AI key to generate images.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isGenerating = true, error = null, result = null, isSaved = false) }
            repo.generate(
                prompt = current.prompt,
                style = current.selectedStyle,
                apiKey = apiKey,
            ).onSuccess { wallpaper ->
                _state.update { it.copy(isGenerating = false, result = wallpaper) }
            }.onFailure { e ->
                _state.update { it.copy(isGenerating = false, error = e.message ?: "Generation failed") }
            }
        }
    }

    fun applyWallpaper(target: WallpaperTarget = WallpaperTarget.BOTH) {
        val wallpaper = _state.value.result ?: return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, error = null) }
            val path = Uri.parse(wallpaper.fullUrl).path
            if (path == null) {
                _state.update { it.copy(isApplying = false, error = "Image path not found.") }
                return@launch
            }
            val bitmap = BitmapFactory.decodeFile(path)
            if (bitmap == null) {
                _state.update { it.copy(isApplying = false, error = "Failed to decode image.") }
                return@launch
            }
            wallpaperApplier.applyFromBitmap(bitmap, target)
                .onSuccess {
                    bitmap.recycle()
                    val label = when (target) {
                        WallpaperTarget.HOME -> "home screen"
                        WallpaperTarget.LOCK -> "lock screen"
                        WallpaperTarget.BOTH -> "home & lock screen"
                    }
                    _state.update { it.copy(isApplying = false, applySuccess = "Applied to $label") }
                }
                .onFailure { e ->
                    bitmap.recycle()
                    _state.update { it.copy(isApplying = false, error = "Apply failed: ${e.message}") }
                }
        }
    }

    fun saveToFavorites(prompt: String) {
        val wallpaper = _state.value.result ?: return
        viewModelScope.launch {
            favoritesRepo.add(
                FavoriteEntity(
                    id = wallpaper.id,
                    source = ContentSource.AI_GENERATED.name,
                    type = "WALLPAPER",
                    thumbnailUrl = wallpaper.thumbnailUrl,
                    fullUrl = wallpaper.fullUrl,
                    name = "AI: ${prompt.take(60).ifBlank { "Generated wallpaper" }}",
                    width = wallpaper.width,
                    height = wallpaper.height,
                    tags = wallpaper.tags.joinToString(","),
                    category = "AI Generated",
                    uploaderName = "AI",
                )
            )
            _state.update { it.copy(isSaved = true) }
        }
    }
}
