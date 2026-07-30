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

    // ---- Generic boolean toggles (security, adblock, etc.) ----
    fun getBool(c: Context, key: String, default: Boolean): Boolean =
        prefs(c).getBoolean(key, default)

    fun setBool(c: Context, key: String, value: Boolean) {
        prefs(c).edit().putBoolean(key, value).apply()
    }

    // Security keys + defaults
    const val SEC_HTTPS_ONLY = "sec_https_only"       // default true
    const val SEC_SAFE_BROWSING = "sec_safe_browsing" // default true
    const val SEC_BLOCK_POPUPS = "sec_block_popups"   // default true
    const val SEC_BLOCK_3P_COOKIES = "sec_block_3p_cookies" // default false
    const val SEC_CONFIRM_DOWNLOADS = "sec_confirm_downloads" // default true

    // Adblock
    const val ADBLOCK_ENABLED = "adblock_enabled"     // default false

    // Desktop mode
    const val DESKTOP_MODE = "desktop_mode"           // default false

    // Customize your Search (home page)
    const val HOME_BACKGROUND = "home_background"      // "clean" | "gradient" | "dark"
    const val HOME_ACCENT = "home_accent"              // hex string
    const val HOME_SHOW_TILES = "home_show_tiles"      // default true

    fun getHomeBackground(c: Context): String =
        prefs(c).getString(HOME_BACKGROUND, "clean") ?: "clean"
    fun setHomeBackground(c: Context, v: String) { prefs(c).edit().putString(HOME_BACKGROUND, v).apply() }

    fun getHomeAccent(c: Context): String =
        prefs(c).getString(HOME_ACCENT, "#2B6CF0") ?: "#2B6CF0"
    fun setHomeAccent(c: Context, v: String) { prefs(c).edit().putString(HOME_ACCENT, v).apply() }

    // Accessibility
    const val A11Y_TEXT_SCALE = "a11y_text_scale"     // int percent, default 100
    fun getTextScale(c: Context): Int = prefs(c).getInt(A11Y_TEXT_SCALE, 100)
    fun setTextScale(c: Context, pct: Int) { prefs(c).edit().putInt(A11Y_TEXT_SCALE, pct).apply() }
}
