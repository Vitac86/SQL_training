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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
 var sql by remember{mutableStateOf("SELECT ticker,name,currency\\nFROM instruments\\nORDER BY ticker;")}
 var result by remember{mutableStateOf<QueryResult?>(null)}
 var error by remember{mutableStateOf<String?>(null)}
 var running by remember{mutableStateOf(false)}
 var history by remember{mutableStateOf(runCatching{val j=JSONArray(prefs.getString("history","[]"));(0 until j.length()).map{j.getString(it)}}.getOrDefault(emptyList()))}
 var completed by remember{mutableStateOf((prefs.getStringSet("completed",emptySet())?:emptySet()).mapNotNull{it.toIntOrNull()}.toSet())}
 var selectedLesson by remember{mutableStateOf<Lesson?>(null)}
 var activeTask by remember{mutableStateOf<PracticeTask?>(null)}
 var taskFeedback by remember{mutableStateOf<String?>(null)}
 val scope= rememberCoroutineScope()

 LaunchedEffect(completed){prefs.edit().putStringSet("completed",completed.map{it.toString()}.toSet()).apply()}
 LaunchedEffect(history){prefs.edit().putString("history",JSONArray(history).toString()).apply()}

 MaterialTheme(colorScheme=darkColorScheme(primary=Green,onPrimary=Bg,background=Bg,surface=Panel,onBackground=Color(0xFFEAF0F7),onSurface=Color(0xFFEAF0F7),secondary=Muted,surfaceVariant=Color(0xFF273244))){
  Scaffold(
   containerColor=Bg,
   topBar={TopAppBar(title={Column{Text("SQL TRAINER",style=MaterialTheme.typography.titleMedium);Text("OFFLINE SQL LAB • v0.2",color=Muted,fontSize=10.sp)}},
    actions={IconButton(onClick={scope.launch{db.reset();result=null;error=null;taskFeedback=null}}){Icon(Icons.Default.RestartAlt,"Сбросить учебную БД")}},
    colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg))},
   bottomBar={NavigationBar(containerColor=Panel){listOf("Курс","Практика","SQL","Прогресс").forEachIndexed{i,label->
    NavigationBarItem(selected=tab==i,onClick={tab=i;selectedLesson=null},icon={Icon(listOf(Icons.Default.School,Icons.Default.FitnessCenter,Icons.Default.Code,Icons.Default.Insights)[i],label)},label={Text(label)})
   }}}
  ){padding->Box(Modifier.fillMaxSize().padding(padding)){
   when(tab){
    0->CourseScreen(completed,onOpen={selectedLesson=it},onDone={completed=completed+it.id})
    1->PracticeScreen(activeTask?.id){task->activeTask=task;taskFeedback=null;sql=task.starter;result=null;error=null;tab=2}
    2->if(selectedLesson!=null)LessonScreen(selectedLesson!!,onBack={selectedLesson=null},onTry={sql=selectedLesson!!.example;selectedLesson=null;activeTask=null},onDone={completed=completed+selectedLesson!!.id;selectedLesson=null})
      else EditorScreen(sql,{sql=it;taskFeedback=null},result,error,running,history,activeTask,taskFeedback,{sql=it},{ 
       scope.launch{running=true;error=null;taskFeedback=null
        try{val r=db.run(sql);result=r;history=(listOf(sql)+history.filterNot{it==sql}).take(50)}
        catch(e:Exception){result=null;error=e.message?:"Ошибка SQL"}
        running=false
       }
      },{
       scope.launch{running=true;error=null;taskFeedback=null
        try{
         val actual=db.run(sql);val expected=db.run(activeTask!!.expectedSql);result=actual
         val ok=actual.columns==expected.columns && actual.rows==expected.rows
         taskFeedback=if(ok)"Верно! Результат совпадает с эталоном." else "Пока не совпадает. Проверь условия, группировку и сортировку."
         history=(listOf(sql)+history.filterNot{it==sql}).take(50)
        }catch(e:Exception){error=e.message?:"Ошибка SQL"}
        running=false
       }
      })
    else->ProgressScreen(completed,history.size,{completed=emptySet();history=emptyList()})
   }
  }}
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
fun LessonScreen(lesson:Lesson,onBack:()->Unit,onTry:()->Unit,onDone:()->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  TextButton(onClick=onBack){Text("← К программе")}
  Text(lesson.title,style=MaterialTheme.typography.headlineSmall)
  Text(lesson.level.uppercase(),color=Green)
  Text(lesson.description,style=MaterialTheme.typography.bodyLarge)
  Text("Пример запроса",style=MaterialTheme.typography.titleMedium)
  Surface(color=Panel,shape=RoundedCornerShape(12.dp)){Text(lesson.example,Modifier.fillMaxWidth().padding(14.dp),fontFamily=FontFamily.Monospace,fontSize=13.sp)}
  Button(onClick=onTry,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Открыть в редакторе")}
  OutlinedButton(onClick=onDone,modifier=Modifier.fillMaxWidth()){Text("Отметить изученным")}
 }
}

@Composable
fun PracticeScreen(activeId:Int?,onOpen:(PracticeTask)->Unit){
 Column(Modifier.fillMaxSize().padding(16.dp)){
  Text("Практические задания",style=MaterialTheme.typography.headlineSmall)
  Text("Реши задачу своим запросом и сравни результат с эталоном.",color=Muted)
  Spacer(Modifier.height(12.dp))
  LazyColumn(verticalArrangement=Arrangement.spacedBy(9.dp)){items(practiceTasks){task->
   Card(onClick={onOpen(task)},colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(14.dp)){
    Row(Modifier.fillMaxWidth().padding(15.dp),verticalAlignment=Alignment.CenterVertically){
     Column(Modifier.weight(1f)){Text(task.title,style=MaterialTheme.typography.titleSmall);Text(task.description,color=Muted,fontSize=12.sp);if(task.id==activeId)Text("Открыто в редакторе",color=Green,fontSize=11.sp)}
     Icon(Icons.Default.ChevronRight,null,tint=Green)
    }
   }
  }}
 }
}

