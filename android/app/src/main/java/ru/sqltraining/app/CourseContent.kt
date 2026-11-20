package ru.sqltraining.app

import android.content.Context
import org.json.JSONObject

/** Desktop-source excerpts, not rewritten to pretend they are SQLite code. */
data class DesktopSection(
    val title: String,
    val source: String,
    val markdown: String
) {
    val fromSqlServerCourse: Boolean get() = source.startsWith("Урок ")
}

data class DetailedLesson(
    val id: Int,
    val sourceFiles: List<String>,
    val sections: List<DesktopSection>
)

data class LessonSyntax(
    val id: Int,
    val kind: String,
    val kindLabel: String,
    val summary: String,
    val warning: String,
    val serverSql: String,
    val androidSql: String
)

data class CourseContent(
    val detail: Map<Int, DetailedLesson>,
    val syntax: Map<Int, LessonSyntax>,
    val globalNotice: String
)

object CourseContentStore {
    fun load(context: Context): CourseContent {
        fun read(name: String): JSONObject =
            context.assets.open(name).bufferedReader().use { JSONObject(it.readText()) }

        val readings = read("lesson_readings.json")
        val differences = read("lesson_dialects.json")
        require(readings.getInt("version") == 1)
        require(differences.getInt("version") == 1)

        val readingJson = readings.getJSONArray("lessons")
        val lessons = (0 until readingJson.length()).map { index ->
            val node = readingJson.getJSONObject(index)
            val source = node.getJSONArray("origin")
            val sections = node.getJSONArray("sections")
            val chapter = DetailedLesson(
                id = node.getInt("id"),
                sourceFiles = (0 until source.length()).map(source::getString),
                sections = (0 until sections.length()).map { n ->
                    val row = sections.getJSONObject(n)
                    DesktopSection(
                        title = row.getString("title"),
                        source = row.getString("source"),
                        markdown = row.getString("markdown")
                    )
                }
            )
            require(chapter.sections.isNotEmpty()) { "Empty lesson ${chapter.id}" }
            chapter
        }
        val dialectJson = differences.getJSONArray("lessons")
        val dialects = (0 until dialectJson.length()).map { index ->
            val node = dialectJson.getJSONObject(index)
            LessonSyntax(
                id = node.getInt("id"),
                kind = node.getString("kind"),
                kindLabel = node.getString("kindLabel"),
                summary = node.getString("summary"),
                warning = node.getString("warning"),
                serverSql = node.getString("serverSql"),
                androidSql = node.getString("androidSql")
            )
        }
        require(lessons.size == 32 && dialects.size == 32) {
            "Every mobile lesson must have a detailed explanation and a dialect note"
        }
        require(lessons.map { it.id }.toSet() == (0..31).toSet())
        require(dialects.map { it.id }.toSet() == (0..31).toSet())
        return CourseContent(
            detail = lessons.associateBy(DetailedLesson::id),
            syntax = dialects.associateBy(LessonSyntax::id),
            globalNotice = differences.getString("globalNotice")
        )
    }
}
