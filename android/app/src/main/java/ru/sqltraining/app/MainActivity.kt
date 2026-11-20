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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
 var selectedLesson by remember{mutableStateOf<Lesson?>(null)}
 var activeTask by remember{mutableStateOf<PracticeTask?>(null)}
 var taskFeedback by remember{mutableStateOf<String?>(null)}
 val context = LocalContext.current
 val practiceTasks = remember(context) { PracticeCatalog.load(context) }
 val lessonGuides = remember(context) { LessonGuides.load(context) }
 val scope= rememberCoroutineScope()

 LaunchedEffect(completed){prefs.edit().putStringSet("completed",completed.map{it.toString()}.toSet()).apply()}
 LaunchedEffect(completedTasks){prefs.edit().putStringSet("completed_tasks",completedTasks.map{it.toString()}.toSet()).apply()}
 LaunchedEffect(history){prefs.edit().putString("history",JSONArray(history).toString()).apply()}
 LaunchedEffect(sql) { prefs.edit().putString("editor_sql", sql).apply() }
 LaunchedEffect(savedQueries) { SavedQueryStore.save(prefs, savedQueries) }
 LaunchedEffect(Unit) {
   try { schema = db.schema() }
   catch (e: Exception) { error = SqlErrors.explain(e.message ?: "Не удалось загрузить схему БД") }
 }

 MaterialTheme(colorScheme=darkColorScheme(primary=Green,onPrimary=Bg,background=Bg,surface=Panel,onBackground=Color(0xFFEAF0F7),onSurface=Color(0xFFEAF0F7),secondary=Muted,surfaceVariant=Color(0xFF273244))){
  Scaffold(
   containerColor=Bg,
   topBar={TopAppBar(title={Column{Text("SQL TRAINER",style=MaterialTheme.typography.titleMedium);Text("OFFLINE SQL LAB • v0.4.1",color=Muted,fontSize=10.sp)}},
    actions={IconButton(enabled=!running,onClick={showResetDialog=true}){Icon(Icons.Default.RestartAlt,"Сбросить учебную БД")}},
    colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg))},
   bottomBar={NavigationBar(containerColor=Panel){listOf("Курс","Практика","SQL","Прогресс").forEachIndexed{i,label->
    NavigationBarItem(selected=tab==i,onClick={tab=i;selectedLesson=null},icon={Icon(listOf(Icons.Default.School,Icons.Default.FitnessCenter,Icons.Default.Code,Icons.Default.Insights)[i],label)},label={Text(label)})
   }}}
  ){padding->Box(Modifier.fillMaxSize().padding(padding)){
   when(tab){
    0->CourseScreen(completed,onOpen={selectedLesson=it;tab=2},onDone={completed=completed+it.id})
    1->PracticeScreen(practiceTasks,activeTask?.id,completedTasks){task->activeTask=task;taskFeedback=null;sql=task.starter;result=null;error=null;tab=2}
    2 -> if (selectedLesson != null) {
       LessonScreen(
         lesson = selectedLesson!!,
         guide = lessonGuides[selectedLesson!!.id],
         practiceCount = practiceTasks.count { it.lessonId == selectedLesson!!.id },
         onBack = { selectedLesson = null },
         onTry = { sql = selectedLesson!!.example; selectedLesson = null; activeTask = null },
         onPractice = {
           val forLesson = practiceTasks.filter { it.lessonId == selectedLesson!!.id }
           val next = forLesson.firstOrNull { it.id !in completedTasks } ?: forLesson.firstOrNull()
           if (next != null) {
             activeTask = next; sql = next.starter; result = null; error = null
             taskFeedback = null; selectedLesson = null
           }
         },
         onDone = { completed = completed + selectedLesson!!.id; selectedLesson = null }
       )
     } else {
       EditorScreen(
         sql = sql,
         onSql = { sql = it; taskFeedback = null; error = null },
         result = result,
         error = error,
         onDismissError = { error = null },
         running = running,
         history = history,
         task = activeTask,
         feedback = taskFeedback,
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
           val current = activeTask
           val sameTopic = practiceTasks.filter { it.topic == current?.topic && it.id !in completedTasks }
           val next = sameTopic.firstOrNull { it.id != current?.id }
               ?: practiceTasks.firstOrNull { it.id !in completedTasks && it.id != current?.id }
           if (next != null) {
             activeTask = next
             sql = next.starter
             result = null
             error = null
             taskFeedback = null
           }
         }
       )
     }
     else->ProgressScreen(completed,completedTasks,practiceTasks,history.size,{completed=emptySet();completedTasks=emptySet();history=emptyList()})
   }
  }}
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
fun CourseScreen(done:Set<Int>,onOpen:(Lesson)->Unit,onDone:(Lesson)->Unit){
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Text("Программа обучения",style=MaterialTheme.typography.headlineSmall)
  Text("${done.size} из ${lessons.size} тем пройдено",color=Muted)
  LinearProgressIndicator(progress={done.size.toFloat()/lessons.size},modifier=Modifier.fillMaxWidth().padding(vertical=12.dp),color=Green)
  LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(lessons){lesson->
   Card(onClick={onOpen(lesson)},colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(14.dp)){
    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text("${lesson.id+1}. ${lesson.title}",style=MaterialTheme.typography.titleSmall);Text(lesson.level,color=Green,fontSize=11.sp)}
     if(lesson.id in done)Icon(Icons.Default.CheckCircle,null,tint=Green)else Icon(Icons.Default.ChevronRight,null,tint=Muted)
    }
   }
  }}
 }
}

