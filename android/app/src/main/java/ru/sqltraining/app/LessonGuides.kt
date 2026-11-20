package ru.sqltraining.app

import android.content.Context
import org.json.JSONObject

data class LessonGuide(
    val id: Int,
    val what: String,
    val why: String,
    val steps: List<String>,
    val pitfall: String,
    val question: String,
    val answer: String
)

object LessonGuides {
    fun load(context: Context): Map<Int, LessonGuide> {
        val raw = context.assets.open("lesson_guides.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        require(root.getInt("version") == 1)
        val items = root.getJSONArray("guides")
        return (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            val steps = item.getJSONArray("steps")
            LessonGuide(
                id = item.getInt("id"),
                what = item.getString("what"),
                why = item.getString("why"),
                steps = (0 until steps.length()).map(steps::getString),
                pitfall = item.getString("pitfall"),
                question = item.getString("question"),
                answer = item.getString("answer")
            )
        }.associateBy(LessonGuide::id)
    }
}
