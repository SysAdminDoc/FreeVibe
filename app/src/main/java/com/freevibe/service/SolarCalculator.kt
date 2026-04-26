package com.freevibe.service

import kotlin.math.*

/**
 * Pure-JVM sunrise/sunset times using NOAA simplified solar equations.
 * No network, no GPS permission required at call-site — caller supplies lat/lon from stored prefs.
 *
 * Reference: https://gml.noaa.gov/grad/solcalc/solareqns.PDF
 */
object SolarCalculator {

    data class SunTimes(
        /** Local decimal hours, e.g. 6.5 = 6:30 AM. */
        val sunriseHour: Double,
        val sunsetHour: Double,
    )

    /**
     * Calculate local sunrise and sunset times.
     *
     * @param lat  Latitude in degrees (-90..90, positive = North).
     * @param lon  Longitude in degrees (-180..180, positive = East).
     * @param dayOfYear  Day-of-year (1..366); defaults to today.
     * @param utcOffsetHours  Local UTC offset in decimal hours; defaults to the device time zone.
     */
    fun sunTimes(
        lat: Double,
        lon: Double,
        dayOfYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR),
        utcOffsetHours: Double = java.util.TimeZone.getDefault().rawOffset / 3_600_000.0,
    ): SunTimes {
        // Fractional year in radians
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1)

        // Equation of time (minutes)
        val eqtime = 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) - 0.04089 * sin(2 * gamma)
        )

        // Solar declination (radians)
        val decl = 0.006918 -
            0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)

        val latRad = Math.toRadians(lat)

        // Hour angle at sunrise/sunset (accounts for atmospheric refraction at 90.833°)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(latRad) * cos(decl)) - tan(latRad) * tan(decl)
        // Clamp to valid range: |cosHa| > 1 means polar day or polar night
        val haMinutes = Math.toDegrees(acos(cosHa.coerceIn(-1.0, 1.0))) * 4.0

        // Solar noon in local minutes
        val solarNoonMinutes = 720.0 - 4.0 * lon - eqtime + utcOffsetHours * 60.0

        return SunTimes(
            sunriseHour = (solarNoonMinutes - haMinutes) / 60.0,
            sunsetHour = (solarNoonMinutes + haMinutes) / 60.0,
        )
    }

    /**
     * Returns [r, g, b] scale offsets for a ColorMatrix based on the current time of day.
     * Values are additive deltas in 0-255 space, scaled by [intensity].
     *
     * Phase zones:
     *   Deep night  (2h after sunset → 1.5h before sunrise): cool blue
     *   Pre-dawn    (1.5h before sunrise → 0.5h before): soft gold
     *   Sunrise     (0.5h before → 1h after): warm amber
     *   Morning     (1h after sunrise → noon-1h): gentle warm
     *   Midday      (noon ± 1h): neutral
     *   Afternoon   (noon+1 → golden hour): slight warm
     *   Golden hour (1.5h before sunset): rich amber
     *   Sunset      (0.5h after): rose/dusk
     *   Evening     (0.5h–2h after sunset): soft indigo
     */
    fun tintOffsets(
        currentHour: Double,
        sunTimes: SunTimes,
        intensity: Float = 1f,
    ): FloatArray {
        val rise = sunTimes.sunriseHour
        val set = sunTimes.sunsetHour
        val noon = (rise + set) / 2.0

        // Each triple is (dR, dG, dB) additive in 0-255 space
        val (dR, dG, dB) = when {
            currentHour < rise - 1.5 || currentHour >= set + 2.0 ->
                Triple(0f, 0f, 18f)          // deep night: blue
            currentHour < rise - 0.5 ->
                Triple(14f, 7f, -10f)        // pre-dawn: soft gold
            currentHour < rise + 1.0 ->
                Triple(22f, 12f, -14f)       // sunrise: warm amber
            currentHour < noon - 1.0 ->
                Triple(8f, 4f, -4f)          // morning: gentle warm
            currentHour < noon + 1.0 ->
                Triple(0f, 0f, 0f)           // midday: neutral
            currentHour < set - 1.5 ->
                Triple(6f, 3f, -5f)          // afternoon: slight warm
            currentHour < set ->
                Triple(26f, 14f, -18f)       // golden hour: rich amber
            currentHour < set + 0.5 ->
                Triple(14f, 4f, 8f)          // sunset/dusk: rose
            currentHour < set + 2.0 ->
                Triple(6f, 0f, 14f)          // evening: soft indigo
            else ->
                Triple(0f, 0f, 18f)          // deep night fallback
        }

        val s = intensity
        return floatArrayOf(dR * s, dG * s, dB * s)
    }

    /** Convenience: decimal hour for "right now" using the device clock. */
    fun currentHour(): Double {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60.0
    }
}
