package ru.sqltraining.app

import android.content.Context
import android.database.Cursor
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

data class DatabaseObject(val name: String, val columns: List<String>)

class SqlDatabase(context: Context) : SQLiteOpenHelper(context, "sql_trainer.db", null, 1) {
    private val app = context.applicationContext

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE instruments(instrument_id INTEGER PRIMARY KEY,ticker TEXT NOT NULL UNIQUE,name TEXT NOT NULL,asset_class TEXT NOT NULL,currency TEXT NOT NULL)")
        db.execSQL("CREATE TABLE accounts(account_id INTEGER PRIMARY KEY,account_name TEXT NOT NULL UNIQUE,strategy TEXT NOT NULL,risk_limit_rub NUMERIC NOT NULL CHECK(risk_limit_rub>0))")
        db.execSQL("CREATE TABLE trades(trade_id INTEGER PRIMARY KEY,trade_date TEXT NOT NULL,account_id INTEGER NOT NULL REFERENCES accounts(account_id),instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),side TEXT NOT NULL CHECK(side IN ('BUY','SELL')),quantity INTEGER NOT NULL CHECK(quantity>0),price NUMERIC NOT NULL CHECK(price>0),commission NUMERIC NOT NULL CHECK(commission>=0))")
        db.execSQL("CREATE TABLE daily_prices(price_date TEXT NOT NULL,instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),close_price NUMERIC NOT NULL CHECK(close_price>0),volume INTEGER NOT NULL CHECK(volume>=0),PRIMARY KEY(price_date,instrument_id))")
        db.execSQL("CREATE TABLE liquidity(price_date TEXT NOT NULL,instrument_id INTEGER NOT NULL REFERENCES instruments(instrument_id),turnover_rub NUMERIC NOT NULL CHECK(turnover_rub>=0),trades_count INTEGER NOT NULL CHECK(trades_count>=0),bid_ask_spread_bps NUMERIC NOT NULL CHECK(bid_ask_spread_bps>=0),PRIMARY KEY(price_date,instrument_id))")
        val seed = app.assets.open("seed.sql").bufferedReader().use { it.readLines() }
        db.beginTransaction()
        try {
            seed.filter { it.startsWith("INSERT INTO ") }.forEach(db::execSQL)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    suspend fun schema(): List<DatabaseObject> = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val objects = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type IN ('table','view') AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) objects.add(cursor.getString(0))
        }
        objects.map { name ->
            val safe = name.replace("\"", "\"\"")
            val columns = mutableListOf<String>()
            db.rawQuery("PRAGMA table_info(\"$safe\")", null).use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) columns.add(cursor.getString(nameIndex))
            }
            DatabaseObject(name, columns)
        }
    }

    suspend fun run(raw: String): QueryResult = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        val statements = SqlScriptParser.split(TsqlLiteAdapter.prepare(raw)).map(TsqlLiteAdapter::convert)
        require(statements.isNotEmpty()) { "Введите SQL-запрос." }
        val db = writableDatabase
        var columns = emptyList<String>()
        var rows = emptyList<List<String?>>()
        var message = "Команда выполнена."
        var count = 0
        try {
            for (sql in statements) {
                val first = Regex("""^[A-Za-z]+""").find(sql)?.value?.uppercase() ?: ""
                when (first) {
                    "BEGIN" -> {
                        require(!db.inTransaction()) { "Вложенные транзакции не поддерживаются." }
                        db.beginTransaction()
                        message = "Транзакция начата."
                        columns = emptyList(); rows = emptyList()
                    }
                    "COMMIT", "END" -> {
                        require(db.inTransaction()) { "Нет транзакции для COMMIT." }
                        db.setTransactionSuccessful()
                        db.endTransaction()
                        message = "Транзакция зафиксирована."
                        columns = emptyList(); rows = emptyList()
                    }
                    "ROLLBACK" -> {
                        require(db.inTransaction()) { "Нет транзакции для ROLLBACK." }
                        db.endTransaction()
                        message = "Транзакция отменена."
                        columns = emptyList(); rows = emptyList()
                    }
                    "SELECT", "WITH", "PRAGMA", "EXPLAIN", "VALUES" -> {
                        db.rawQuery(sql, null).use { cursor ->
                            columns = (0 until cursor.columnCount).map(cursor::getColumnName)
                            rows = readRows(cursor, 500)
                            message = "Получено строк: ${rows.size}" + if (rows.size == 500) " (первые 500)" else ""
                        }
                    }
                    else -> {
                        db.execSQL(sql)
                        columns = emptyList(); rows = emptyList()
                        message = "Команда выполнена."
                    }
                }
                count++
            }
            if (db.inTransaction()) {
                db.endTransaction()
                message = "Незавершённая транзакция автоматически отменена. Добавь COMMIT или ROLLBACK."
            }
            QueryResult(columns, rows, message, (System.nanoTime() - started) / 1_000_000, count)
        } catch (error: Exception) {
            if (db.inTransaction()) db.endTransaction()
            throw IllegalArgumentException(SqlErrors.explain(error.message ?: "Ошибка SQL"))
        }
    }

    /**
     * Compare learner and reference queries against the same database using
     * a dedicated read-only connection. Assessment must never mutate tables.
     */
    suspend fun assess(userSql: String, expectedSql: String): Pair<QueryResult, Boolean> =
        withContext(Dispatchers.IO) {
            val dbPath = readableDatabase.path
            val readOnly = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val actual = readOnlyQuery(readOnly, userSql)
                val expected = readOnlyQuery(readOnly, expectedSql)
                actual to (
                    actual.columns.map(String::lowercase) == expected.columns.map(String::lowercase) &&
                    actual.rows == expected.rows
                )
            } catch (error: Exception) {
                throw IllegalArgumentException(SqlErrors.explain(error.message ?: "Ошибка проверки"))
            } finally {
                readOnly.close()
            }
        }

    private fun readOnlyQuery(db: SQLiteDatabase, raw: String): QueryResult {
        val started = System.nanoTime()
        val statements = SqlScriptParser.split(TsqlLiteAdapter.prepare(raw))
        require(statements.size == 1) { "Для проверки задания нужен один SELECT без изменяющих команд." }
        val sql = TsqlLiteAdapter.convert(statements.single())
        require(Regex("""(?is)^\s*(SELECT|WITH)\b""").containsMatchIn(sql)) {
            "Для проверки задания используй SELECT. Изменять данные здесь нельзя."
        }
        return db.rawQuery(sql, null).use { cursor ->
            val columns = (0 until cursor.columnCount).map(cursor::getColumnName)
            val rows = readRows(cursor, 500)
            QueryResult(columns, rows, "Получено строк: ${rows.size}", (System.nanoTime() - started) / 1_000_000)
        }
    }

    private fun readRows(cursor: Cursor, limit: Int): List<List<String?>> {
        val rows = ArrayList<List<String?>>()
        while (rows.size < limit && cursor.moveToNext()) {
            rows += (0 until cursor.columnCount).map { index ->
                if (cursor.isNull(index)) null else cursor.getString(index)
            }
        }
        return rows
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        close()
        check(app.deleteDatabase("sql_trainer.db")) { "Не удалось удалить учебную базу." }
        writableDatabase
    }
}
