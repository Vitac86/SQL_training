package ru.sqltraining.app

import android.content.Context
import org.json.JSONObject

/**
 * Stable task identifiers preserve completion progress across app updates.
 * Catalog is shipped as JSON in assets, so it remains available offline.
 */
data class PracticeTask(
    val id: Int,
    val title: String,
    val description: String,
    val starter: String,
    val expectedSql: String,
    val hint: String,
    val topic: String,
    val difficulty: Int,
    val lessonId: Int,
    val hints: List<String>,
    val explanation: String
) {
    val difficultyName: String get() = when (difficulty) {
        1 -> "Базовый"
        2 -> "Средний"
        else -> "Сложный"
    }
}

object PracticeCatalog {
    fun load(context: Context): List<PracticeTask> {
        val raw = context.assets.open("practice_tasks.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        require(root.getInt("version") == 1) { "Неизвестная версия каталога заданий" }
        val source = root.getJSONArray("tasks")
        val tasks = ArrayList<PracticeTask>(source.length())
        val ids = HashSet<Int>()
        for (i in 0 until source.length()) {
            val item = source.getJSONObject(i)
            val id = item.getInt("id")
            require(ids.add(id)) { "Повторяется номер задания #$id" }
            val hintSource = item.getJSONArray("hints")
            val hints = (0 until hintSource.length()).map(hintSource::getString)
            require(hints.size == 3) { "Для задания #$id нужны три подсказки" }
            val sql = item.getString("expectedSql")
            require(sql.isNotBlank()) { "У задания #$id нет эталонного SQL" }
            tasks += PracticeTask(
                id = id,
                title = item.getString("title"),
                description = item.getString("description"),
                starter = item.optString("starter"),
                expectedSql = sql,
                hint = hints.first(),
                topic = item.getString("topic"),
                difficulty = item.getInt("difficulty"),
                lessonId = item.getInt("lessonId"),
                hints = hints,
                explanation = item.getString("explanation")
            )
        }
        return tasks.sortedBy(PracticeTask::id)
    }
}


/**
 * Learning order is determined by the prerequisite lesson, not by legacy task ID.
 * Task IDs must remain stable because user completion data is stored by ID.
 */
object PracticePath {
    fun ordered(tasks: List<PracticeTask>): List<PracticeTask> =
        tasks.sortedWith(compareBy<PracticeTask> { it.lessonId }.thenBy { it.difficulty }.thenBy { it.id })

    fun forLesson(tasks: List<PracticeTask>, lessonId: Int): List<PracticeTask> =
        ordered(tasks).filter { it.lessonId == lessonId }

    fun position(tasks: List<PracticeTask>, taskId: Int): Int =
        ordered(tasks).indexOfFirst { it.id == taskId }

    fun next(tasks: List<PracticeTask>, taskId: Int): PracticeTask? =
        ordered(tasks).let { path ->
            val index = path.indexOfFirst { it.id == taskId }
            if (index in 0 until path.lastIndex) path[index + 1] else null
        }

    fun previous(tasks: List<PracticeTask>, taskId: Int): PracticeTask? =
        ordered(tasks).let { path ->
            val index = path.indexOfFirst { it.id == taskId }
            if (index > 0) path[index - 1] else null
        }

    fun nextUnsolved(tasks: List<PracticeTask>, completed: Set<Int>): PracticeTask? =
        ordered(tasks).firstOrNull { it.id !in completed }
}
