package ru.sqltraining.app

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray

private val Bg=Color(0xFF10151F)
private val Panel=Color(0xFF1B2432)
private val Green=Color(0xFF65D6AD)
private val Muted=Color(0xFF9BA9BC)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  val db=SqlDatabase(this)
  val prefs=getSharedPreferences("sql_trainer_state",MODE_PRIVATE)
  setContent{TrainerApp(db,prefs)}
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerApp(db:SqlDatabase,prefs:SharedPreferences){
 var tab by remember{mutableIntStateOf(2)}
 val defaultSql = """SELECT ticker, name, currency
FROM instruments
ORDER BY ticker;""".trimIndent()
 var sql by remember { mutableStateOf(prefs.getString("editor_sql", defaultSql) ?: defaultSql) }
 var result by remember{mutableStateOf<QueryResult?>(null)}
 var error by remember{mutableStateOf<String?>(null)}
 var running by remember{mutableStateOf(false)}
 var history by remember{mutableStateOf(runCatching{val j=JSONArray(prefs.getString("history","[]"));(0 until j.length()).map{j.getString(it)}}.getOrDefault(emptyList()))}
 var completed by remember{mutableStateOf((prefs.getStringSet("completed",emptySet())?:emptySet()).mapNotNull{it.toIntOrNull()}.toSet())}
 var completedTasks by remember{mutableStateOf((prefs.getStringSet("completed_tasks",emptySet())?:emptySet()).mapNotNull{it.toIntOrNull()}.toSet())}
 var schema by remember { mutableStateOf(emptyList<DatabaseObject>()) }
 var savedQueries by remember { mutableStateOf(SavedQueryStore.load(prefs)) }
 var showResetDialog by remember { mutableStateOf(false) }
 var showSettingsMenu by remember { mutableStateOf(false) }
 var showGithubUpdates by remember { mutableStateOf(false) }
 var selectedLesson by remember{mutableStateOf<Lesson?>(null)}
 var activeTask by remember{mutableStateOf<PracticeTask?>(null)}
 var taskFeedback by remember{mutableStateOf<String?>(null)}
 var practiceFocusLessonId by remember { mutableStateOf<Int?>(null) }
 var taskDrafts by remember { mutableStateOf(PracticeDraftStore.load(prefs)) }
 val context = LocalContext.current
 val practiceTasks = remember(context) { PracticeCatalog.load(context) }
 val lessonGuides = remember(context) { LessonGuides.load(context) }
 val courseContent = remember(context) { CourseContentStore.load(context) }
 val scope= rememberCoroutineScope()
 val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

 fun openPracticeTask(task: PracticeTask) {
   activeTask?.let { previous -> taskDrafts = taskDrafts + (previous.id to sql) }
   activeTask = task
   sql = taskDrafts[task.id] ?: task.starter
   selectedLesson = null
   taskFeedback = null
   result = null
   error = null
   practiceFocusLessonId = task.lessonId
   tab = 2
 }


 LaunchedEffect(completed){prefs.edit().putStringSet("completed",completed.map{it.toString()}.toSet()).apply()}
 LaunchedEffect(completedTasks){prefs.edit().putStringSet("completed_tasks",completedTasks.map{it.toString()}.toSet()).apply()}
 LaunchedEffect(history){prefs.edit().putString("history",JSONArray(history).toString()).apply()}
 LaunchedEffect(sql) { prefs.edit().putString("editor_sql", sql).apply() }
 LaunchedEffect(savedQueries) { SavedQueryStore.save(prefs, savedQueries) }
 LaunchedEffect(taskDrafts) {
   delay(400)
   PracticeDraftStore.save(prefs, taskDrafts)
 }

 LaunchedEffect(Unit) {
   try { schema = db.schema() }
   catch (e: Exception) { error = SqlErrors.explain(e.message ?: "Не удалось загрузить схему БД") }
 }

 MaterialTheme(colorScheme=darkColorScheme(primary=Green,onPrimary=Bg,background=Bg,surface=Panel,onBackground=Color(0xFFEAF0F7),onSurface=Color(0xFFEAF0F7),secondary=Muted,surfaceVariant=Color(0xFF273244))){
  Scaffold(
   containerColor=Bg,
   topBar={TopAppBar(title={Column{Text("SQL TRAINER",style=MaterialTheme.typography.titleMedium);Text("OFFLINE SQL LAB • v0.7.0",color=Muted,fontSize=10.sp)}},
    actions={
        TextButton(onClick={showGithubUpdates=true}) {
            Icon(Icons.Default.SystemUpdate,contentDescription=null,modifier=Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Обновить",fontSize=12.sp)
        }
        Box {
            IconButton(onClick={showSettingsMenu=true}) {
                Icon(Icons.Default.MoreVert,"Настройки")
            }
            DropdownMenu(
                expanded=showSettingsMenu,
                onDismissRequest={showSettingsMenu=false}
            ) {
                DropdownMenuItem(
                    text={Text("Сбросить учебную БД")},
                    onClick={showSettingsMenu=false;showResetDialog=true},
                    enabled=!running,
                    leadingIcon={Icon(Icons.Default.DeleteOutline,null)}
                )
            }
        }
    },
    colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg))},
   bottomBar={
       if (!imeOpen) {
         NavigationBar(containerColor=Panel){
           listOf("Курс","Практика","SQL","Прогресс").forEachIndexed { i,label ->
             NavigationBarItem(
               selected=tab==i,
               onClick={tab=i;selectedLesson=null;if(i==1)practiceFocusLessonId=null},
               icon={Icon(listOf(Icons.Default.School,Icons.Default.FitnessCenter,Icons.Default.Code,Icons.Default.Insights)[i],label)},
               label={Text(label)}
             )
           }
         }
       }
   }
  ){padding->Box(Modifier.fillMaxSize().padding(padding)){
   when(tab){
    0->CourseScreen(
      done=completed,
      tasks=practiceTasks,
      taskDone=completedTasks,
      onOpen={selectedLesson=it;tab=2},
      onPractice={lessonId->practiceFocusLessonId=lessonId;tab=1;selectedLesson=null}
    )
    1->PracticeScreen(
      tasks=practiceTasks,
      activeId=activeTask?.id,
      completed=completedTasks,
      completedLessons=completed,
      focusLessonId=practiceFocusLessonId,
      onOpen={task->openPracticeTask(task)},
      onOpenLesson={lesson->selectedLesson=lesson;tab=2}
    )
    2 -> if (selectedLesson != null) {
       LessonScreen(
         lesson = selectedLesson!!,
         guide = lessonGuides[selectedLesson!!.id],
         detail = courseContent.detail[selectedLesson!!.id],
         dialect = courseContent.syntax[selectedLesson!!.id],
         globalNotice = courseContent.globalNotice,
         practiceCount = practiceTasks.count { it.lessonId == selectedLesson!!.id },
         onBack = { selectedLesson = null },
         onTry = { example ->
           activeTask?.let { t -> taskDrafts = taskDrafts + (t.id to sql) }
           sql = example
           selectedLesson = null
           activeTask = null
           taskFeedback = null
           result = null
           error = null
         },
         onPractice = {
           val forLesson = PracticePath.forLesson(practiceTasks, selectedLesson!!.id)
           val next = forLesson.firstOrNull { it.id !in completedTasks } ?: forLesson.firstOrNull()
           if (next != null) openPracticeTask(next)
         },
         onDone = { completed = completed + selectedLesson!!.id; selectedLesson = null }
       )
     } else {
       EditorScreen(
         sql = sql,
         onSql = {
           sql = it
           activeTask?.let { t -> taskDrafts = taskDrafts + (t.id to it) }
           taskFeedback = null
           error = null
         },
         result = result,
         error = error,
         onDismissError = { error = null },
         running = running,
         history = history,
         task = activeTask,
         lessonSyntax = activeTask?.let { courseContent.syntax[it.lessonId] },
         feedback = taskFeedback,
         onDismissFeedback = { taskFeedback = null },
         schema = schema,
         savedQueries = savedQueries,
         onSave = { query ->
           savedQueries = (listOf(query) + savedQueries.filterNot { it.name.equals(query.name, ignoreCase = true) }).take(100)
         },
         onDeleteSaved = { name -> savedQueries = savedQueries.filterNot { it.name == name } },
         onHistory = { sql = it; taskFeedback = null; error = null },
         onRun = {
           scope.launch {
             running = true; error = null; taskFeedback = null
             try {
               result = db.run(sql)
               schema = db.schema()
               history = (listOf(sql) + history.filterNot { it == sql }).take(50)
             } catch (e: Exception) {
               error = e.message ?: "Ошибка SQL"
             } finally { running = false }
           }
         },
         onCheck = {
           scope.launch {
             val chosen = activeTask
             if (chosen != null) {
               running = true; error = null; taskFeedback = null
               try {
                 val assessment = db.assess(sql, chosen.expectedSql)
                 result = assessment.actual
                 taskFeedback = assessment.feedback
                 if (assessment.correct) completedTasks = completedTasks + chosen.id
                 history = (listOf(sql) + history.filterNot { it == sql }).take(50)
               } catch (e: Exception) {
                 error = e.message ?: "Ошибка SQL"
               } finally { running = false }
             }
           }
         },
         onNextTask = {
           activeTask?.let { current ->
             PracticePath.next(practiceTasks, current.id)?.let { openPracticeTask(it) }
           }
         },
         previousTask = activeTask?.let { PracticePath.previous(practiceTasks, it.id) },
         nextTask = activeTask?.let { PracticePath.next(practiceTasks, it.id) },
         taskNumber = activeTask?.let { task ->
           PracticePath.forLesson(practiceTasks, task.lessonId).indexOfFirst { it.id == task.id } + 1
         } ?: 0,
         taskCount = activeTask?.let { PracticePath.forLesson(practiceTasks, it.lessonId).size } ?: 0,
         onNavigateTask = { task -> openPracticeTask(task) },
         onTaskList = {
           practiceFocusLessonId = activeTask?.lessonId
           tab = 1
         },
         onTaskLesson = {
           activeTask?.let { task ->
             selectedLesson = lessons.firstOrNull { it.id == task.lessonId }
             tab = 2
           }
         }
       )
     }
     else->ProgressScreen(
       done=completed,
       taskDone=completedTasks,
       tasks=practiceTasks,
       historyCount=history.size,
       onOpenLesson={lesson->selectedLesson=lesson;tab=2},
       onOpenPractice={lessonId->practiceFocusLessonId=lessonId;tab=1;selectedLesson=null},
       onReset={completed=emptySet();completedTasks=emptySet();history=emptyList()}
     )
   }
  }}
  if (showGithubUpdates) {
    GithubUpdateDialog(onClose = { showGithubUpdates = false })
  }
  if (showResetDialog) {
    AlertDialog(
      onDismissRequest = { showResetDialog = false },
      title = { Text("Сбросить учебную базу?") },
      text = { Text("Все изменения данных и созданные тобой таблицы будут удалены. История, прогресс и сохранённые запросы останутся.") },
      confirmButton = {
        TextButton(onClick = {
          showResetDialog = false
          scope.launch {
            running = true
            try {
              db.reset()
              schema = db.schema()
              result = null
              error = null
              taskFeedback = null
            } catch (e: Exception) {
              error = SqlErrors.explain(e.message ?: "Не удалось сбросить базу")
              tab = 2
            } finally { running = false }
          }
        }) { Text("Сбросить") }
      },
      dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("Отмена") } }
    )
  }
 }
}

