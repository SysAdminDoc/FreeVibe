package com.freevibe.data.remote.stability

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PartMap

// Stability AI REST API - image generation via stable-diffusion-core.
// Requires a Bearer key from platform.stability.ai. Response is raw image bytes.
interface StabilityAiApi {

    // Generate an image from a text prompt.
    // Required parts: prompt, aspect_ratio, output_format
    // Optional parts: style_preset, negative_prompt, cfg_scale, seed
    @Multipart
    @POST("v2beta/stable-image/generate/core")
    suspend fun generateImage(
        @Header("Authorization") authHeader: String,
        @Header("Accept") accept: String,
        @PartMap parts: Map<String, @JvmSuppressWildcards RequestBody>,
    ): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://api.stability.ai/"
    }
}
