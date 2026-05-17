package com.freevibe.service

import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * NX-11: opt into Android 17's `PhotoPickerUiCustomizationParams` 9:16 portrait
 * grid for the system photo picker, without requiring `compileSdk 37` at build
 * time. Reflection is bounded to a single call site (`apply9x16AspectRatio`),
 * fails silently on older devices, and never throws — caller flows unchanged
 * when the API isn't available.
 *
 * On a build configured at `compileSdk >= 37` the reflection block is a
 * straight-line API call; until N-1 lands the toolchain bump, this approach
 * lets Aura ship the runtime behaviour on Android 17 devices today.
 *
 * The official 9:16 thumbnail API is the canonical wallpaper-picker UX
 * documented in
 * https://android-developers.googleblog.com/2026/03/the-third-beta-of-android-17.html
 */
object PhotoPickerCustomization {

    /**
     * Mutates [intent] to attach a 9:16 [PhotoPickerUiCustomizationParams]
     * extra when running on Android 17+. No-op on older devices.
     *
     * Expected wallpaper-app usage: pass the intent that `PickVisualMedia`
     * builds (via `ActivityResultContracts.PickVisualMedia` + its
     * `createIntent` path) before launching, OR call this on the
     * `ACTION_PICK_IMAGES` intent the photo-picker launcher dispatches.
     */
    fun apply9x16AspectRatio(intent: Intent) {
        if (Build.VERSION.SDK_INT < 37) return
        runCatching {
            // PhotoPickerUiCustomizationParams.Builder().setGridAspectRatio(9, 16).build()
            val builderCls = Class.forName(
                "android.provider.MediaStore\$PhotoPickerUiCustomizationParams\$Builder",
            )
            val builder = builderCls.getDeclaredConstructor().newInstance()
            val setAspect = builderCls.getMethod(
                "setGridAspectRatio",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            setAspect.invoke(builder, 9, 16)
            val build = builderCls.getMethod("build")
            val params = build.invoke(builder)

            // Intent.putExtra(EXTRA_PHOTO_PICKER_UI_CUSTOMIZATION_PARAMS, params)
            // The extra key constant landed in android.provider.MediaStore. Read it
            // by name rather than the raw string so a future rename surfaces a
            // failed reflection (logged DEBUG) instead of a silent no-op against
            // a now-defunct extra.
            val mediaStoreCls = Class.forName("android.provider.MediaStore")
            val extraKeyField = mediaStoreCls.getField("EXTRA_PHOTO_PICKER_UI_CUSTOMIZATION_PARAMS")
            val extraKey = extraKeyField.get(null) as? String
            if (extraKey != null) {
                @Suppress("UNCHECKED_CAST")
                val putExtra = Intent::class.java.getMethod(
                    "putExtra",
                    String::class.java,
                    android.os.Parcelable::class.java,
                )
                putExtra.invoke(intent, extraKey, params)
            }
        }.onFailure { e ->
            // Reflection failure is acceptable — the picker just renders at its
            // default 1:1 grid. Only log on debug builds; no user-visible toast.
            Log.d("PhotoPickerCustomization", "apply9x16 reflection skipped: ${e.message}")
        }
    }
}
