package ru.sqltraining.app

import android.content.SharedPreferences
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

data class SavedQuery(val name: String, val sql: String)

object SavedQueryStore {
    fun load(prefs: SharedPreferences): List<SavedQuery> = runCatching {
        val data = JSONArray(prefs.getString("saved_queries", "[]"))
        (0 until data.length()).map { index ->
            val item = data.getJSONObject(index)
            SavedQuery(item.getString("name"), item.getString("sql"))
        }
    }.getOrDefault(emptyList())

    fun save(prefs: SharedPreferences, queries: List<SavedQuery>) {
        val data = JSONArray()
        queries.forEach { data.put(JSONObject().put("name", it.name).put("sql", it.sql)) }
        prefs.edit().putString("saved_queries", data.toString()).apply()
    }
}

private val panel = Color(0xFF1B2432)
private val accent = Color(0xFF65D6AD)
private val muted = Color(0xFF9BA9BC)

@Composable
fun EditorScreen(
    sql: String,
    onSql: (String) -> Unit,
    result: QueryResult?,
    error: String?,
    onDismissError: () -> Unit,
    running: Boolean,
    history: List<String>,
    task: PracticeTask?,
    feedback: String?,
    onDismissFeedback: () -> Unit,
    schema: List<DatabaseObject>,
    savedQueries: List<SavedQuery>,
    onSave: (SavedQuery) -> Unit,
    onDeleteSaved: (String) -> Unit,
    onHistory: (String) -> Unit,
    onRun: () -> Unit,
    onCheck: () -> Unit,
    onNextTask: () -> Unit,
    previousTask: PracticeTask?,
    nextTask: PracticeTask?,
    taskNumber: Int,
    taskCount: Int,
    onNavigateTask: (PracticeTask) -> Unit,
    onTaskList: () -> Unit,
    onTaskLesson: () -> Unit
) {
    var showSave by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSchema by remember { mutableStateOf(false) }
    var saveName by remember { mutableStateOf("") }
    var hintStep by remember(task?.id) { mutableIntStateOf(0) }
    var showSolution by remember(task?.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var taskExpanded by remember(task?.id) { mutableStateOf(false) }
    var editorLarge by remember { mutableStateOf(false) }
    var resultExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(result) {
        if (result != null) resultExpanded = true
    }

    // The workspace scrolls as a whole. Fixed editor height is independent
    // of the length of the assignment, feedback and result.
    Column(
        Modifier.fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SQL Playground", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("SQLite · офлайн", color = accent, fontSize = 11.sp)
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = { showSchema = true }) {
                Icon(Icons.Default.AccountTree, null, Modifier.size(16.dp)); Text("Таблицы", fontSize = 12.sp)
            }
            TextButton(onClick = { saveName = ""; showSave = true }) {
                Icon(Icons.Default.BookmarkAdd, null, Modifier.size(16.dp)); Text("Сохранить", fontSize = 12.sp)
            }
            TextButton(onClick = { showSaved = true }) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp)); Text("Мои SQL", fontSize = 12.sp)
            }
            TextButton(onClick = { showHistory = true }) {
                Icon(Icons.Default.History, null, Modifier.size(16.dp)); Text("История", fontSize = 12.sp)
            }
        }

        if (task != null) {
            Surface(
                color = Color(0xFF20372F),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "УРОК ${task.lessonId + 1} · ${lessons.getOrNull(task.lessonId)?.title ?: task.topic}",
                                color = accent,
                                fontSize = 11.sp
                            )
                            Text(
                                "Задание $taskNumber/$taskCount · ID #${task.id} · ${task.difficultyName}",
                                color = muted,
                                fontSize = 11.sp
                            )
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                        }
                        TextButton(onClick = { taskExpanded = !taskExpanded }) {
                            Icon(
                                if (taskExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(19.dp)
                            )
                            Text(if (taskExpanded) "Свернуть" else "Условие", fontSize = 12.sp)
                        }
                    }
                    Text(
                        task.description,
                        fontSize = 12.sp,
                        maxLines = if (taskExpanded) Int.MAX_VALUE else 2
                    )
                    if (taskExpanded) {
                        TextButton(
                            onClick = onTaskLesson,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Повторить урок ${task.lessonId + 1}", fontSize = 12.sp)
                        }
                    }
                    if (taskExpanded) {
                        Text(task.topic, color = accent, fontSize = 11.sp, modifier = Modifier.padding(top = 5.dp))
                        if (task.expectedSql.contains("LIMIT ", ignoreCase = true)) {
                            Text(
                                "Ограничение строк: TOP (n) — после SELECT в SQL Server; " +
                                    "LIMIT n — в конце запроса SQLite. Тренажёр принимает оба варианта.",
                                color = muted,
                                fontSize = 11.sp
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { if (hintStep < task.hints.size) hintStep++ },
                                enabled = hintStep < task.hints.size,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Подсказка $hintStep/3", fontSize = 12.sp)
                            }
                            if (hintStep >= task.hints.size) {
                                TextButton(
                                    onClick = { showSolution = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Решение", fontSize = 12.sp)
                                }
                            }
                        }
                        task.hints.take(hintStep).forEachIndexed { index, hint ->
                            Text("${index + 1}. $hint", color = muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (task != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { previousTask?.let(onNavigateTask) },
                    enabled = previousTask != null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("← Назад", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = onTaskList,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("К списку", fontSize = 12.sp) }
                OutlinedButton(
                    onClick = { nextTask?.let(onNavigateTask) },
                    enabled = nextTask != null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Далее →", fontSize = 12.sp) }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Редактор SQL", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { editorLarge = !editorLarge }) {
                Icon(
                    if (editorLarge) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    null, Modifier.size(18.dp)
                )
                Text(if (editorLarge) "Обычный" else "Увеличить", fontSize = 12.sp)
            }
        }
        Surface(
            color = panel,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(if (editorLarge) 490.dp else 300.dp)
        ) {
            SqlEditorField(sql = sql, schema = schema, onSql = onSql, modifier = Modifier.fillMaxSize())
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onRun, enabled = !running, modifier = Modifier.weight(1f)) {
                if (running) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.PlayArrow, null, Modifier.size(19.dp))
                Spacer(Modifier.width(4.dp))
                Text("Выполнить")
            }
            if (task != null) {
                OutlinedButton(onClick = onCheck, enabled = !running) {
                    Icon(Icons.Default.FactCheck, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Проверить")
                }
            }
        }

        if (feedback != null) {
            Surface(
                color = if (feedback.startsWith("Верно")) Color(0xFF20372F) else Color(0xFF382F1E),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            feedback,
                            modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                            color = if (feedback.startsWith("Верно")) accent else Color(0xFFFFC66D),
                            fontSize = 12.sp
                        )
                        IconButton(onClick = onDismissFeedback, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Закрыть результат проверки",
                                tint = muted
                            )
                        }
                    }
                    if (feedback.startsWith("Верно") && task != null) {
                        Text(task.explanation, color = muted, fontSize = 12.sp)
                        TextButton(onClick = onNextTask) {
                            Text("Следующая задача по теме", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        if (error != null) {
            Surface(
                color = Color(0xFF44252B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        error,
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                        color = Color(0xFFFFB7B7),
                        fontSize = 12.sp,
                        maxLines = 5
                    )
                    IconButton(onClick = onDismissError, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть ошибку", tint = Color(0xFFFFB7B7))
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Результат", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (result != null && result.columns.isNotEmpty()) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(resultToTsv(result))) }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(15.dp))
                    Text("Копировать", fontSize = 11.sp)
                }
            }
            if (result != null) {
                TextButton(onClick = { resultExpanded = !resultExpanded }) {
                    Icon(
                        if (resultExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null, Modifier.size(17.dp)
                    )
                    Text(if (resultExpanded) "Скрыть" else "Показать", fontSize = 12.sp)
                }
            }
        }
        if (result == null) {
            Text("Напиши запрос и нажми «Выполнить».", color = muted, fontSize = 12.sp)
        } else {
            Text(
                "${result.message} · ${result.elapsedMs} мс · команд: ${result.statementsExecuted}",
                color = muted,
                fontSize = 11.sp
            )
            if (resultExpanded && result.columns.isNotEmpty()) {
                ResultGrid(result, modifier = Modifier.fillMaxWidth().height(220.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showSolution && task != null) {
        AlertDialog(
            onDismissRequest = { showSolution = false },
            title = { Text("Решение: ${task.title}") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(task.expectedSql, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = accent)
                    Spacer(Modifier.height(12.dp))
                    Text(task.explanation, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Просмотр решения сам по себе не отмечает задание выполненным.", fontSize = 11.sp, color = muted)
                }
            },
            confirmButton = { TextButton(onClick = { showSolution = false }) { Text("Закрыть") } }
        )
    }

    if (showSave) {
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Сохранить SQL") },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it.take(60) },
                    label = { Text("Название запроса") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = saveName.trim().isNotEmpty() && sql.isNotBlank(),
                    onClick = { onSave(SavedQuery(saveName.trim(), sql)); showSave = false }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Отмена") } }
        )
    }

    if (showSaved) {
        AlertDialog(
            onDismissRequest = { showSaved = false },
            title = { Text("Мои SQL-запросы") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (savedQueries.isEmpty()) Text("Пока нет сохранённых запросов.", color = muted)
                    savedQueries.forEach { query ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onHistory(query.sql); showSaved = false }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(query.name, fontSize = 13.sp, maxLines = 1)
                                    Text(query.sql.replace("\n", " ").take(50), color = muted, fontSize = 11.sp, maxLines = 1)
                                }
                            }
                            IconButton(onClick = { onDeleteSaved(query.name) }) {
                                Icon(Icons.Default.DeleteOutline, "Удалить ${query.name}", tint = muted)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSaved = false }) { Text("Закрыть") } }
        )
    }

    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("История запросов") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    if (history.isEmpty()) Text("История пока пустая.", color = muted)
                    history.forEachIndexed { index, query ->
                        TextButton(
                            onClick = { onHistory(query); showHistory = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${index + 1}. " + query.replace("\n", " ").take(110), maxLines = 2)
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Закрыть") } }
        )
    }

    if (showSchema) {
        AlertDialog(
            onDismissRequest = { showSchema = false },
            title = { Text("Таблицы и поля") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                    if (schema.isEmpty()) Text("Список таблиц загружается…", color = muted)
                    schema.forEach { table ->
                        Text(table.name, color = accent, style = MaterialTheme.typography.titleSmall)
                        Text(table.columns.joinToString(" · "), color = muted, fontSize = 12.sp)
                        Spacer(Modifier.height(13.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSchema = false }) { Text("Готово") } }
        )
    }
}

@Composable
private fun ResultGrid(result: QueryResult, modifier: Modifier = Modifier) {
    val cellWidth = 128.dp
    val fullWidth = cellWidth * result.columns.size.coerceAtLeast(1)
    Row(modifier.horizontalScroll(rememberScrollState())) {
        LazyColumn(Modifier.width(fullWidth).fillMaxHeight()) {
            item {
                Surface(color = Color(0xFF273448)) {
                    Row {
                        result.columns.forEach { column ->
                            Text(
                                column,
                                modifier = Modifier.width(cellWidth).padding(8.dp),
                                color = accent, fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace, maxLines = 2
                            )
                        }
                    }
                }
            }
            items(result.rows) { row ->
                Row {
                    row.forEach { value ->
                        Text(
                            value ?: "NULL",
                            modifier = Modifier.width(cellWidth).padding(horizontal = 8.dp, vertical = 7.dp),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2,
                            color = if (value == null) muted else Color(0xFFE6EDF7)
                        )
                    }
                }
                HorizontalDivider(color = Color(0xFF2C3543))
            }
        }
    }
}

private fun resultToTsv(result: QueryResult): String {
    fun cell(value: String?): String = (value ?: "NULL").replace('\t', ' ').replace('\n', ' ')
    val header = result.columns.joinToString("\t", transform = ::cell)
    val rows = result.rows.joinToString("\n") { row -> row.joinToString("\t", transform = ::cell) }
    return header + "\n" + rows
}
