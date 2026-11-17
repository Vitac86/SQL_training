# Урок 02. SELECT, WHERE, TOP, ORDER BY и NULL

**Цель:** получать нужные строки в T-SQL и понимать логическую обработку запроса.

## Логический порядок запроса

Упрощённо: `FROM / JOIN` → `WHERE` → `GROUP BY` → `HAVING` → `SELECT` → `DISTINCT` → `ORDER BY` → `TOP / OFFSET`. Это **логический** порядок; физический план оптимизатор может менять, сохраняя семантику.

```sql
USE TradingTraining;
SELECT TOP (10) trade_id, trade_date, quantity, price,
       quantity * price AS notional
FROM dbo.trades
WHERE side = 'BUY' AND quantity >= 100
ORDER BY trade_date DESC, trade_id DESC;
```

В SQL Server **`TOP (10)` вместо SQLite `LIMIT 10`**. Без `ORDER BY` выбор «первых десяти» не определён.

## Операторы

```sql
SELECT ticker, asset_class FROM dbo.instruments
WHERE asset_class IN ('equity', 'fx')
ORDER BY ticker;

SELECT trade_id, price FROM dbo.trades
WHERE price BETWEEN 100 AND 1000
ORDER BY price;

SELECT ticker FROM dbo.instruments
WHERE ticker LIKE 'S%';

SELECT TOP (5) trade_id, price FROM dbo.trades
ORDER BY price DESC, trade_id;
```

`BETWEEN` включает оба края. `AND` имеет более высокий приоритет, чем `OR` — для ясности используй скобки. `LIKE` зависит от **collation** (в частности, регистрозависимости). `DISTINCT` удаляет дубли **комбинаций выбранных полей**, а не дубли исходных записей.

## NULL — «значение неизвестно»

`NULL` не равно 0, пустой строке и самому себе в обычном сравнении. Логика SQL трёхзначная: **TRUE, FALSE, UNKNOWN**. Для проверки отсутствия значения:

```sql
SELECT CAST(NULL AS INT) AS a WHERE NULL = NULL; -- пустой результат
SELECT CAST(NULL AS INT) AS a WHERE NULL IS NULL; -- одна строка
```

`WHERE` оставляет только TRUE. Это особенно важно для `NOT IN`, если подзапрос может вернуть NULL (подробнее в уроке 06). Для замены значения используй `COALESCE(column, replacement)`, но не подменяй NULL нулём без бизнес-смысла.

## Страницы результата

```sql
SELECT trade_id, trade_date
FROM dbo.trades
ORDER BY trade_date, trade_id
OFFSET 10 ROWS FETCH NEXT 10 ROWS ONLY;
```

`OFFSET` требует `ORDER BY`; для стабильной пагинации он должен разрешать равенства — поэтому добавлен `trade_id`.

## Задания

1. Найди 10 самых дорогих сделок BUY по `price`; сортируй также по `trade_id`.
2. Выведи уникальные `asset_class` и уникальные `strategy` из соответствующих таблиц.
3. Выведи инструменты, чей тикер начинается на `S`, и объясни влияние collation.
4. Почему `WHERE price <> NULL` не находит строки? Как правильно искать непустые значения?
5. Сделай вторую страницу сделок (11–20) по `trade_id`.

**Самопроверка:** если меняешь `TOP`, без сортировки нельзя утверждать, какие именно сделки попадут в результат.
