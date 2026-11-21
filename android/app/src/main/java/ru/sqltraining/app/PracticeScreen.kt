package ru.sqltraining.app

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val green = Color(0xFF65D6AD)
private val muted = Color(0xFF9BA9BC)
private val panel = Color(0xFF1B2432)

@Composable
fun PracticeScreen(
    tasks: List<PracticeTask>,
    activeId: Int?,
    completed: Set<Int>,
    completedLessons: Set<Int>,
    focusLessonId: Int?,
    onOpen: (PracticeTask) -> Unit,
    onOpenLesson: (Lesson) -> Unit
) {
    val path = remember(tasks) { PracticePath.ordered(tasks) }
    var search by remember { mutableStateOf("") }
    var difficulty by remember { mutableIntStateOf(0) }
    var status by remember { mutableIntStateOf(0) }
    var lessonFilter by remember { mutableIntStateOf(focusLessonId ?: -1) }
    var expanded by remember { mutableStateOf(setOf(focusLessonId ?: path.firstOrNull()?.lessonId ?: 0)) }
    LaunchedEffect(focusLessonId) {
        if (focusLessonId != null) {
            lessonFilter = focusLessonId
            expanded = expanded + focusLessonId
        }
    }
    val visible = remember(path, completed, search, lessonFilter, difficulty, status) {
        path.filter { task ->
            (lessonFilter < 0 || task.lessonId == lessonFilter) &&
            (difficulty == 0 || task.difficulty == difficulty) &&
            (status == 0 || (status == 1 && task.id !in completed) || (status == 2 && task.id in completed)) &&
            (search.isBlank() || listOf(task.title, task.description, task.topic, task.id.toString())
                .any { it.contains(search.trim(), ignoreCase = true) })
        }
    }
    val grouped = remember(visible) { visible.groupBy(PracticeTask::lessonId) }
    val solved = tasks.count { it.id in completed }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("Практика по урокам", style = MaterialTheme.typography.headlineSmall)
        Text(
            "$solved / ${tasks.size} задач решено · изучено уроков ${completedLessons.size} / ${lessons.size}",
            color = muted, fontSize = 12.sp
        )
        LinearProgressIndicator(
            progress = { if (tasks.isEmpty()) 0f else solved.toFloat() / tasks.size },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 9.dp),
            color = green
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { PracticePath.nextUnsolved(tasks, completed)?.let(onOpen) },
                enabled = tasks.any { it.id !in completed },
                modifier = Modifier.weight(1f)
            ) { Text("Продолжить", fontSize = 12.sp) }
            OutlinedButton(
                onClick = {
                    val options = visible.filterNot { it.id in completed }.ifEmpty { visible }
                    options.randomOrNull()?.let(onOpen)
                },
                enabled = visible.isNotEmpty()
            ) {
                Icon(Icons.Default.Shuffle, null, Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("Случайная", fontSize = 12.sp)
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Найти задачу") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = lessonFilter == -1,
                onClick = { lessonFilter = -1 },
                label = { Text("Все уроки") }
            )
            if (lessonFilter >= 0) {
                FilterChip(
                    selected = true,
                    onClick = { lessonFilter = -1 },
                    label = { Text("Урок ${lessonFilter + 1} · Снять фильтр") }
                )
            }
            listOf("Все уровни", "Базовые", "Средние", "Сложные").forEachIndexed { index, label ->
                FilterChip(
                    selected = difficulty == index,
                    onClick = { difficulty = index },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Все задачи", "Осталось", "Решено").forEachIndexed { index, label ->
                FilterChip(
                    selected = status == index,
                    onClick = { status = index },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
        Text("По порядку уроков · найдено ${visible.size} задач", color = muted, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MobileCurriculum.courseLessons().forEach { lesson ->
                val current = grouped[lesson.id].orEmpty()
                if (current.isNotEmpty()) {
                    val allForLesson = PracticePath.forLesson(tasks, lesson.id)
                    val totalSolved = allForLesson.count { it.id in completed }
                    val isOpen = lesson.id in expanded || search.isNotBlank()
                    item(key = "lesson-${lesson.id}") {
                        Card(
                            onClick = {
                                expanded = if (lesson.id in expanded) expanded - lesson.id else expanded + lesson.id
                            },
                            colors = CardDefaults.cardColors(containerColor = panel),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(13.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Урок ${lesson.id + 1} · ${lesson.title}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "Задачи: $totalSolved / ${allForLesson.size} решено" +
                                                if (lesson.id in completedLessons) " · урок освоен" else "",
                                            color = muted, fontSize = 12.sp
                                        )
                                    }
                                    Icon(
                                        if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isOpen) "Свернуть список" else "Раскрыть задачи",
                                        tint = green
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = {
                                        if (allForLesson.isEmpty()) 0f
                                        else totalSolved.toFloat() / allForLesson.size
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                    color = green
                                )
                                TextButton(
                                    onClick = { onOpenLesson(lesson) },
                                    contentPadding = PaddingValues(0.dp)
                                ) { Text("Повторить теорию урока", fontSize = 12.sp) }
                            }
                        }
                    }
                    if (isOpen) {
                        items(current, key = { "task-${it.id}" }) { task ->
                            val number = allForLesson.indexOfFirst { it.id == task.id } + 1
                            Card(
                                onClick = { onOpen(task) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF16212D)),
                                modifier = Modifier.padding(start = 14.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Задание $number/${allForLesson.size} · ${task.title}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${task.difficultyName} · ID #${task.id}" +
                                                if (task.id == activeId) " · открыто" else "",
                                            color = muted,
                                            fontSize = 11.sp
                                        )
                                        Text(task.description, color = muted, fontSize = 11.sp, maxLines = 2)
                                    }
                                    if (task.id in completed) {
                                        Icon(Icons.Default.CheckCircle, "Решено", tint = green)
                                    } else {
                                        Icon(Icons.Default.ChevronRight, null, tint = muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "По заданным фильтрам задач нет. Выбери «Все уроки» или измени фильтры.",
                        color = muted,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            item {
                Text(
                    "ID сохраняется для истории и прогресса. Порядковый номер задания — его место внутри урока.",
                    color = muted, fontSize = 11.sp, modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
