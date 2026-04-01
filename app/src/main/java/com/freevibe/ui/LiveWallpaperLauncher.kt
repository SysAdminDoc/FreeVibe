package com.freevibe.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

internal enum class LiveWallpaperLaunchMode {
    DIRECT,
    CHOOSER,
}

internal fun launchLiveWallpaperPicker(
    context: Context,
    serviceComponent: ComponentName,
    tag: String,
): LiveWallpaperLaunchMode? {
    val directIntent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
        putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, serviceComponent)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.tryStartLiveWallpaperIntent(directIntent, "$tag:direct")) {
        return LiveWallpaperLaunchMode.DIRECT
    }

    val chooserIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (context.tryStartLiveWallpaperIntent(chooserIntent, "$tag:chooser")) {
        return LiveWallpaperLaunchMode.CHOOSER
    }

    return null
}

private fun Context.tryStartLiveWallpaperIntent(intent: Intent, tag: String): Boolean {
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        if (com.freevibe.BuildConfig.DEBUG) {
            Log.w("LiveWallpaper", "Activity missing for $tag", e)
        }
        false
    } catch (e: SecurityException) {
        if (com.freevibe.BuildConfig.DEBUG) {
            Log.w("LiveWallpaper", "Security error for $tag", e)
        }
        false
    } catch (e: Exception) {
        if (com.freevibe.BuildConfig.DEBUG) {
            Log.e("LiveWallpaper", "Unexpected failure for $tag", e)
        }
        false
    }
}
