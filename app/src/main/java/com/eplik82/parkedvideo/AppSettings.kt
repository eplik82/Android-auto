package com.eplik82.parkedvideo

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

enum class ThemeMode { AUTO, DAY, NIGHT }

/** Telefonis tehtud seaded, mida auto ekraan kasutab. */
class AppSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("video", Context.MODE_PRIVATE)

    var videoId: String?
        get() = prefs.getString(KEY_VIDEO_ID, null)
        set(value) = prefs.edit().putString(KEY_VIDEO_ID, value).apply()

    var homeUrl: String
        get() = prefs.getString(KEY_HOME_URL, null) ?: SettingsInput.DEFAULT_HOME_URL
        set(value) = prefs.edit().putString(KEY_HOME_URL, value).apply()

    var stationarySeconds: Int
        get() = prefs.getInt(KEY_STATIONARY_SECONDS, SettingsInput.DEFAULT_STATIONARY_SECONDS)
            .coerceIn(SettingsInput.MIN_STATIONARY_SECONDS, SettingsInput.MAX_STATIONARY_SECONDS)
        set(value) = prefs.edit().putInt(KEY_STATIONARY_SECONDS, value).apply()

    var themeMode: ThemeMode
        get() = ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.AUTO
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var simulatedMotion: SimulatedMotion
        get() = SimulatedMotion.entries.firstOrNull { it.name == prefs.getString(KEY_SIMULATION, null) }
            ?: SimulatedMotion.OFF
        set(value) = prefs.edit().putString(KEY_SIMULATION, value.name).apply()

    /** Kas kasutada tumedat kujundust; AUTO puhul otsustab [autoDark] (auto või telefoni süsteem). */
    fun isDark(autoDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.AUTO -> autoDark
        ThemeMode.DAY -> false
        ThemeMode.NIGHT -> true
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        prefs.unregisterOnSharedPreferenceChangeListener(listener)

    companion object {
        private const val KEY_VIDEO_ID = "video_id"
        private const val KEY_HOME_URL = "home_url"
        private const val KEY_STATIONARY_SECONDS = "stationary_seconds"
        private const val KEY_THEME = "theme"
        private const val KEY_SIMULATION = "simulated_motion"

        fun isSystemDark(context: Context): Boolean =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    }
}