@Composable
fun CourseScreen(
    done: Set<Int>,
    tasks: List<PracticeTask>,
    taskDone: Set<Int>,
    onOpen: (Lesson) -> Unit,
    onPractice: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Программа обучения", style = MaterialTheme.typography.headlineSmall)
        Text("Сначала урок, затем задания по его теме.", color = Muted, fontSize = 12.sp)
        Text(
            "Теория: ${done.size}/${lessons.size} · Практика: ${taskDone.size}/${tasks.size}",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(lessons, key = { it.id }) { lesson ->
                val forLesson = PracticePath.forLesson(tasks, lesson.id)
                val solved = forLesson.count { it.id in taskDone }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Урок ${lesson.id + 1}. ${lesson.title}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Теория: " + if (lesson.id in done) "изучена" else "не отмечена",
                                    fontSize = 11.sp, color = if (lesson.id in done) Green else Muted
                                )
                                Text(
                                    if (forLesson.isEmpty()) "Практика: нет проверяемых задач"
                                    else "Практика: $solved из ${forLesson.size} задач",
                                    color = Muted, fontSize = 11.sp
                                )
                            }
                            if (lesson.id in done) Icon(Icons.Default.CheckCircle, null, tint = Green)
                            else Icon(Icons.Default.ChevronRight, null, tint = Muted)
                        }
                        Row {
                            TextButton(onClick = { onOpen(lesson) }) { Text("Теория", fontSize = 12.sp) }
                            if (forLesson.isNotEmpty()) {
                                TextButton(onClick = { onPractice(lesson.id) }) {
                                    Text("Задания (${forLesson.size})", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LessonScreen(
    lesson: Lesson,
    guide: LessonGuide?,
    detail: DetailedLesson?,
    dialect: LessonSyntax?,
    globalNotice: String,
    practiceCount: Int,
    onBack: () -> Unit,
    onTry: (String) -> Unit,
    onPractice: () -> Unit,
    onDone: () -> Unit
) {
    var showAnswer by remember(lesson.id) { mutableStateOf(false) }
    var showFullMaterial by remember(lesson.id) { mutableStateOf(true) }
    val androidExample = dialect?.androidSql?.takeIf { it.isNotBlank() } ?: lesson.example
    val modifiesData = Regex("""(?i)^\s*(CREATE|ALTER|DROP|INSERT|UPDATE|DELETE|BEGIN)\b""")
        .containsMatchIn(androidExample)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        TextButton(onClick = onBack) { Text("← К программе") }
        Text("Урок ${lesson.id + 1} · ${lesson.title}", style = MaterialTheme.typography.headlineSmall)
        Text(lesson.level.uppercase(), color = Green, fontSize = 12.sp)
        Text(lesson.description, style = MaterialTheme.typography.bodyLarge)

        Surface(color = Color(0xFF293242), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Среда выполнения и диалект SQL", style = MaterialTheme.typography.titleMedium)
                Text("Десктоп: SQL Server + SSMS  |  Телефон: SQLite + частичный адаптер T-SQL", color = Color(0xFFFFD28A), fontSize = 12.sp)
                Text(globalNotice, fontSize = 12.sp)
                if (dialect != null) {
                    Text(dialect.kindLabel, color = Green, style = MaterialTheme.typography.titleSmall)
                    Text(dialect.summary, style = MaterialTheme.typography.bodyMedium)
                    Text("Важно: ${dialect.warning}", fontSize = 12.sp, color = Color(0xFFFFD28A))
                }
            }
        }
        if (dialect != null && dialect.serverSql.isNotBlank()) {
            Text("Как пишется в SQL Server / SSMS", style = MaterialTheme.typography.titleMedium)
            Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("T-SQL · пример для десктопа; не для непосредственного выполнения в SQLite", color = Color(0xFFFFD28A), fontSize = 11.sp)
                    Spacer(Modifier.height(7.dp))
                    Text(
                        dialect.serverSql,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }

        if (guide != null) {
            Text("Что означает эта тема", style = MaterialTheme.typography.titleMedium)
            Text(guide.what)
            Text("Практический смысл", style = MaterialTheme.typography.titleMedium)
            Text(guide.why)
            Text("Разбор по шагам", style = MaterialTheme.typography.titleMedium)
            guide.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (detail != null) {
            Text("Развёрнутый урок", style = MaterialTheme.typography.titleLarge)
            Text(
                "${detail.sections.size} разделов на основе десктопного курса. " +
                    "Нумерация уроков в источнике относится к десктопной программе. " +
                    "Исходные примеры в разделах помечены как T-SQL / SQL Server. " +
                    "Материалы доступны без интернета.",
                color = Muted, fontSize = 12.sp
            )
            OutlinedButton(
                onClick = { showFullMaterial = !showFullMaterial },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showFullMaterial) "Свернуть подробный материал" else "Читать подробный материал")
            }
            if (showFullMaterial) LessonReadingSections(detail)
        }

        Text("Пример для Android-тренажёра", style = MaterialTheme.typography.titleMedium)
        Text(
            "Этот SQL будет перенесён в редактор без выполнения. " +
                if (modifiesData) "Обрати внимание: после ручного запуска он может изменить локальную БД."
                else "Кнопка «Выполнить» запустит его на SQLite.",
            fontSize = 12.sp,
            color = if (modifiesData) Color(0xFFFFD28A) else Muted
        )
        Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
            Text(
                androidExample,
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        Button(onClick = { onTry(androidExample) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Открыть пример SQLite в редакторе")
        }
        if (dialect?.kind == "adapter" && lesson.example.trim() != androidExample.trim()) {
            OutlinedButton(onClick = { onTry(lesson.example) }, modifier = Modifier.fillMaxWidth()) {
                Text("Попробовать T-SQL через адаптер")
            }
            Text(
                "Второй вариант работает только с ограниченным набором преобразований. " +
                    "Он не запускает Microsoft SQL Server.",
                color = Muted,
                fontSize = 11.sp
            )
        }
        if (guide != null) {
            Surface(color = Color(0xFF293242), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Частая ошибка", color = Color(0xFFFFD28A), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(guide.pitfall)
                }
            }
            Text("Проверка понимания", style = MaterialTheme.typography.titleMedium)
            Text(guide.question)
            OutlinedButton(onClick = { showAnswer = !showAnswer }) {
                Text(if (showAnswer) "Скрыть объяснение" else "Показать объяснение")
            }
            if (showAnswer) Text(guide.answer, color = Green)
        }
        if (practiceCount > 0) {
            OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth()) {
                Text("Задачи по уроку ($practiceCount)")
            }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Отметить теорию изученной")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
fun ProgressScreen(
    done: Set<Int>,
    taskDone: Set<Int>,
    tasks: List<PracticeTask>,
    historyCount: Int,
    onOpenLesson: (Lesson) -> Unit,
    onOpenPractice: (Int) -> Unit,
    onReset: () -> Unit
) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Text("Мой прогресс", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Теория отмечается отдельно от проверки задач. Каждая задача привязана к уроку.",
            color = Muted, fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(13.dp)) {
                    Text("${done.size}/${lessons.size}", style = MaterialTheme.typography.headlineMedium, color = Green)
                    Text("уроков отмечено", fontSize = 11.sp)
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(13.dp)) {
                    Text("${taskDone.size}/${tasks.size}", style = MaterialTheme.typography.headlineMedium, color = Green)
                    Text("задач решено", fontSize = 11.sp)
                }
            }
        }
        Text("По урокам", style = MaterialTheme.typography.titleMedium)
        lessons.forEach { lesson ->
            val forLesson = PracticePath.forLesson(tasks, lesson.id)
            val count = forLesson.count { it.id in taskDone }
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Урок ${lesson.id + 1} · ${lesson.title}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Теория: " + if (lesson.id in done) "изучена" else "не отмечена",
                        color = if (lesson.id in done) Green else Muted, fontSize = 11.sp
                    )
                    Text(
                        if (forLesson.isEmpty()) "Практика: нет проверяемых задач"
                        else "Практика: $count из ${forLesson.size} задач решено",
                        color = Muted, fontSize = 11.sp
                    )
                    if (forLesson.isNotEmpty()) {
                        LinearProgressIndicator(
                            progress = { count.toFloat() / forLesson.size },
                            modifier = Modifier.fillMaxWidth(), color = Green
                        )
                    }
                    Row {
                        TextButton(onClick = { onOpenLesson(lesson) }) { Text("К уроку", fontSize = 12.sp) }
                        if (forLesson.isNotEmpty()) {
                            TextButton(onClick = { onOpenPractice(lesson.id) }) {
                                Text("К заданиям", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        Text("Запросов в истории: $historyCount", color = Muted, fontSize = 12.sp)
        Text("Прогресс и черновики запросов сохраняются на устройстве.", color = Muted, fontSize = 12.sp)
        OutlinedButton(onClick = { confirmReset = true }) { Text("Сбросить статистику") }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить статистику?") },
            text = {
                Text(
                    "Будут очищены отметки о пройденных уроках, решённых задачах и история. " +
                        "Учебная база и сохранённые SQL-запросы останутся."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            }
        )
    }
}
