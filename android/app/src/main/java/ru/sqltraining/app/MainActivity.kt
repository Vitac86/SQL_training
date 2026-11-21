package ru.sqltraining.app

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
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
 var tab by remember{mutableIntStateOf(0)}
 val defaultSql = """SELECT ticker, name, currency
FROM instruments
ORDER BY ticker;""".trimIndent()
 var sql by remember { mutableStateOf(prefs.getString("editor_sql", defaultSql) ?: defaultSql) }
 var result by remember{mutableStateOf<QueryResult?>(null)}
 var error by remember{mutableStateOf<String?>(null)}
 var running by remember{mutableStateOf(false)}
 var history by remember{mutableStateOf(runCatching{val j=JSONArray(prefs.getString("history","[]"));(0 until j.length()).map{j.getString(it)}}.getOrDefault(emptyList()))}
 var completedTasks by remember{mutableStateOf((prefs.getStringSet("completed_tasks",emptySet())?:emptySet()).mapNotNull{it.toIntOrNull()}.toSet())}
 var schema by remember { mutableStateOf(emptyList<DatabaseObject>()) }
 var savedQueries by remember { mutableStateOf(SavedQueryStore.load(prefs)) }
 var showResetDialog by remember { mutableStateOf(false) }
 var showSettingsMenu by remember { mutableStateOf(false) }
 var showGithubUpdates by remember { mutableStateOf(false) }
 var selectedLesson by remember{mutableStateOf<Lesson?>(null)}
 var exampleLessonId by remember { mutableStateOf<Int?>(null) }
 var activeTask by remember{mutableStateOf<PracticeTask?>(null)}
 var taskFeedback by remember{mutableStateOf<String?>(null)}
 var practiceFocusLessonId by remember { mutableStateOf<Int?>(null) }
 var taskDrafts by remember { mutableStateOf(PracticeDraftStore.load(prefs)) }
 val context = LocalContext.current
 val allPracticeTasks = remember(context) { PracticeCatalog.load(context) }
 val practiceTasks = remember(allPracticeTasks) { MobileCurriculum.tasks(allPracticeTasks) }
 val completed = MobileCurriculum.masteredLessons(practiceTasks, completedTasks)
 val courseContent = remember(context) { CourseContentStore.load(context) }
 val scope= rememberCoroutineScope()
 val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

 fun openPracticeTask(task: PracticeTask) {
   activeTask?.let { previous -> taskDrafts = taskDrafts + (previous.id to sql) }
   activeTask = task
   exampleLessonId = null
   sql = taskDrafts[task.id] ?: task.starter
   selectedLesson = null
   taskFeedback = null
   result = null
   error = null
   practiceFocusLessonId = task.lessonId
   tab = 2
 }


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
   topBar={TopAppBar(title={
      Row(verticalAlignment=Alignment.CenterVertically) {
        Image(
          painter=painterResource(R.drawable.sql_slime),
          contentDescription=null,
          modifier=Modifier.size(38.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column {
          Text("SQL TRAINER",style=MaterialTheme.typography.titleMedium)
          Text("OFFLINE SQL LAB • v0.8.0",color=Muted,fontSize=10.sp)
        }
      }
    },
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
      onOpen={selectedLesson=it;exampleLessonId=null;tab=2},
      onContinue={lesson->selectedLesson=lesson;exampleLessonId=null;tab=2},
      onPractice={lessonId->practiceFocusLessonId=lessonId;tab=1;selectedLesson=null}
    )
    1->PracticeScreen(
      tasks=practiceTasks,
      activeId=activeTask?.id,
      completed=completedTasks,
      completedLessons=completed,
      focusLessonId=practiceFocusLessonId,
      onOpen={task->openPracticeTask(task)},
      onOpenLesson={lesson->selectedLesson=lesson;exampleLessonId=null;tab=2}
    )
    2 -> if (selectedLesson != null) {
       LessonScreen(
         lesson = selectedLesson!!,
         detail = courseContent.detail[selectedLesson!!.id],
         dialect = courseContent.syntax[selectedLesson!!.id],
         practiceCount = practiceTasks.count { it.lessonId == selectedLesson!!.id },
         completedCount = practiceTasks.count { it.lessonId == selectedLesson!!.id && it.id in completedTasks },
         onBack = { selectedLesson = null; tab=0 },
         onTry = { example ->
           activeTask?.let { t -> taskDrafts = taskDrafts + (t.id to sql) }
           exampleLessonId = selectedLesson!!.id
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
         onContinue = {
           val next = MobileCurriculum.nextUnfinishedLesson(
             selectedLesson!!.id, practiceTasks, completedTasks
           )
           if (next != null) selectedLesson = next else { selectedLesson = null; tab = 0 }
         }
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
         exampleLesson = exampleLessonId?.let { id -> MobileCurriculum.courseLessons().firstOrNull { it.id == id } },
         onReturnExampleLesson = {
           exampleLessonId?.let { id ->
             selectedLesson = MobileCurriculum.courseLessons().firstOrNull { it.id == id }
           }
         },
         onStartExampleTasks = {
           exampleLessonId?.let { id ->
             val task = MobileCurriculum.nextUnsolvedTaskOfLesson(id, practiceTasks, completedTasks)
                 ?: PracticePath.forLesson(practiceTasks,id).firstOrNull()
             if (task != null) openPracticeTask(task)
           }
         },
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
                 if (assessment.correct) {
                   val newCompleted = completedTasks + chosen.id
                   completedTasks = newCompleted
                   if (chosen.lessonId in MobileCurriculum.masteredLessons(practiceTasks, newCompleted)) {
                     taskFeedback = "Верно! Все задачи урока решены — он освоен автоматически."
                   }
                 }
                 history = (listOf(sql) + history.filterNot { it == sql }).take(50)
               } catch (e: Exception) {
                 error = e.message ?: "Ошибка SQL"
               } finally { running = false }
             }
           }
         },
         onNextTask = {
           activeTask?.let { current ->
             val next = MobileCurriculum.nextUnsolvedTaskOfLesson(
               current.lessonId, practiceTasks, completedTasks
             )
             if (next != null) openPracticeTask(next)
             else {
               val lesson = MobileCurriculum.nextUnfinishedLesson(
                 current.lessonId, practiceTasks, completedTasks
               )
               if (lesson != null) {
                 selectedLesson = lesson
                 exampleLessonId = null
                 activeTask = null
                 taskFeedback = null
                 result = null
                 error = null
                 tab = 2
               } else { tab = 0; activeTask = null }
             }
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
             selectedLesson = MobileCurriculum.courseLessons().firstOrNull { it.id == task.lessonId }
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
       onOpenLesson={lesson->selectedLesson=lesson;exampleLessonId=null;tab=2},
       onOpenPractice={lessonId->practiceFocusLessonId=lessonId;tab=1;selectedLesson=null},
       onReset={completedTasks=emptySet();history=emptyList()}
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
    onPractice: (Int) -> Unit,
    onContinue: (Lesson) -> Unit
) {
    val mobileLessons = MobileCurriculum.courseLessons()
    val next = MobileCurriculum.firstUnfinishedLesson(tasks, taskDone)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Курс SQL", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Материал → свой запрос → задачи → следующий урок. " +
                "Урок засчитывается автоматически, когда решены все его задачи.",
            fontSize = 12.sp, color = Muted
        )
        Text(
            "Освоено ${done.size} из ${mobileLessons.size} уроков · " +
                "${taskDone.count { id -> tasks.any { it.id == id } }} из ${tasks.size} задач",
            color = Muted, fontSize = 12.sp
        )
        LinearProgressIndicator(
            progress = {
                if (mobileLessons.isEmpty()) 0f else done.size.toFloat() / mobileLessons.size
            },
            modifier = Modifier.fillMaxWidth(), color = Green
        )
        Button(
            onClick = { next?.let(onContinue) },
            enabled = next != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (next == null) "Мобильный курс завершён" else "Продолжить — урок ${next.id + 1}")
        }
        Text("Уроки", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(mobileLessons, key = { it.id }) { lesson ->
                val forLesson = PracticePath.forLesson(tasks, lesson.id)
                val solved = forLesson.count { it.id in taskDone }
                val mastered = lesson.id in done
                Card(
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(13.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Урок ${lesson.id + 1}. ${lesson.title}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    if (mastered) "Освоен автоматически"
                                    else "$solved из ${forLesson.size} задач решено",
                                    color = if (mastered) Green else Muted, fontSize = 12.sp
                                )
                            }
                            Icon(
                                if (mastered) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
                                null,
                                tint = if (mastered) Green else Muted
                            )
                        }
                        Row {
                            TextButton(onClick = { onOpen(lesson) }) { Text("Материал", fontSize = 12.sp) }
                            TextButton(
                                onClick = { onPractice(lesson.id) },
                                enabled = forLesson.isNotEmpty()
                            ) { Text("Задачи (${forLesson.size})", fontSize = 12.sp) }
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
    detail: DetailedLesson?,
    dialect: LessonSyntax?,
    practiceCount: Int,
    completedCount: Int,
    onBack: () -> Unit,
    onTry: (String) -> Unit,
    onPractice: () -> Unit,
    onContinue: () -> Unit
) {
    val androidExample = dialect?.androidSql?.takeIf { it.isNotBlank() } ?: lesson.example
    val mastered = practiceCount > 0 && completedCount == practiceCount
    val chapterScroll = key(lesson.id) { rememberScrollState() }
    Column(
        Modifier.fillMaxSize().verticalScroll(chapterScroll).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) { Text("← К программе") }
        Text("Урок ${lesson.id + 1} · ${lesson.title}", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (mastered) "Урок освоен: все $practiceCount задач решены"
            else "Прогресс: $completedCount из $practiceCount задач",
            fontSize = 12.sp, color = if (mastered) Green else Muted
        )
        Text("1. Изучи материал", style = MaterialTheme.typography.titleLarge)
        if (detail != null) {
            LessonReadingSections(detail)
        } else {
            Text("Материал этой главы отсутствует.", color = Muted)
        }
        HorizontalDivider()
        Text("2. Попробуй запрос самостоятельно", style = MaterialTheme.typography.titleLarge)
        Text(
            "На телефоне работает SQLite. Текст T-SQL для SQL Server в материале " +
                "отмечен отдельно. Ниже — исполняемый вариант для Android.",
            color = Muted, fontSize = 12.sp
        )
        if (dialect != null) {
            Surface(color = Color(0xFF293242), shape = RoundedCornerShape(11.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(dialect.kindLabel, color = Color(0xFFFFD28A), fontSize = 12.sp)
                    Text(dialect.summary, fontSize = 12.sp)
                    Text(dialect.warning, fontSize = 12.sp, color = Color(0xFFFFD28A))
                    if (dialect.serverSql.isNotBlank()) {
                        Text("SQL Server / SSMS", color = Muted, fontSize = 11.sp)
                        Text(
                            dialect.serverSql,
                            fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
        Text("Пример SQLite", style = MaterialTheme.typography.titleSmall)
        Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
            Text(
                androidExample,
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        Button(onClick = { onTry("-- Напиши запрос по теме урока\nSELECT \nFROM ") },
            modifier = Modifier.fillMaxWidth()) {
            Text("Написать запрос самому")
        }
        OutlinedButton(onClick = { onTry(androidExample) }, modifier = Modifier.fillMaxWidth()) {
            Text("Открыть пример в SQL-редакторе")
        }
        HorizontalDivider()
        Text("3. Реши задачи по теме", style = MaterialTheme.typography.titleLarge)
        Text(
            "Задачи проверяются автоматически. После выполнения всех задач урок " +
                "получит статус «Освоен» — отмечать его вручную не нужно.",
            color = Muted, fontSize = 12.sp
        )
        if (practiceCount > 0) {
            Button(onClick = onPractice, modifier = Modifier.fillMaxWidth()) {
                Text(if (mastered) "Повторить задачи ($practiceCount)" else
                    "К задачам — осталось ${practiceCount - completedCount} из $practiceCount")
            }
        }
        if (mastered) {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Следующий урок →")
            }
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
            "Урок осваивается автоматически после решения всех задач по теме. Отдельных отметок нет.",
            color = Muted, fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(13.dp)) {
                    Text("${done.size}/${MobileCurriculum.courseLessons().size}", style = MaterialTheme.typography.headlineMedium, color = Green)
                    Text("уроков освоено", fontSize = 11.sp)
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(13.dp)) {
                    Text("${taskDone.count { id -> tasks.any { it.id == id } }}/${tasks.size}",
                        style = MaterialTheme.typography.headlineMedium, color = Green)
                    Text("задач решено", fontSize = 11.sp)
                }
            }
        }
        Text("По урокам", style = MaterialTheme.typography.titleMedium)
        MobileCurriculum.courseLessons().forEach { lesson ->
            val forLesson = PracticePath.forLesson(tasks, lesson.id)
            val count = forLesson.count { it.id in taskDone }
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Урок ${lesson.id + 1} · ${lesson.title}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (lesson.id in done) "Урок освоен" else "Урок в процессе",
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
                    "Будут очищены отметки о решённых задачах и история. " +
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
