package ru.sqltraining.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ReadingBlock(val kind: String, val text: String, val language: String = "")

/** Offline Markdown reader for the source materials from desktop lessons. */
object LessonMarkdown {
    fun parse(markdown: String): List<ReadingBlock> {
        val blocks = mutableListOf<ReadingBlock>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var inCode = false
        var codeLanguage = ""

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += ReadingBlock("paragraph", paragraph.joinToString(" ").trim())
                paragraph.clear()
            }
        }

        for (line in markdown.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                flushParagraph()
                if (inCode) {
                    blocks += ReadingBlock("code", code.joinToString("\n").trimEnd(), codeLanguage)
                    code.clear()
                    inCode = false
                    codeLanguage = ""
                } else {
                    inCode = true
                    codeLanguage = trimmed.removePrefix("```").trim().lowercase()
                }
                continue
            }
            if (inCode) {
                code += line
                continue
            }
            if (trimmed.isEmpty()) {
                flushParagraph()
                continue
            }
            val kind = when {
                trimmed.startsWith("### ") || trimmed.startsWith("## ") -> "heading"
                trimmed.startsWith("> ") -> "quote"
                trimmed.startsWith("- ") || trimmed.startsWith("* ") ||
                    Regex("""^\d+[.)]\s""").containsMatchIn(trimmed) -> "list"
                trimmed.startsWith("|") -> "table"
                else -> ""
            }
            if (kind.isNotBlank()) {
                flushParagraph()
                blocks += ReadingBlock(kind, trimmed)
            } else {
                paragraph += trimmed
            }
        }
        flushParagraph()
        if (inCode && code.isNotEmpty()) {
            blocks += ReadingBlock("code", code.joinToString("\n"), codeLanguage)
        }
        return blocks
    }
}

private fun inlineMarkdown(value: String): AnnotatedString {
    val regex = Regex("""\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\([^)]+\)""")
    return buildAnnotatedString {
        var cursor = 0
        for (match in regex.findAll(value)) {
            append(value.substring(cursor, match.range.first))
            val bold = match.groups[1]?.value
            val code = match.groups[2]?.value
            val link = match.groups[3]?.value
            when {
                bold != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
                code != null -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, color = Color(0xFF65D6AD))
                ) { append(code) }
                link != null -> append(link)
            }
            cursor = match.range.last + 1
        }
        append(value.substring(cursor))
    }
}

@Composable
fun LessonReadingSections(detail: DetailedLesson) {
    val clipboard = LocalClipboardManager.current
    detail.sections.forEachIndexed { index, section ->
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${index + 1}. ${section.title}",
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFEAF0F7)
            )
            Text(
                section.source +
                    if (section.fromSqlServerCourse) " · оригинал для SQL Server/SSMS"
                    else " · пример для мобильной SQLite",
                fontSize = 11.sp,
                color = Color(0xFF9BA9BC)
            )
            val blocks = remember(section.markdown) { LessonMarkdown.parse(section.markdown) }
            blocks.forEach { block ->
                when (block.kind) {
                    "code" -> {
                        val isSql = block.language == "sql" || block.language == "tsql" || block.language == "t-sql"
                        val caption = if (isSql && section.fromSqlServerCourse)
                            "T-SQL · SSMS. Не копируй в Android без проверки отличий."
                        else if (isSql) "SQLite · учебный пример для телефона"
                        else "Схема или пример данных"
                        Surface(
                            color = Color(0xFF1B2432),
                            shape = RoundedCornerShape(11.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row {
                                    Text(
                                        caption,
                                        modifier = Modifier.weight(1f),
                                        color = if (isSql && section.fromSqlServerCourse) Color(0xFFFFD28A)
                                        else Color(0xFF65D6AD),
                                        fontSize = 10.sp
                                    )
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString(block.text)) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, "Скопировать пример", Modifier.size(15.dp))
                                    }
                                }
                                Text(
                                    block.text,
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = Color(0xFFEAF0F7)
                                )
                            }
                        }
                    }
                    "heading" -> Text(
                        inlineMarkdown(block.text.removePrefix("### ").removePrefix("## ")),
                        style = MaterialTheme.typography.titleSmall
                    )
                    "list" -> Text(
                        inlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    "quote" -> Text(
                        inlineMarkdown(block.text.removePrefix("> ")),
                        fontSize = 13.sp,
                        color = Color(0xFF9BA9BC)
                    )
                    "table" -> Text(
                        block.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                    else -> Text(
                        inlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = Color(0xFF273244)
        )
    }
}
