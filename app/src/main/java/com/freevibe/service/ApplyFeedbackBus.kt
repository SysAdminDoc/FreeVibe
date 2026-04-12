package com.freevibe.service

import com.freevibe.data.model.WallpaperHistoryEntity
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide feedback bus for "just applied X" events. The root Scaffold observes this
 * and shows a global Snackbar with an Undo action that restores the previous wallpaper.
 *
 * A shared flow (not StateFlow) so repeated applies each produce a distinct event even if
 * the text / undo target happen to match — keeps snackbar behavior predictable.
 */
@Singleton
class ApplyFeedbackBus @Inject constructor() {
    private val _events = MutableSharedFlow<ApplyFeedbackEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = _events.asSharedFlow()

    fun post(event: ApplyFeedbackEvent) {
        _events.tryEmit(event)
    }
}

/**
 * One user-visible feedback event after an apply (or an undo) completes.
 *
 * @param message what the snackbar should say (already localized / formatted)
 * @param undoTarget the wallpaper to restore if the user taps "Undo" — or null when
 *                   there is no previous history entry (first apply ever, or the user
 *                   just tapped Undo and we don't want to offer undo-of-undo).
 */
data class ApplyFeedbackEvent(
    val message: String,
    val undoTarget: WallpaperHistoryEntity? = null,
)
