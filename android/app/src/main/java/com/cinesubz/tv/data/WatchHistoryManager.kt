package com.cinesubz.tv.data

import android.content.Context
import com.cinesubz.tv.model.Movie
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object WatchHistoryManager {
    private const val PREFS_NAME = "nimsaratv_continue_watching"
    private const val KEY_ITEMS = "continue_watching_items"
    private const val MAX_ITEMS = 10

    private val gson = Gson()

    fun recordWatch(context: Context, movie: Movie) {
        if (movie.id.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentList = getContinueWatching(context).toMutableList()

        // Remove duplicate to move item to the front of continue watching
        currentList.removeAll { it.id == movie.id }

        // Add at the beginning (index 0)
        currentList.add(0, movie)

        // Keep at most 10 items
        val cappedList = if (currentList.size > MAX_ITEMS) {
            currentList.subList(0, MAX_ITEMS)
        } else {
            currentList
        }

        val json = gson.toJson(cappedList)
        prefs.edit().putString(KEY_ITEMS, json).apply()
    }

    fun getContinueWatching(context: Context): List<Movie> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<Movie>>() {}.type
            gson.fromJson<List<Movie>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearContinueWatching(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ITEMS).apply()
    }
}
