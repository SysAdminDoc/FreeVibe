package com.freevibe.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.freevibe.data.remote.weather.OpenMeteoApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that fetches current weather from Open-Meteo
 * and stores the weather effect + wind speed in SharedPreferences
 * for WeatherWallpaperService to read.
 */
@HiltWorker
class WeatherUpdateWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val openMeteoApi: OpenMeteoApi,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val location = getLastKnownLocation() ?: return Result.success() // No location = skip

            val response = openMeteoApi.getCurrentWeather(
                latitude = location.first,
                longitude = location.second,
            )

            val weather = response.currentWeather ?: return Result.success()

            // Store weather data for WeatherWallpaperService
            applicationContext.getSharedPreferences("freevibe_weather_wp", Context.MODE_PRIVATE)
                .edit()
                .putString("weather_effect", weather.weatherEffect.name)
                .putFloat("wind_speed", weather.windSpeed.toFloat())
                .putFloat("temperature", weather.temperature.toFloat())
                .putInt("is_day", weather.isDay)
                .apply()

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun getLastKnownLocation(): Pair<Double, Double>? {
        val hasFine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val lm = applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val location = try {
            @Suppress("DEPRECATION")
            if (hasFine) {
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else {
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
        } catch (_: Exception) { null }

        return location?.let { it.latitude to it.longitude }
    }

    companion object {
        const val WORK_NAME = "weather_update"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(
                30, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
