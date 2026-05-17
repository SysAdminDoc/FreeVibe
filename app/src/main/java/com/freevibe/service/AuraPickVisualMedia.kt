package com.freevibe.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

/**
 * NX-11: drop-in replacement for [ActivityResultContracts.PickVisualMedia] that
 * attaches the Android 17 `PhotoPickerUiCustomizationParams` 9:16 portrait
 * aspect ratio so wallpaper imports show portrait thumbnails instead of
 * default 1:1 squares.
 *
 * On Android 16 and below behaviour is identical to the parent contract.
 * Reflection in [PhotoPickerCustomization] handles the API-37-only API
 * surface without requiring `compileSdk = 37`.
 *
 * Wallpaper apps are the canonical use case Google called out for the
 * customisation API (Android 17 Beta 3 announcement).
 */
class AuraPickVisualMedia : ActivityResultContracts.PickVisualMedia() {

    override fun createIntent(context: Context, input: PickVisualMediaRequest): Intent {
        val intent = super.createIntent(context, input)
        PhotoPickerCustomization.apply9x16AspectRatio(intent)
        return intent
    }
}
