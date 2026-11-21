package ru.sqltraining.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileCurriculumTest {
    private fun task(id: Int, lessonId: Int) = PracticeTask(
        id, "Title", "Description", "SELECT ", "SELECT 1", "Hint",
        "Basics", 1, lessonId, listOf("A", "B", "C"), "Explanation"
    )

    @Test fun advancedDesktopTasksAreNotInMobilePath() {
        val tasks = listOf(task(1, 0), task(2, 20), task(3, 22), task(4, 31))
        assertEquals(listOf(1, 2), MobileCurriculum.tasks(tasks).map { it.id })
        assertEquals(21, MobileCurriculum.courseLessons().size)
    }

    @Test fun aLessonIsMasteredOnlyAfterEveryTaskPasses() {
        val tasks = listOf(task(100, 0), task(101, 0), task(102, 1))
        assertFalse(0 in MobileCurriculum.masteredLessons(tasks, setOf(100)))
        assertTrue(0 in MobileCurriculum.masteredLessons(tasks, setOf(100, 101)))
        assertFalse(1 in MobileCurriculum.masteredLessons(tasks, setOf(100, 101)))
    }

    @Test fun nextLessonAutomaticallySkipsMasteredTopics() {
        val tasks = listOf(task(100, 0), task(101, 1), task(102, 2))
        assertEquals(1, MobileCurriculum.firstUnfinishedLesson(tasks, setOf(100))?.id)
        assertEquals(2, MobileCurriculum.nextUnfinishedLesson(0, tasks, setOf(101))?.id)
        assertEquals(2, MobileCurriculum.nextUnfinishedLesson(1, tasks, setOf(100, 101))?.id)
    }

    @Test fun missingLessonTasksNeverCauseFalseMastery() {
        val tasks = listOf(task(100, 0))
        assertFalse(1 in MobileCurriculum.masteredLessons(tasks, setOf(100)))
        assertNull(MobileCurriculum.nextUnsolvedTaskOfLesson(0, tasks, setOf(100)))
    }
}
