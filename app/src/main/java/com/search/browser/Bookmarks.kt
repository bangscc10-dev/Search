package com.search.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved bookmarks, backed by SharedPreferences (JSON).
 * De-duplicated by URL, newest first.
 */
object Bookmarks {

    private const val PREFS = "search_bookmarks"
    private const val KEY = "entries"

    data class Entry(val title: String, val url: String, val time: Long)

    fun add(context: Context, title: String, url: String) {
        if (url.isBlank() || url == "about:blank" ||
            url.startsWith("file:///android_asset/")
        ) return
        val list = load(context).toMutableList()
        list.removeAll { it.url == url }
        list.add(0, Entry(title.ifBlank { url }, url, System.currentTimeMillis()))
        save(context, list)
    }

    fun remove(context: Context, url: String) {
        val list = load(context).toMutableList()
        list.removeAll { it.url == url }
        save(context, list)
    }

    fun isBookmarked(context: Context, url: String): Boolean =
        load(context).any { it.url == url }

    fun load(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.getString("title"), o.getString("url"), o.getLong("time"))
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("title", e.title); put("url", e.url); put("time", e.time)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
