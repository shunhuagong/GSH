package com.gsh.app.data

import android.content.Context

/**
 * 搜索历史：最近 10 条，去重且新词置顶。用 SharedPreferences 持久化。
 */
class SearchHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("gsh_search_history", Context.MODE_PRIVATE)

    fun get(): List<String> =
        prefs.getString(KEY_TERMS, null)
            ?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    fun add(term: String) {
        val t = term.trim()
        if (t.isEmpty()) return
        val updated = (get() - t).toMutableList()
        updated.add(0, t)
        prefs.edit()
            .putString(KEY_TERMS, updated.take(MAX).joinToString(SEPARATOR))
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_TERMS).apply()
    }

    private companion object {
        const val KEY_TERMS = "terms"
        const val SEPARATOR = "\n"
        const val MAX = 10
    }
}
