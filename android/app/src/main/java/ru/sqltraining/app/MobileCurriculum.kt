package ru.sqltraining.app

/**
 * The Android course focuses on SQL querying and relational foundations.
 * SQL Server administration, DDL tuning, transactions and broker back-office
 * projects remain in the desktop course. Old task IDs remain stable in JSON.
 */
object MobileCurriculum {
    const val FIRST_LESSON = 0
    const val LAST_LESSON = 20

    fun courseLessons(): List<Lesson> =
        lessons.filter { it.id in FIRST_LESSON..LAST_LESSON }

    fun tasks(all: List<PracticeTask>): List<PracticeTask> =
        all.filter { it.lessonId in FIRST_LESSON..LAST_LESSON }

    /** Mastery comes exclusively from successful assessment of every task. */
    fun masteredLessons(tasks: List<PracticeTask>, completedTaskIds: Set<Int>): Set<Int> =
        courseLessons().filter { lesson ->
            val assigned = tasks.filter { it.lessonId == lesson.id }
            assigned.isNotEmpty() && assigned.all { it.id in completedTaskIds }
        }.mapTo(mutableSetOf()) { it.id }

    fun firstUnfinishedLesson(tasks: List<PracticeTask>, completedTaskIds: Set<Int>): Lesson? {
        val mastered = masteredLessons(tasks, completedTaskIds)
        return courseLessons().firstOrNull { it.id !in mastered }
    }

    fun nextUnfinishedLesson(
        afterId: Int,
        tasks: List<PracticeTask>,
        completedTaskIds: Set<Int>
    ): Lesson? {
        val mastered = masteredLessons(tasks, completedTaskIds)
        return courseLessons().firstOrNull { it.id > afterId && it.id !in mastered }
    }

    fun nextUnsolvedTaskOfLesson(
        lessonId: Int,
        tasks: List<PracticeTask>,
        completedTaskIds: Set<Int>
    ): PracticeTask? =
        PracticePath.forLesson(tasks, lessonId).firstOrNull { it.id !in completedTaskIds }
}