@Composable
fun LessonScreen(
    lesson: Lesson,
    guide: LessonGuide?,
    practiceCount: Int,
    onBack: () -> Unit,
    onTry: () -> Unit,
    onPractice: () -> Unit,
    onDone: () -> Unit
) {
    var showAnswer by remember(lesson.id) { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        TextButton(onClick = onBack) { Text("← К программе") }
        Text(lesson.title, style = MaterialTheme.typography.headlineSmall)
        Text(lesson.level.uppercase(), color = Green, fontSize = 12.sp)
        Text(lesson.description, style = MaterialTheme.typography.bodyLarge)

        if (guide != null) {
            Text("Что это означает", style = MaterialTheme.typography.titleMedium)
            Text(guide.what)
            Text("Зачем это нужно", style = MaterialTheme.typography.titleMedium)
            Text(guide.why)
            Text("Разберём по шагам", style = MaterialTheme.typography.titleMedium)
            guide.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step", style = MaterialTheme.typography.bodyMedium)
            }
            Surface(color = Color(0xFF293242), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("На что обратить внимание", color = Color(0xFFFFD28A), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(guide.pitfall, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Text("Пример запроса", style = MaterialTheme.typography.titleMedium)
        Surface(color = Panel, shape = RoundedCornerShape(12.dp)) {
            Text(
                lesson.example,
                Modifier.fillMaxWidth().padding(14.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        if (guide != null) {
            Text("Проверь понимание", style = MaterialTheme.typography.titleMedium)
            Text(guide.question)
            OutlinedButton(onClick = { showAnswer = !showAnswer }) {
                Text(if (showAnswer) "Скрыть объяснение" else "Показать объяснение")
            }
            if (showAnswer) Text(guide.answer, color = Green)
        }

        Button(onClick = onTry, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Попробовать запрос")
        }
        if (practiceCount > 0) {
            OutlinedButton(onClick = onPractice, modifier = Modifier.fillMaxWidth()) {
                Text("Тренировка по теме ($practiceCount задач)")
            }
        }
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Отметить тему изученной")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun ProgressScreen(done:Set<Int>,taskDone:Set<Int>,tasks:List<PracticeTask>,historyCount:Int,onReset:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text("Мой прогресс",style=MaterialTheme.typography.headlineSmall)
  Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(18.dp)){Text("${done.size} / ${lessons.size}",style=MaterialTheme.typography.headlineLarge,color=Green);Text("изученных тем");LinearProgressIndicator(progress={done.size.toFloat()/lessons.size},modifier=Modifier.fillMaxWidth().padding(top=12.dp),color=Green)}}
  Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(18.dp)){Text("${taskDone.size} / ${tasks.size}",style=MaterialTheme.typography.headlineMedium,color=Green);Text("решённых практических задач")}}
  Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(18.dp)){Text("$historyCount",style=MaterialTheme.typography.headlineMedium);Text("сохранённых запросов в истории")}}
  Text("Прогресс по темам",style=MaterialTheme.typography.titleMedium)
  tasks.groupBy { it.topic }.forEach { (topic,section) ->
    val count = section.count { it.id in taskDone }
    Text("$topic · $count / ${section.size}",fontSize=12.sp,color=Muted)
    LinearProgressIndicator(
      progress={ if(section.isEmpty()) 0f else count.toFloat()/section.size },
      modifier=Modifier.fillMaxWidth(),
      color=Green
    )
  }
  Text("Прогресс и история сохраняются на устройстве и доступны после перезапуска приложения.",color=Muted)
  OutlinedButton(onClick=onReset){Text("Сбросить статистику")}
 }
}
