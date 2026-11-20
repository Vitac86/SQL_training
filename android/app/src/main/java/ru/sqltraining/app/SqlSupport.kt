package ru.sqltraining.app

/**
 * Splits SQL into statements; semicolons inside quoted literals, identifiers,
 * and comments are never treated as separators.
 */
object SqlScriptParser {
    fun split(input: String): List<String> {
        val result = mutableListOf<String>()
        val part = StringBuilder()
        var quote: Char? = null
        var bracket = false
        var lineComment = false
        var blockComment = false
        var i = 0
        fun flush() {
            val statement = part.toString().trim()
            if (statement.isNotEmpty()) result += statement
            part.clear()
        }
        while (i < input.length) {
            val c = input[i]
            val next = input.getOrNull(i + 1)
            when {
                lineComment -> {
                    if (c == '\n') { lineComment = false; part.append('\n') }
                }
                blockComment -> {
                    if (c == '*' && next == '/') { blockComment = false; i++ }
                    else if (c == '\n') part.append('\n')
                }
                quote != null -> {
                    part.append(c)
                    if (c == quote) {
                        if (next == quote) { part.append(next); i++ }
                        else quote = null
                    }
                }
                bracket -> {
                    part.append(c)
                    if (c == ']') {
                        if (next == ']') { part.append(next); i++ }
                        else bracket = false
                    }
                }
                c == '-' && next == '-' -> { lineComment = true; part.append(' '); i++ }
                c == '/' && next == '*' -> { blockComment = true; part.append(' '); i++ }
                c == 39.toChar() || c == '"' || c == 96.toChar() -> { quote = c; part.append(c) }
                c == '[' -> { bracket = true; part.append(c) }
                c == ';' -> flush()
                else -> part.append(c)
            }
            i++
        }
        flush()
        return result
    }
}

/** Helpful substitutions for early lessons, not a full T-SQL implementation. */
object TsqlLiteAdapter {
    fun prepare(input: String): String = input
        .replace(Regex("""(?im)^\s*GO\s*$"""), "")
        .replace(Regex("""(?im)^\s*USE\s+\[?\w+]?;?\s*$"""), "")

    fun convert(statement: String): String {
        var sql = statement
            .replace(Regex("""(?i)\bdbo\."""), "")
            .replace(Regex("""(?i)\bISNULL\s*\("""), "IFNULL(")
            .replace(Regex("""(?i)\bLEN\s*\("""), "LENGTH(")
            .replace(Regex("""(?i)\bGETDATE\s*\(\s*\)"""), "datetime('now')")
            .replace(Regex("""(?i)\bGETUTCDATE\s*\(\s*\)"""), "datetime('now')")
            .replace(Regex("""(?i)\bN'"""), "'")

        val top = Regex("""(?is)^\s*SELECT\s+(?:DISTINCT\s+)?TOP\s*\(?\s*(\d+)\s*\)?""").find(sql)
        if (top != null) {
            val number = top.groupValues[1]
            val withoutTop = top.value.replace(Regex("""(?i)TOP\s*\(?\s*\d+\s*\)?"""), "")
            sql = withoutTop + sql.substring(top.range.last + 1)
            if (!Regex("""(?i)\bLIMIT\b""").containsMatchIn(sql)) sql = sql.trimEnd() + " LIMIT " + number
        }
        return sql.trim()
    }
}

object SqlErrors {
    fun explain(message: String): String {
        val original = message.ifBlank { "Неизвестная ошибка SQL" }
        val lower = original.lowercase()
        val tip = when {
            "no such table" in lower -> "Таблица не найдена. Открой схему БД и проверь FROM и название таблицы."
            "no such column" in lower -> "Столбец не найден. Проверь имя поля и таблицу, из которой оно выбирается."
            "ambiguous column" in lower -> "Имя столбца неоднозначно. Укажи псевдоним таблицы: например, t.account_id."
            "syntax error" in lower -> "Синтаксическая ошибка. Проверь порядок SELECT → FROM → WHERE → GROUP BY → HAVING → ORDER BY."
            "unique constraint" in lower -> "Нарушена уникальность: строка с таким ключом или уникальным значением уже существует."
            "foreign key constraint" in lower -> "Нарушен внешний ключ: связанная запись отсутствует в родительской таблице."
            "check constraint" in lower -> "Нарушено ограничение CHECK: база не принимает указанное значение."
            "not null constraint" in lower -> "В обязательное поле нельзя записывать NULL."
            "readonly" in lower || "read-only" in lower -> "Это проверка задания: разрешены только запросы на чтение данных."
            "database is locked" in lower -> "База сейчас занята другой операцией. Повтори запрос."
            else -> "Проверь SQL и сообщение ниже. Здесь SQLite, а не Microsoft SQL Server."
        }
        return "$tip\n\nSQLite: $original"
    }
}
