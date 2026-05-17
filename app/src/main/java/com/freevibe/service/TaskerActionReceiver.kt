package com.freevibe.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Broadcast receiver exposing Aura's rotation actions to external automation
 * tools (Tasker, MacroDroid, adb shell, Termux scripts).
 *
 * ROADMAP L-2 minimum: just the actions that already exist as in-app behaviour.
 * Adding/firing custom Aura intents from Tasker is a one-line "Send Intent"
 * action — no Tasker plugin SDK integration needed.
 *
 * **Public contract** (treat as semver-stable):
 * - `com.freevibe.action.ROTATE_NOW` — enqueue a one-shot wallpaper rotation
 *   that respects the user's existing rotation source + target prefs.
 * - `com.freevibe.action.SHUFFLE_NOW` — alias for ROTATE_NOW (matches the
 *   home-screen widget label and the WallYou one-tap-shuffle idiom).
 *
 * Both are receiver-exported (`android:exported="true"`) so external apps can
 * send them. They do not require any Aura permission. The actual rotation work
 * runs inside `AutoWallpaperWorker`, which honours the rotation constraint
 * prefs (charging-only, Wi-Fi-only, idle-only) — so the rotation may be
 * deferred until those constraints are satisfied.
 *
 * Example Tasker action:
 *
 * ```
 * Send Intent → Action: com.freevibe.action.ROTATE_NOW
 *               Target: Broadcast Receiver
 *               Package: com.freevibe
 * ```
 */
class TaskerActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ROTATE_NOW, ACTION_SHUFFLE_NOW -> {
                RotationTriggerService.enqueueRotation(context)
            }
        }
    }

    companion object {
        const val ACTION_ROTATE_NOW = "com.freevibe.action.ROTATE_NOW"
        const val ACTION_SHUFFLE_NOW = "com.freevibe.action.SHUFFLE_NOW"
    }
}
