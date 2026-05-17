package com.freevibe.ui.screens.aigenerate

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
import kotlinx.coroutines.Job
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

    // Tracks the in-flight generation coroutine so back-navigation can cancel it
    // (NX-13). Cancelling a generation that has already hit Stability AI's
    // billing endpoint won't refund the credit, but it stops the spinner from
    // re-surfacing on resume and frees the OkHttp connection promptly.
    private var generationJob: Job? = null

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
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
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

    /**
     * Cancels any in-flight generation (NX-13). Invoked by the screen's
     * [androidx.activity.compose.BackHandler] when the user presses back
     * while a generation is still streaming.
     */
    fun cancelGeneration() {
        val job = generationJob ?: return
        if (job.isActive) {
            job.cancel()
            _state.update { it.copy(isGenerating = false, error = "Generation cancelled") }
        }
        generationJob = null
    }

    override fun onCleared() {
        generationJob?.cancel()
        generationJob = null
        super.onCleared()
    }

    fun applyWallpaper(target: WallpaperTarget = WallpaperTarget.BOTH) {
        val wallpaper = _state.value.result ?: return
        viewModelScope.launch {
            _state.update { it.copy(isApplying = true, error = null) }
            // Route through WallpaperApplier.applyByLocator so the disk read + decode + sampling
            // all happen on the IO dispatcher inside the applier (the prior version decoded the
            // full-resolution PNG on the Main coroutine context — a 3-4 MB PNG → ~10 MB bitmap
            // synchronously on the UI thread). applyByLocator handles file:// URIs natively.
            wallpaperApplier.applyByLocator(wallpaper.fullUrl, target)
                .onSuccess {
                    val label = when (target) {
                        WallpaperTarget.HOME -> "home screen"
                        WallpaperTarget.LOCK -> "lock screen"
                        WallpaperTarget.BOTH -> "home & lock screen"
                    }
                    _state.update { it.copy(isApplying = false, applySuccess = "Applied to $label") }
                }
                .onFailure { e ->
                    _state.update { it.copy(isApplying = false, error = "Apply failed: ${e.message ?: "unknown"}") }
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
