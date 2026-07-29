package com.search.browser

import android.content.Context

/**
 * App settings backed by SharedPreferences.
 * Central place for the default search engine, theme choice, etc.
 */
object Settings {

    private const val PREFS = "search_settings"
    private const val KEY_ENGINE = "search_engine"
    private const val KEY_THEME = "theme_mode"

    // Search engines: label -> query URL prefix
    val ENGINES = linkedMapOf(
        "Google" to "https://www.google.com/search?q=",
        "DuckDuckGo" to "https://duckduckgo.com/?q=",
        "Bing" to "https://www.bing.com/search?q="
    )

    // Theme modes
    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    private fun prefs(c: Context) =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getEngineName(c: Context): String =
        prefs(c).getString(KEY_ENGINE, "Google") ?: "Google"

    fun getEngineUrl(c: Context): String =
        ENGINES[getEngineName(c)] ?: ENGINES["Google"]!!

    fun setEngine(c: Context, name: String) {
        prefs(c).edit().putString(KEY_ENGINE, name).apply()
    }

    fun getTheme(c: Context): Int =
        prefs(c).getInt(KEY_THEME, THEME_SYSTEM)

    fun setTheme(c: Context, mode: Int) {
        prefs(c).edit().putInt(KEY_THEME, mode).apply()
    }
}
