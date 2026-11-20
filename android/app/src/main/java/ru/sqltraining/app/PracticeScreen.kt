package ru.sqltraining.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Shuffle
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
    onOpen: (PracticeTask) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("Все темы") }
    var difficulty by remember { mutableIntStateOf(0) }
    var status by remember { mutableIntStateOf(0) }
    val topics = remember(tasks) { tasks.map(PracticeTask::topic).distinct() }
    val visible = remember(tasks, completed, search, topic, difficulty, status) {
        tasks.filter { task ->
            (topic == "Все темы" || task.topic == topic) &&
            (difficulty == 0 || task.difficulty == difficulty) &&
            (status == 0 || (status == 1 && task.id !in completed) || (status == 2 && task.id in completed)) &&
            (search.isBlank() || listOf(task.title, task.description, task.topic, task.id.toString())
                .any { it.contains(search.trim(), ignoreCase = true) })
        }
    }
    val solved = tasks.count { it.id in completed }

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Тренажёр SQL", style = MaterialTheme.typography.headlineSmall)
                Text("$solved из ${tasks.size} задач решено", color = muted, fontSize = 12.sp)
            }
            IconButton(
                onClick = {
                    val options = visible.filterNot { it.id in completed }.ifEmpty { visible }
                    options.randomOrNull()?.let(onOpen)
                },
                enabled = visible.isNotEmpty()
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = "Случайная задача", tint = green)
            }
        }
        LinearProgressIndicator(
            progress = { if (tasks.isEmpty()) 0f else solved.toFloat() / tasks.size },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp),
            color = green
        )
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Поиск задачи или темы") },
            placeholder = { Text("Например, JOIN или комиссия") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("Все темы").plus(topics).forEach { name ->
                FilterChip(
                    selected = topic == name,
                    onClick = { topic = name },
                    label = { Text(name, fontSize = 11.sp, maxLines = 1) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Все уровни", "Базовый", "Средний", "Сложный").forEachIndexed { index, name ->
                FilterChip(
                    selected = difficulty == index,
                    onClick = { difficulty = index },
                    label = { Text(name, fontSize = 11.sp) }
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Все", "Не решены", "Решены").forEachIndexed { index, name ->
                FilterChip(
                    selected = status == index,
                    onClick = { status = index },
                    label = { Text(name, fontSize = 11.sp) }
                )
            }
        }
        Text(
            "Найдено задач: ${visible.size}",
            color = muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 7.dp)
        )
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(visible, key = PracticeTask::id) { task ->
                Card(
                    onClick = { onOpen(task) },
                    colors = CardDefaults.cardColors(containerColor = panel),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "#${task.id} · ${task.title}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(task.description, color = muted, fontSize = 12.sp, maxLines = 3)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(task.difficultyName, color = green, fontSize = 11.sp)
                                Text(task.topic, color = muted, fontSize = 11.sp, maxLines = 1)
                            }
                            if (activeId == task.id) Text("Открыто в редакторе", color = green, fontSize = 11.sp)
                        }
                        if (task.id in completed) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Задание решено", tint = green)
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = muted)
                        }
                    }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text(
                        "По выбранным фильтрам заданий нет. Измени тему, сложность или статус.",
                        color = muted,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
