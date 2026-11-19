package ru.sqltraining.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QueryResult(val columns:List<String>,val rows:List<List<String?>>,val message:String,val elapsedMs:Long)
class SqlDatabase(context:Context):SQLiteOpenHelper(context,"sql_trainer.db",null,1){
 private val app=context.applicationContext
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("PRAGMA foreign_keys=ON")
  db.execSQL("CREATE TABLE instruments(instrument_id INTEGER PRIMARY KEY,ticker TEXT NOT NULL UNIQUE,name TEXT NOT NULL,asset_class TEXT NOT NULL,currency TEXT NOT NULL)")
  db.execSQL("CREATE TABLE accounts(account_id INTEGER PRIMARY KEY,account_name TEXT NOT NULL UNIQUE,strategy TEXT NOT NULL,risk_limit_rub NUMERIC NOT NULL CHECK(risk_limit_rub>0))")
  db.execSQL("CREATE TABLE trades(trade_id INTEGER PRIMARY KEY,trade_date TEXT NOT NULL,account_id INTEGER NOT NULL REFERENCES accounts(account_id),instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),side TEXT NOT NULL CHECK(side IN ('BUY','SELL')),quantity INTEGER NOT NULL CHECK(quantity>0),price NUMERIC NOT NULL CHECK(price>0),commission NUMERIC NOT NULL CHECK(commission>=0))")
  db.execSQL("CREATE TABLE daily_prices(price_date TEXT NOT NULL,instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),close_price NUMERIC NOT NULL CHECK(close_price>0),volume INTEGER NOT NULL CHECK(volume>=0),PRIMARY KEY(price_date,instrument_id))")
  db.execSQL("CREATE TABLE liquidity(price_date TEXT NOT NULL,instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),turnover_rub NUMERIC NOT NULL CHECK(turnover_rub>=0),trades_count INTEGER NOT NULL CHECK(trades_count>=0),bid_ask_spread_bps NUMERIC NOT NULL CHECK(bid_ask_spread_bps>=0),PRIMARY KEY(price_date,instrument_id))")
  val lines=app.assets.open("seed.sql").bufferedReader().use{it.readLines()}
  db.beginTransaction();try{lines.filter{it.startsWith("INSERT INTO ")}.forEach{db.execSQL(it)};db.setTransactionSuccessful()}finally{db.endTransaction()}
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){}
 suspend fun run(raw:String):QueryResult=withContext(Dispatchers.IO){
  val started=System.nanoTime();val sql=TsqlLiteAdapter.convert(raw).trim().removeSuffix(";");require(sql.isNotBlank()){"Введите SQL-запрос."}
  val db=writableDatabase;val first=Regex("^([A-Za-z]+)").find(sql)?.groupValues?.get(1)?.uppercase()?:""
  try{
   if(first in listOf("SELECT","WITH","PRAGMA","EXPLAIN"))db.rawQuery(sql,null).use{c->
    val cols=(0 until c.columnCount).map{c.getColumnName(it)};val rows=mutableListOf<List<String?>>()
    while(c.moveToNext()&&rows.size<500)rows+=(0 until c.columnCount).map{i->if(c.isNull(i))null else c.getString(i)}
    QueryResult(cols,rows,"Строк: ${rows.size}"+if(rows.size==500)" (первые 500)" else "",(System.nanoTime()-started)/1_000_000)
   }else{db.execSQL(sql);QueryResult(emptyList(),emptyList(),"Команда выполнена.",(System.nanoTime()-started)/1_000_000)}
  }catch(e:Exception){throw IllegalArgumentException(e.message?:"Ошибка SQL")}
 }
 suspend fun reset()=withContext(Dispatchers.IO){close();app.deleteDatabase("sql_trainer.db");writableDatabase}
}
object TsqlLiteAdapter{
 fun convert(input:String):String{
  var s=input.replace(Regex("(?im)^\\s*(GO|USE\\s+\\w+)\\s*;?\\s*$"),"")
   .replace(Regex("(?i)\\bdbo\\."),"").replace(Regex("(?i)N'"),"'")
   .replace(Regex("(?i)\\bISNULL\\s*\\("),"IFNULL(").replace(Regex("(?i)\\bLEN\\s*\\("),"LENGTH(")
   .replace(Regex("(?i)\\bGETDATE\\s*\\(\\s*\\)"),"datetime('now')")
  val top=Regex("(?is)^\\s*SELECT\\s+(?:DISTINCT\\s+)?TOP\\s*\\(?\\s*(\\d+)\\s*\\)?").find(s)
  if(top!=null){s=s.replaceRange(top.range,top.value.replace(Regex("(?i)TOP\\s*\\(?\\s*\\d+\\s*\\)?"),"").trimEnd());if(!Regex("(?i)\\bLIMIT\\b").containsMatchIn(s))s+=" LIMIT ${top.groupValues[1]}"}
  s
 }
}