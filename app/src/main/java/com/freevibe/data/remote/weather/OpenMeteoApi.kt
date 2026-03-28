package com.freevibe.data.remote.weather

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Open-Meteo API — free weather data, no key, no rate limit.
 * Used for weather effects overlay on wallpapers (rain, snow, fog, sun).
 * Weathercodes: 0=clear, 1-3=cloudy, 45-48=fog, 51-67=rain, 71-77=snow,
 *               80-82=showers, 95-99=thunderstorm
 */
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current_weather") currentWeather: Boolean = true,
    ): OpenMeteoResponse

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"
    }
}

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    @Json(name = "current_weather") val currentWeather: CurrentWeather? = null,
)

@JsonClass(generateAdapter = true)
data class CurrentWeather(
    @Json(name = "temperature") val temperature: Double = 0.0,
    @Json(name = "windspeed") val windSpeed: Double = 0.0,
    @Json(name = "winddirection") val windDirection: Double = 0.0,
    @Json(name = "weathercode") val weatherCode: Int = 0,
    @Json(name = "is_day") val isDay: Int = 1,
    @Json(name = "time") val time: String = "",
) {
    /** Map weathercode to effect type for wallpaper overlay */
    val weatherEffect: WeatherEffect get() = when (weatherCode) {
        0 -> if (isDay == 1) WeatherEffect.CLEAR_DAY else WeatherEffect.CLEAR_NIGHT
        1, 2, 3 -> WeatherEffect.CLOUDY
        45, 48 -> WeatherEffect.FOG
        in 51..67 -> WeatherEffect.RAIN
        in 71..77 -> WeatherEffect.SNOW
        in 80..82 -> WeatherEffect.RAIN
        in 95..99 -> WeatherEffect.THUNDERSTORM
        else -> WeatherEffect.CLEAR_DAY
    }
}

enum class WeatherEffect {
    CLEAR_DAY,
    CLEAR_NIGHT,
    CLOUDY,
    FOG,
    RAIN,
    SNOW,
    THUNDERSTORM,
}
