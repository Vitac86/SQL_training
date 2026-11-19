package ru.sqltraining.app

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

private val Bg = Color(0xFF10151F)
private val Panel = Color(0xFF1B2432)
private val Green = Color(0xFF65D6AD)
private val Muted = Color(0xFF9BA9BC)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = SqlDatabase(this)
        setContent { TrainerApp(db) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainerApp(db: SqlDatabase) {
    var tab by remember { mutableIntStateOf(2) }
    var sql by remember { mutableStateOf("SELECT ticker, name, currency\nFROM instruments\nORDER BY ticker;") }
    var result by remember { mutableStateOf<QueryResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var completed by remember { mutableStateOf(setOf<Int>()) }
    var selected by remember { mutableStateOf<Lesson?>(null) }
    val scope = rememberCoroutineScope()

    MaterialTheme(colorScheme = darkColorScheme(
        primary=Green, onPrimary=Bg, background=Bg, surface=Panel,
        onBackground=Color(0xFFEAF0F7), onSurface=Color(0xFFEAF0F7),
        secondary=Muted, surfaceVariant=Color(0xFF273244)
    )) {
        Scaffold(
            containerColor=Bg,
            topBar={
                TopAppBar(
                    title={ Column { Text("SQL TRAINER", style=MaterialTheme.typography.titleMedium); Text("OFFLINE SQL LAB",color=Muted,fontSize=10.sp) } },
                    actions={ IconButton(onClick={scope.launch { db.reset(); result=null; error=null }}) { Icon(Icons.Default.RestartAlt,"Сбросить БД") } },
                    colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg)
                )
            },
            bottomBar={
                NavigationBar(containerColor=Panel) {
                    val labels=listOf("Курс","Задачи","SQL","Профиль")
                    val icons=listOf(Icons.Default.School,Icons.Default.FitnessCenter,Icons.Default.Code,Icons.Default.Insights)
                    labels.forEachIndexed { i,label ->
                        NavigationBarItem(selected=tab==i,onClick={tab=i;selected=null},icon={Icon(icons[i],label)},label={Text(label)})
                    }
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                when(tab) {
                    0 -> CourseScreen(completed,onOpen={ selected=it; sql=it.example; result=null;error=null;tab=2;selected=it },onDone={completed=completed+it.id})
                    1 -> PracticeScreen(onOpen={sql=it;result=null;error=null;tab=2})
                    2 -> if(selected!=null) LessonScreen(selected!!,onBack={selected=null},onTry={sql=selected!!.example;selected=null},onDone={completed=completed+selected!!.id;selected=null})
                         else EditorScreen(sql,{sql=it},result,error,running,history,{sql=it},onRun={
                             scope.launch {
                                 running=true;error=null
                                 try { val r=db.run(sql);result=r;history=(listOf(sql)+history.filterNot{it==sql}).take(30) }
                                 catch(e:Exception){result=null;error=e.message ?: "Ошибка SQL"}
                                 running=false
                             }
                         })
                    else -> ProfileScreen(completed,history.size,onReset={completed=emptySet();history=emptyList()})
                }
            }
        }
    }
}

@Composable
fun CourseScreen(done:Set<Int>,onOpen:(Lesson)->Unit,onDone:(Lesson)->Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Программа обучения",style=MaterialTheme.typography.headlineSmall)
        Text("${done.size} из ${lessons.size} тем пройдено",color=Muted)
        LinearProgressIndicator(progress={done.size.toFloat()/lessons.size},modifier=Modifier.fillMaxWidth().padding(vertical=12.dp),color=Green)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(lessons) { l ->
                Card(onClick={onOpen(l)},colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("${l.id+1}. ${l.title}",style=MaterialTheme.typography.titleSmall);Text(l.level,color=Green,fontSize=11.sp) }
                        if(l.id in done) Icon(Icons.Default.CheckCircle,null,tint=Green) else Icon(Icons.Default.ChevronRight,null,tint=Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun LessonScreen(l:Lesson,onBack:()->Unit,onTry:()->Unit,onDone:()->Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TextButton(onClick=onBack){Text("← К программе")}
        Text(l.title,style=MaterialTheme.typography.headlineSmall)
        Text(l.level.uppercase(),color=Green)
        Text(l.description,style=MaterialTheme.typography.bodyLarge)
        Text("Пример запроса",style=MaterialTheme.typography.titleMedium)
        Surface(color=Panel,shape=RoundedCornerShape(12.dp)){Text(l.example,Modifier.fillMaxWidth().padding(14.dp),fontFamily=FontFamily.Monospace,fontSize=13.sp)}
        Button(onClick=onTry,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.PlayArrow,null);Spacer(Modifier.width(8.dp));Text("Открыть в редакторе")}
        OutlinedButton(onClick=onDone,modifier=Modifier.fillMaxWidth()){Text("Отметить изученным")}
    }
}

@Composable
fun PracticeScreen(onOpen:(String)->Unit) {
    val tasks=listOf(
        "Найди все сделки на покупку" to "SELECT * FROM trades WHERE side='BUY';",
        "Количество сделок по счетам" to "SELECT account_id,COUNT(*) AS trades_count FROM trades GROUP BY account_id ORDER BY account_id;",
        "Найди инструменты без сделок" to "SELECT i.ticker FROM instruments i LEFT JOIN trades t ON t.instrument_id=i.instrument_id WHERE t.trade_id IS NULL;",
        "Три самые дорогие сделки" to "SELECT trade_id,price FROM trades ORDER BY price DESC,trade_id LIMIT 3;",
        "Комиссия по счетам" to "SELECT account_id,SUM(commission) AS total_fee FROM trades GROUP BY account_id;"
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Практические задачи",style=MaterialTheme.typography.headlineSmall)
        Text("Нажми на задачу, чтобы открыть SQL-заготовку.",color=Muted)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            items(tasks) { task ->
                Card(onClick={onOpen(task.second)},colors=CardDefaults.cardColors(containerColor=Panel)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(task.first,Modifier.weight(1f));Icon(Icons.Default.Code,null,tint=Green)
                    }
                }
            }
        }
    }
}

@Composable
fun EditorScreen(sql:String,onSql:(String)->Unit,result:QueryResult?,error:String?,running:Boolean,history:List<String>,onHistory:(String)->Unit,onRun:()->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=12.dp, vertical=8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text("SQL Playground",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
            Text("LOCAL DB",color=Green,fontSize=10.sp)
        }
        Text("Автономно на телефоне • SQLite с ограниченным T-SQL адаптером",color=Muted,fontSize=10.sp)
        Spacer(Modifier.height(8.dp))
        Surface(color=Panel,shape=RoundedCornerShape(14.dp),modifier=Modifier.weight(0.46f).fillMaxWidth()) {
            BasicTextField(value=sql,onValueChange=onSql,modifier=Modifier.fillMaxSize().padding(14.dp).verticalScroll(rememberScrollState()),textStyle=androidx.compose.ui.text.TextStyle(color=Color(0xFFE6EDF7),fontFamily=FontFamily.Monospace,fontSize=13.sp,lineHeight=19.sp),decorationBox={inner->Box { if(sql.isEmpty()) Text("Напиши SQL-запрос…",color=Muted);inner() }})
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick=onRun,enabled=!running,modifier=Modifier.weight(1f)) { if(running) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth=2.dp) else Icon(Icons.Default.PlayArrow,null);Spacer(Modifier.width(6.dp));Text("Выполнить") }
            OutlinedButton(onClick={onSql("SELECT * FROM trades LIMIT 20;")}){Text("Пример")}
        }
        Spacer(Modifier.height(10.dp))
        Text("Результат",style=MaterialTheme.typography.titleMedium)
        if(error!=null) Surface(color=Color(0xFF44252B),shape=RoundedCornerShape(10.dp)){Text(error,Modifier.fillMaxWidth().padding(12.dp),color=Color(0xFFFFB7B7),fontSize=12.sp)}
        else if(result!=null) {
            Text("${result.message} • ${result.elapsedMs} мс",color=Muted,fontSize=11.sp)
            if(result.columns.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Column {
                        Row { result.columns.forEach { Text(it,Modifier.width(130.dp).padding(7.dp),color=Green,fontSize=12.sp) } }
                        HorizontalDivider(color=Muted.copy(alpha=.25f))
                        result.rows.take(80).forEach { row->Row { row.forEach { Text(it ?: "NULL",Modifier.width(130.dp).padding(7.dp),fontSize=12.sp,maxLines=2) } } }
                    }
                }
            }
        } else Text("Запусти запрос, чтобы увидеть результат.",color=Muted,fontSize=12.sp)
        Spacer(Modifier.height(8.dp))
        if(history.isNotEmpty()) {
            Text("История",color=Muted,fontSize=11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                history.take(6).forEach { q->AssistChip(onClick={onHistory(q)},label={Text(q.replace("\n"," ").take(22),fontSize=10.sp)}) }
            }
        }
    }
}

@Composable
fun ProfileScreen(done:Set<Int>,history:Int,onReset:()->Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("Мой прогресс",style=MaterialTheme.typography.headlineSmall)
        Card(colors=CardDefaults.cardColors(containerColor=Panel)) {
            Column(Modifier.padding(18.dp)) {
                Text("${done.size} / ${lessons.size}",style=MaterialTheme.typography.headlineLarge,color=Green)
                Text("изученных тем")
                LinearProgressIndicator(progress={done.size.toFloat()/lessons.size},modifier=Modifier.fillMaxWidth().padding(top=12.dp),color=Green)
            }
        }
        Card(colors=CardDefaults.cardColors(containerColor=Panel)){Column(Modifier.padding(18.dp)){Text("$history",style=MaterialTheme.typography.headlineMedium);Text("запросов в истории")}}
        Text("Данные учебной БД находятся локально на устройстве. Прогресс и история пока хранятся в памяти текущего сеанса.",color=Muted)
        OutlinedButton(onClick=onReset){Text("Сбросить статистику")}
    }
}
