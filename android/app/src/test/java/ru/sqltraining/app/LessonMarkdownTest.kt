package ru.sqltraining.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonMarkdownTest {
    @Test fun separatesDesktopSqlFromProse() {
        val input = "Текст до примера.\n\n```sql\nSELECT TOP (3) *\nFROM dbo.trades;\n```\n\nТекст после."
        val blocks = LessonMarkdown.parse(input)
        assertEquals(listOf("paragraph", "code", "paragraph"), blocks.map { it.kind })
        assertEquals("sql", blocks[1].language)
        assertTrue(blocks[1].text.contains("TOP (3)"))
    }

    @Test fun preservesListsHeadingsAndCode() {
        val input = "## Объяснение\n- Первое\n- Второе\n\n```text\n1 | SBER\n2 | GAZP\n```"
        val blocks = LessonMarkdown.parse(input)
        assertEquals(listOf("heading", "list", "list", "code"), blocks.map { it.kind })
        assertEquals("text", blocks.last().language)
        assertEquals("1 | SBER\n2 | GAZP", blocks.last().text)
    }

    @Test fun keepsParagraphLinesTogether() {
        val blocks = LessonMarkdown.parse("Первая строка\nвторая строка\n\nСледующий абзац")
        assertEquals(2, blocks.size)
        assertEquals("Первая строка вторая строка", blocks.first().text)
    }
}
