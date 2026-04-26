package com.freevibe.service

import java.time.LocalDate
import java.time.MonthDay
import javax.inject.Inject
import javax.inject.Singleton

/** Describes the current in-season theme. Null when no season is active. */
data class SeasonalTheme(
    /** Short display title shown on the collection card and banner, e.g. "Holiday Vibes". */
    val title: String,
    /** One-line subtitle describing the theme, e.g. "Festive picks for December". */
    val subtitle: String,
    /** Freesound / YouTube search query to surface seasonal sounds. */
    val soundQuery: String,
    /** Wallhaven / Pexels search query to surface seasonal wallpapers. */
    val wallpaperQuery: String,
    /** Short label used for accessibility and compact badges, e.g. "Holiday". */
    val label: String,
)

/**
 * Determines the active seasonal theme based on the current calendar date.
 *
 * Active windows:
 *  - Holiday (Christmas)    : December
 *  - Halloween              : Oct 15 – Oct 31
 *  - New Year               : Jan 1 – Jan 3
 *  - Valentine's Day        : Feb 10 – Feb 14
 *  - Summer                 : Jun 21 – Sep 1
 *
 * Returns null during off-season periods so callers can cleanly hide seasonal UI.
 */
@Singleton
class SeasonalContentManager @Inject constructor() {

    fun currentTheme(today: LocalDate = LocalDate.now()): SeasonalTheme? {
        val md = MonthDay.of(today.monthValue, today.dayOfMonth)
        return when {
            md >= HOLIDAY_START ->
                SeasonalTheme(
                    title = "Holiday Vibes",
                    subtitle = "Festive picks for December",
                    soundQuery = "christmas holiday bells jingle festive",
                    wallpaperQuery = "christmas snow winter holiday",
                    label = "Holiday",
                )
            md in HALLOWEEN_START..HALLOWEEN_END ->
                SeasonalTheme(
                    title = "Halloween Edition",
                    subtitle = "Spooky sounds and dark wallpapers",
                    soundQuery = "halloween spooky horror eerie ambient",
                    wallpaperQuery = "halloween pumpkin spooky dark night",
                    label = "Halloween",
                )
            md <= NEW_YEAR_END ->
                SeasonalTheme(
                    title = "New Year",
                    subtitle = "Countdown and celebration themes",
                    soundQuery = "new year celebration fireworks countdown",
                    wallpaperQuery = "new year fireworks city celebration",
                    label = "New Year",
                )
            md in VALENTINE_START..VALENTINE_END ->
                SeasonalTheme(
                    title = "Valentine's Day",
                    subtitle = "Love songs and romantic tones",
                    soundQuery = "romantic love piano soft gentle",
                    wallpaperQuery = "valentine heart love pink",
                    label = "Valentine",
                )
            md >= SUMMER_START && md <= SUMMER_END ->
                SeasonalTheme(
                    title = "Summer Vibes",
                    subtitle = "Beach, ocean and sunshine themes",
                    soundQuery = "summer beach ocean waves tropical",
                    wallpaperQuery = "summer beach ocean sunset",
                    label = "Summer",
                )
            else -> null
        }
    }

    companion object {
        private val HOLIDAY_START = MonthDay.of(12, 1)
        private val HALLOWEEN_START = MonthDay.of(10, 15)
        private val HALLOWEEN_END = MonthDay.of(10, 31)
        private val NEW_YEAR_END = MonthDay.of(1, 3)
        private val VALENTINE_START = MonthDay.of(2, 10)
        private val VALENTINE_END = MonthDay.of(2, 14)
        private val SUMMER_START = MonthDay.of(6, 21)
        private val SUMMER_END = MonthDay.of(9, 1)
    }
}
