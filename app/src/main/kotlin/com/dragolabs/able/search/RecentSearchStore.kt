package com.dragolabs.able.search

import android.content.Context
import androidx.core.content.edit

/**
 * Recent options-search queries, stored only on this device in a feature-local
 * SharedPreferences file (not backed up into `.ablebackup`, never sent anywhere).
 */
class RecentSearchStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    /** Adds [query] at the top, de-duplicated case/diacritic-insensitively; returns the new list. */
    fun add(query: String): List<String> {
        val updated = RecentSearches.add(load(), query)
        prefs.edit { putString(KEY, updated.joinToString(SEPARATOR.toString())) }
        return updated
    }

    fun clear() {
        prefs.edit { remove(KEY) }
    }

    private companion object {
        const val FILE = "able_global_search"
        const val KEY = "recent_queries"
        const val SEPARATOR = '\n'
    }
}

/** Pure list logic for recent searches (unit-tested). */
object RecentSearches {
    const val MAX = 8

    fun add(current: List<String>, query: String, max: Int = MAX): List<String> {
        val clean = query.replace('\n', ' ').trim().replace(Regex("\\s+"), " ")
        val key = SearchMatcher.normalizeQuery(clean)
        if (key.isEmpty()) return current
        return (listOf(clean) + current.filter { SearchMatcher.normalizeQuery(it) != key }).take(max)
    }
}
