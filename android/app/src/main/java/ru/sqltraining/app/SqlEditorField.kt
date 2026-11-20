package ru.sqltraining.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val syntaxKeyword = Color(0xFF9BBAFF)
private val syntaxString = Color(0xFFE5B978)
private val syntaxNumber = Color(0xFFA8DEAA)
private val syntaxFunction = Color(0xFFBFA4FF)
private val syntaxComment = Color(0xFF7C8C9F)
private val syntaxText = Color(0xFFE6EDF7)

private val keywords = setOf(
    "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "NULL", "IS", "IN", "BETWEEN",
    "LIKE", "DISTINCT", "TOP", "LIMIT", "ORDER", "BY", "ASC", "DESC",
    "GROUP", "HAVING", "JOIN", "INNER", "LEFT", "RIGHT", "OUTER", "ON", "AS",
    "UNION", "ALL", "CASE", "WHEN", "THEN", "ELSE", "END",
    "WITH", "OVER", "PARTITION", "ROWS", "CURRENT", "ROW", "PRECEDING",
    "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
    "CREATE", "TABLE", "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CHECK", "DEFAULT",
    "UNIQUE", "VIEW", "INDEX", "DROP", "ALTER", "EXISTS", "BEGIN", "COMMIT", "ROLLBACK"
)
private val functions = setOf(
    "COUNT", "SUM", "MIN", "MAX", "AVG", "ROUND", "COALESCE", "NULLIF",
    "IFNULL", "LAG", "LEAD", "ROW_NUMBER", "RANK", "DENSE_RANK",
    "LENGTH", "SUBSTR", "CAST", "DATE", "STRFTIME", "ABS", "TRIM", "UPPER", "LOWER"
)

/** Colors are applied without changing the text length, so selection offsets stay correct. */
class SqlSyntaxHighlight : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val sql = text.text
        val builder = AnnotatedString.Builder(text)
        var i = 0
        fun mark(start: Int, end: Int, color: Color) {
            if (end > start) builder.addStyle(SpanStyle(color = color), start, end)
        }
        while (i < sql.length) {
            val start = i
            when {
                sql[i] == '-' && sql.getOrNull(i + 1) == '-' -> {
                    i += 2
                    while (i < sql.length && sql[i] != '\n') i++
                    mark(start, i, syntaxComment)
                }
                sql[i] == '/' && sql.getOrNull(i + 1) == '*' -> {
                    i += 2
                    while (i < sql.length && !(sql[i] == '*' && sql.getOrNull(i + 1) == '/')) i++
                    i = (i + 2).coerceAtMost(sql.length)
                    mark(start, i, syntaxComment)
                }
                sql[i] == 39.toChar() -> {
                    i++
                    while (i < sql.length) {
                        if (sql[i] == 39.toChar()) {
                            i++
                            if (sql.getOrNull(i) == 39.toChar()) { i++; continue }
                            break
                        }
                        i++
                    }
                    mark(start, i, syntaxString)
                }
                sql[i] == '"' || sql[i] == '[' -> {
                    val endChar = if (sql[i] == '[') ']' else '"'
                    i++
                    while (i < sql.length && sql[i] != endChar) i++
                    if (i < sql.length) i++
                    mark(start, i, syntaxString)
                }
                sql[i].isDigit() -> {
                    i++
                    while (i < sql.length && (sql[i].isDigit() || sql[i] == '.')) i++
                    mark(start, i, syntaxNumber)
                }
                sql[i].isLetter() || sql[i] == '_' -> {
                    i++
                    while (i < sql.length && (sql[i].isLetterOrDigit() || sql[i] == '_')) i++
                    val word = sql.substring(start, i).uppercase()
                    when {
                        word in keywords -> mark(start, i, syntaxKeyword)
                        word in functions -> mark(start, i, syntaxFunction)
                    }
                }
                else -> i++
            }
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private val highlighter = SqlSyntaxHighlight()
private val quickWords = listOf(
    "SELECT", "FROM", "WHERE", "JOIN", "GROUP BY", "ORDER BY", "COUNT(*)", "LIMIT"
)

@Composable
fun SqlEditorField(
    sql: String,
    schema: List<DatabaseObject>,
    onSql: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var value by remember { mutableStateOf(TextFieldValue(sql, selection = TextRange(sql.length))) }
    LaunchedEffect(sql) {
        if (value.text != sql) value = TextFieldValue(sql, selection = TextRange(sql.length))
    }
    val scroll = rememberScrollState()
    val xScroll = rememberScrollState()
    val lines = remember(value.text) { (1..(value.text.count { it == '\n' } + 1)).joinToString("\n") }
    val cursor = value.selection.end.coerceIn(0, value.text.length)
    val before = value.text.substring(0, cursor)
    val start = before.indexOfLast { !it.isLetterOrDigit() && it != '_' } + 1
    val prefix = before.substring(start)
    val dotted = Regex("""([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)?$""").find(before)
    val qualifier = dotted?.groupValues?.getOrNull(1)
    val allWords = remember(schema) {
        (keywords.toList() + functions.toList() + schema.flatMap { listOf(it.name) + it.columns })
            .distinctBy { it.lowercase() }
    }
    val suggestions = remember(value.text, cursor, schema) {
        val values = if (qualifier != null) {
            val tableName = schema.find { it.name.equals(qualifier, ignoreCase = true) }?.name
                ?: Regex("""(?i)\b(?:FROM|JOIN)\s+([A-Za-z_]\w*)\s+(?:AS\s+)?([A-Za-z_]\w*)""")
                    .findAll(value.text)
                    .firstOrNull { it.groupValues[2].equals(qualifier, ignoreCase = true) }
                    ?.groupValues?.get(1)
            schema.find { it.name.equals(tableName, ignoreCase = true) }?.columns.orEmpty()
        } else allWords
        if (prefix.isBlank() && qualifier == null) quickWords
        else values.filter { it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = true) }.take(8)
    }

    fun insert(completion: String) {
        val updated = value.text.substring(0, start) + completion + value.text.substring(cursor)
        val next = TextFieldValue(updated, selection = TextRange(start + completion.length))
        value = next
        onSql(next.text)
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll).padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                lines,
                modifier = Modifier.width(34.dp),
                color = syntaxComment,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 20.sp
            )
            Box(Modifier.weight(1f).horizontalScroll(xScroll)) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it; onSql(it.text) },
                    modifier = Modifier.widthIn(min = 290.dp),
                    textStyle = TextStyle(
                        color = syntaxText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    ),
                    cursorBrush = SolidColor(Color(0xFF65D6AD)),
                    visualTransformation = highlighter,
                    decorationBox = { inner ->
                        Box {
                            if (value.text.isEmpty()) Text("Напиши SQL-запрос…", color = syntaxComment, fontSize = 13.sp)
                            inner()
                        }
                    }
                )
            }
        }
        HorizontalDivider(color = Color(0xFF334154))
        Row(
            Modifier.fillMaxWidth().height(45.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            suggestions.forEach { word ->
                AssistChip(
                    onClick = { insert(word) },
                    label = { Text(word, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }
    }
}