@Composable
fun EditorScreen(
 sql:String,onSql:(String)->Unit,result:QueryResult?,error:String?,running:Boolean,history:List<String>,
 task:PracticeTask?,feedback:String?,onHistory:(String)->Unit,onRun:()->Unit,onCheck:()->Unit
){
 Column(Modifier.fillMaxSize().padding(horizontal=12.dp,vertical=8.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text("SQL Playground",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));Text("LOCAL DB",color=Green,fontSize=10.sp)}
  Text("SQLite • запросы выполняются на телефоне без интернета",color=Muted,fontSize=10.sp)
  if(task!=null){
   Surface(color=Color(0xFF20372F),shape=RoundedCornerShape(10.dp),modifier=Modifier.fillMaxWidth().padding(top=7.dp)){
    Column(Modifier.padding(10.dp)){Text("ЗАДАНИЕ ${task.id}: ${task.title}",color=Green,fontSize=11.sp);Text(task.description,fontSize=12.sp);Text("Подсказка: ${task.hint}",color=Muted,fontSize=11.sp)}
   }
  }
  Spacer(Modifier.height(7.dp))
  Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){
   listOf("SELECT","WHERE","JOIN","GROUP BY","ORDER BY","COUNT(*)").forEach{word->AssistChip(onClick={onSql(sql.trimEnd()+" "+word+" ")},label={Text(word,fontSize=10.sp)})}
  }
  Surface(color=Panel,shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(.43f).fillMaxWidth()){
   BasicTextField(value=sql,onValueChange=onSql,modifier=Modifier.fillMaxSize().padding(13.dp).verticalScroll(rememberScrollState()),textStyle=androidx.compose.ui.text.TextStyle(color=Color(0xFFE6EDF7),fontFamily=FontFamily.Monospace,fontSize=13.sp,lineHeight=19.sp),decorationBox={inner->Box{if(sql.isEmpty())Text("Напиши SQL-запрос…",color=Muted);inner()}})
  }
  Spacer(Modifier.height(7.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){
   Button(onClick=onRun,enabled=!running,modifier=Modifier.weight(1f)){if(running)CircularProgressIndicator(Modifier.size(15.dp),strokeWidth=2.dp)else Icon(Icons.Default.PlayArrow,null);Text("Запустить")}
   if(task!=null)OutlinedButton(onClick=onCheck,enabled=!running){Icon(Icons.Default.FactCheck,null);Text("Проверить")}
  }
  if(feedback!=null)Text(feedback,color=if(feedback.startsWith("Верно"))Green else Color(0xFFFFC66D),fontSize=12.sp,modifier=Modifier.padding(top=5.dp))
  Spacer(Modifier.height(7.dp))
  Text("Результат",style=MaterialTheme.typography.titleMedium)
  if(error!=null)Surface(color=Color(0xFF44252B),shape=RoundedCornerShape(10.dp)){Text(error,Modifier.fillMaxWidth().padding(10.dp),color=Color(0xFFFFB7B7),fontSize=12.sp)}
  else if(result!=null){
   Text("${result.message} • ${result.elapsedMs} мс • ${result.statementsExecuted} команд",color=Muted,fontSize=10.sp)
   if(result.columns.isNotEmpty())Row(Modifier.fillMaxWidth().weight(.35f,false).horizontalScroll(rememberScrollState())){
    Column{
     Row{result.columns.forEach{Text(it,Modifier.width(125.dp).padding(6.dp),color=Green,fontSize=11.sp)}}
     HorizontalDivider(color=Muted.copy(alpha=.25f))
     result.rows.take(100).forEach{row->Row{row.forEach{Text(it?:"NULL",Modifier.width(125.dp).padding(6.dp),fontSize=11.sp,maxLines=2)}}}
    }
   }
  }else Text("Здесь появится результат выполнения запроса.",color=Muted,fontSize=11.sp)
  if(history.isNotEmpty()){
   Spacer(Modifier.height(5.dp));Text("История запросов",color=Muted,fontSize=10.sp)
   Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)){history.take(5).forEach{q->AssistChip(onClick={onHistory(q)},label={Text(q.replace("\\n"," ").take(20),fontSize=9.sp)})}}
  }
 }
}

@Composable
fun ProgressScreen(done:Set<Int>,historyCount:Int,onReset:()->Unit){
 Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text("Мой прогресс",style=MaterialTheme.typography.headlineSmall)
  Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(18.dp)){Text("${done.size} / ${lessons.size}",style=MaterialTheme.typography.headlineLarge,color=Green);Text("изученных тем");LinearProgressIndicator(progress={done.size.toFloat()/lessons.size},modifier=Modifier.fillMaxWidth().padding(top=12.dp),color=Green)}}
  Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(18.dp)){Text("$historyCount",style=MaterialTheme.typography.headlineMedium);Text("сохранённых запросов в истории")}}
  Text("Прогресс и история сохраняются на устройстве и доступны после перезапуска приложения.",color=Muted)
  OutlinedButton(onClick=onReset){Text("Сбросить статистику")}
 }
}
