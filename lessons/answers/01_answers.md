# Ответы к уроку 1

### 1. Первые 5 строк daily_prices

```sql
SELECT * FROM daily_prices LIMIT 5;
```

### 2. UNIQUE-колонка в instruments

```sql
.schema instruments
```

В выводе видно `ticker TEXT NOT NULL UNIQUE` — значит, помимо `PRIMARY KEY` на
`instrument_id`, отдельно защищён от повторов ещё и `ticker`.

### 3. instrument_id для TEST0

```sql
SELECT instrument_id, ticker, name FROM instruments WHERE ticker = 'TEST0';
```

Вернёт `instrument_id = 12` (при условии, что база собрана из штатных CSV без изменений).
Это специально добавленный инструмент без единой сделки — понадобится в уроке 4.

### 4. Строки liquidity за первую дату

Самый простой способ на уровне знаний урока 1 — использовать `LIMIT`, зная, что данные в
таблице упорядочены по дате при загрузке. Это не строгий и не универсальный способ (правильный
инструмент для гарантии порядка — `ORDER BY`, который вы изучите в уроке 2), но для
знакомства с данными достаточно:

```sql
SELECT * FROM liquidity LIMIT 11;
```

(11 — потому что в базе 11 активных инструментов, и в один торговый день у каждого из них
по одной строке в `liquidity`.) Более надёжный способ с явной сортировкой:

```sql
SELECT * FROM liquidity ORDER BY price_date LIMIT 11;
```

Это забегание вперёд, в урок 2 — но полезно увидеть эту команду уже сейчас.

### 5. Таблицы и внешние ключи на instruments

Всего таблиц в базе: 5 — `instruments`, `accounts`, `trades`, `daily_prices`, `liquidity`.

Ссылаются на `instruments` через `FOREIGN KEY (instrument_id)`:

- `trades`
- `daily_prices`
- `liquidity`

Таблица `accounts` на `instruments` не ссылается вообще — это независимый справочник счетов.
