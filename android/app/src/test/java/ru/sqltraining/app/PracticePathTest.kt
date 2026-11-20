package ru.sqltraining.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PracticePathTest {
    private fun task(id: Int, lesson: Int, difficulty: Int = 1) = PracticeTask(
        id = id,
        title = "Task $id",
        description = "Test",
        starter = "",
        expectedSql = "SELECT 1",
        hint = "Hint",
        topic = "Topic",
        difficulty = difficulty,
        lessonId = lesson,
        hints = listOf("One", "Two", "Three"),
        explanation = "Explanation"
    )

    @Test fun ordersByPrerequisiteLessonRatherThanHistoricalId() {
        val items = listOf(task(1, 3), task(2, 11), task(3, 2), task(10, 0), task(11, 1))
        assertEquals(listOf(10, 11, 3, 1, 2), PracticePath.ordered(items).map { it.id })
    }

    @Test fun ordersTasksWithinLessonByDifficultyThenStableId() {
        val items = listOf(task(90, 2, 3), task(42, 2, 1), task(41, 2, 1), task(50, 2, 2))
        assertEquals(listOf(41, 42, 50, 90), PracticePath.forLesson(items, 2).map { it.id })
    }

    @Test fun previousAndNextWorkRegardlessOfCompletion() {
        val items = listOf(task(11, 1), task(10, 0), task(3, 2))
        assertNull(PracticePath.previous(items, 10))
        assertEquals(11, PracticePath.next(items, 10)?.id)
        assertEquals(10, PracticePath.previous(items, 11)?.id)
        assertEquals(3, PracticePath.next(items, 11)?.id)
        assertNull(PracticePath.next(items, 3))
    }

    @Test fun resumesFromFirstIncompleteTaskInCurricularOrder() {
        val items = listOf(task(3, 2), task(10, 0), task(11, 1))
        assertEquals(11, PracticePath.nextUnsolved(items, setOf(10))?.id)
        assertNull(PracticePath.nextUnsolved(items, setOf(10, 11, 3)))
    }
}
