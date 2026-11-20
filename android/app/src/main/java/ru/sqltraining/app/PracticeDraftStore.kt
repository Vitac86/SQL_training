package ru.sqltraining.app

import android.content.SharedPreferences
import org.json.JSONObject

/**
 * Separate per-exercise drafts. Saved by stable task ID, so changing tasks does
 * not delete a partially written query or affect the checked/unchecked state.
 */
object PracticeDraftStore {
    private const val KEY = "practice_drafts"

    fun load(prefs: SharedPreferences): Map<Int, String> = runCatching {
        val json = JSONObject(prefs.getString(KEY, "{}"))
        val drafts = mutableMapOf<Int, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val id = keys.next().toIntOrNull() ?: continue
            drafts[id] = json.optString(id.toString(), "")
        }
        drafts
    }.getOrDefault(emptyMap())

    fun save(prefs: SharedPreferences, drafts: Map<Int, String>) {
        val json = JSONObject()
        drafts.forEach { (id, sql) -> json.put(id.toString(), sql) }
        prefs.edit().putString(KEY, json.toString()).apply()
    }
}
