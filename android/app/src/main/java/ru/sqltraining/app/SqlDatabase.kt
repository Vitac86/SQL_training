package ru.sqltraining.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QueryResult(
    val columns: List<String>,
    val rows: List<List<String?>>,
    val message: String,
    val elapsedMs: Long,
    val statementsExecuted: Int = 1
)

class SqlDatabase(context: Context) : SQLiteOpenHelper(context, "sql_trainer.db", null, 1) {
    private val app = context.applicationContext

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=ON")
        db.execSQL("CREATE TABLE instruments(instrument_id INTEGER PRIMARY KEY,ticker TEXT NOT NULL UNIQUE,name TEXT NOT NULL,asset_class TEXT NOT NULL,currency TEXT NOT NULL)")
        db.execSQL("CREATE TABLE accounts(account_id INTEGER PRIMARY KEY,account_name TEXT NOT NULL UNIQUE,strategy TEXT NOT NULL,risk_limit_rub NUMERIC NOT NULL CHECK(risk_limit_rub>0))")
        db.execSQL("CREATE TABLE trades(trade_id INTEGER PRIMARY KEY,trade_date TEXT NOT NULL,account_id INTEGER NOT NULL REFERENCES accounts(account_id),instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),side TEXT NOT NULL CHECK(side IN ('BUY','SELL')),quantity INTEGER NOT NULL CHECK(quantity>0),price NUMERIC NOT NULL CHECK(price>0),commission NUMERIC NOT NULL CHECK(commission>=0))")
        db.execSQL("CREATE TABLE daily_prices(price_date TEXT NOT NULL,instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),close_price NUMERIC NOT NULL CHECK(close_price>0),volume INTEGER NOT NULL CHECK(volume>=0),PRIMARY KEY(price_date,instrument_id))")
        db.execSQL("CREATE TABLE liquidity(price_date TEXT NOT NULL,instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),turnover_rub NUMERIC NOT NULL CHECK(turnover_rub>=0),trades_count INTEGER NOT NULL CHECK(trades_count>=0),bid_ask_spread_bps NUMERIC NOT NULL CHECK(bid_ask_spread_bps>=0),PRIMARY KEY(price_date,instrument_id))")
        val lines = app.assets.open("seed.sql").bufferedReader().use { it.readLines() }
        db.beginTransaction()
        try {
            lines.filter { it.startsWith("INSERT INTO ") }.forEach { db.execSQL(it) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    suspend fun run(raw: String): QueryResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val statements = splitStatements(TsqlLiteAdapter.convert(raw))
        require(statements.isNotEmpty()) { "Введите SQL-запрос." }
        val db = writableDatabase
        var lastColumns = emptyList<String>()
        var lastRows = emptyList<List<String?>>()
        var lastMessage = "Команды выполнены."
        var executed = 0
        try {
            statements.forEach { statement ->
                val first = Regex("^([A-Za-z]+)").find(statement)?.groupValues?.get(1)?.uppercase() ?: ""
                if (first in listOf("SELECT", "WITH", "PRAGMA", "EXPLAIN", "VALUES")) {
                    db.rawQuery(statement, null).use { cursor ->
                        lastColumns = (0 until cursor.columnCount).map { cursor.getColumnName(it) }
                        val rows = mutableListOf<List<String?>>()
                        while (cursor.moveToNext() && rows.size < 500) {
                            rows += (0 until cursor.columnCount).map { i -> if (cursor.isNull(i)) null else cursor.getString(i) }
                        }
                        lastRows = rows
                        lastMessage = "Строк: ${rows.size}" + if (rows.size == 500) " (первые 500)" else ""
                    }
                } else {
                    db.execSQL(statement)
                    lastColumns = emptyList()
                    lastRows = emptyList()
                    lastMessage = "Команда выполнена."
                }
                executed++
            }
            QueryResult(lastColumns, lastRows, lastMessage, (System.nanoTime() - started) / 1_000_000, executed)
        } catch (e: Exception) {
            if (db.inTransaction()) db.endTransaction()
            throw IllegalArgumentException(e.message ?: "Ошибка SQL")
        }
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        close()
        app.deleteDatabase("sql_trainer.db")
        writableDatabase
    }

    private fun splitStatements(sql: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            if (quote != null) {
                current.append(c)
                if (c == quote) {
                    if (i + 1 < sql.length && sql[i + 1] == quote) {
                        current.append(sql[i + 1]); i++
                    } else quote = null
                }
            } else {
                when (c) {
                    ''', '"', '`' -> { quote = c; current.append(c) }
                    ';' -> {
                        if (current.isNotBlank()) result += current.toString().trim()
                        current.clear()
                    }
                    else -> current.append(c)
                }
            }
            i++
        }
        if (current.isNotBlank()) result += current.toString().trim()
        return result
    }
}

object TsqlLiteAdapter {
    fun convert(input: String): String {
        var sql = input.replace(Regex("(?im)^\\s*(GO|USE\\s+\\w+)\\s*;?\\s*$"), "")
            .replace(Regex("(?i)\\bdbo\\."), "")
            .replace(Regex("(?i)N'"), "'")
            .replace(Regex("(?i)\\bISNULL\\s*\\("), "IFNULL(")
            .replace(Regex("(?i)\\bLEN\\s*\\("), "LENGTH(")
            .replace(Regex("(?i)\\bGETDATE\\s*\\(\\s*\\)"), "datetime('now')")
        val top = Regex("(?is)^\\s*SELECT\\s+(?:DISTINCT\\s+)?TOP\\s*\\(?\\s*(\\d+)\\s*\\)?").find(sql)
        if (top != null) {
            sql = sql.replaceRange(top.range, top.value.replace(Regex("(?i)TOP\\s*\\(?\\s*\\d+\\s*\\)?"), "").trimEnd())
            if (!Regex("(?i)\\bLIMIT\\b").containsMatchIn(sql)) sql += " LIMIT ${top.groupValues[1]}"
        }
        return sql
    }
}
