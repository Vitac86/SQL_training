# Урок 2. SELECT, WHERE, ORDER BY

Подключитесь к базе, если ещё не подключены:

```cmd
sqlite3 trading_training.db
.headers on
.mode column
```

## SELECT, DISTINCT, AS

Базовый `SELECT` вы уже видели в уроке 1:

```sql
SELECT ticker, name, asset_class FROM instruments;
```

`DISTINCT` убирает повторяющиеся строки в результате:

```sql
SELECT DISTINCT asset_class FROM instruments;
```

Это покажет уникальный список классов активов (`equity`, `fx`, `bond`, `money_market`) —
без повторов, даже если в таблице несколько инструментов одного класса.

`AS` даёт колонке (или вычисляемому значению) другое имя в результате — это называется
"псевдоним" (alias):

```sql
SELECT ticker AS symbol, name AS instrument_name FROM instruments;
```

### Вычисляемые колонки

`SELECT` не обязан выводить только "сырые" колонки — можно сразу вычислять новые значения:

```sql
SELECT
    trade_id,
    ticker,
    quantity,
    price,
    quantity * price AS turnover
FROM trades
JOIN instruments ON instruments.instrument_id = trades.instrument_id
LIMIT 10;
```

(`JOIN` мы разберём подробно в уроке 4 — здесь он использован только для того, чтобы сразу
показать тикер рядом с оборотом; сама механика `JOIN` пока не важна.)

`turnover` (оборот) — это классический пример вычисляемой колонки в торговых данных:
количество бумаг, умноженное на цену сделки.

## WHERE и операторы сравнения

```sql
SELECT * FROM trades WHERE side = 'BUY';
SELECT * FROM trades WHERE quantity <> 100;
SELECT * FROM trades WHERE price > 1000;
SELECT * FROM trades WHERE price < 200;
SELECT * FROM trades WHERE quantity >= 500;
SELECT * FROM trades WHERE quantity <= 10;
```

`=` — равно, `<>` — не равно (в SQLite также работает `!=`), `>`, `<`, `>=`, `<=` — как в
математике.

## AND, OR, NOT

```sql
SELECT * FROM trades WHERE side = 'BUY' AND quantity > 100;

SELECT * FROM trades WHERE side = 'BUY' OR side = 'SELL';

SELECT * FROM trades WHERE NOT side = 'BUY';
```

`AND` требует выполнения **обоих** условий одновременно, `OR` — хотя бы одного, `NOT`
инвертирует условие.

## IN и BETWEEN

```sql
SELECT * FROM instruments WHERE asset_class IN ('fx', 'bond');

SELECT * FROM trades WHERE price BETWEEN 100 AND 300;
```

`IN` — короткая запись для "равно одному из перечисленных значений" (заменяет длинную цепочку
`OR`). `BETWEEN a AND b` включает обе границы (`a` и `b` тоже подходят).

## LIKE

```sql
SELECT * FROM instruments WHERE ticker LIKE 'S%';

SELECT * FROM instruments WHERE name LIKE '%Банк%';
```

`%` — любое количество любых символов (в том числе ноль). `LIKE 'S%'` находит тикеры,
начинающиеся на `S`. `LIKE '%Банк%'` находит текст, где `'Банк'` встречается где угодно
внутри строки.

## IS NULL и IS NOT NULL

В наших учебных таблицах специально нет "пустых" (`NULL`) значений в ключевых полях —
благодаря `NOT NULL` из урока 0. Но важно знать: `NULL` нельзя сравнивать через `= NULL` —
это никогда не сработает, даже для по-настоящему пустых значений. Правильный синтаксис:

```sql
SELECT * FROM trades WHERE commission IS NOT NULL;

SELECT * FROM trades WHERE commission IS NULL;
```

Второй запрос вернёт пустой результат — в `trades.commission` нет `NULL`-значений, потому
что колонка объявлена как `NOT NULL`. Это ожидаемо и является хорошей проверкой того, что вы
верно понимаете разницу между `= NULL` (никогда не работает) и `IS NULL` (правильный способ).

## ORDER BY, ASC, DESC

```sql
SELECT * FROM trades ORDER BY price;

SELECT * FROM trades ORDER BY price DESC;

SELECT * FROM trades ORDER BY trade_date ASC, price DESC;
```

По умолчанию `ORDER BY` сортирует по возрастанию (`ASC` можно не писать явно). `DESC` —
по убыванию. Можно сортировать по нескольким колонкам сразу: сначала по первой, а при
совпадении значений — по второй.

## LIMIT

```sql
SELECT * FROM trades ORDER BY price DESC LIMIT 5;
```

Это классический паттерн "top N" — здесь: 5 самых дорогих по цене сделок.

## Самостоятельные задания

Решений здесь нет — они в `lessons/answers/02_answers.md`.

1. Выведите все сделки типа `SELL` с количеством больше 100, отсортированные по цене по
   убыванию.
2. Выведите список уникальных валют (`currency`) из таблицы `instruments`.
3. Выведите все инструменты, чей `asset_class` равен `'equity'` или `'bond'`, используя `IN`.
4. Выведите 5 самых больших по обороту сделок (оборот = `quantity * price`), используя
   вычисляемую колонку с `AS` и `ORDER BY`.
5. Выведите все инструменты, тикер которых заканчивается на `'M'` (используйте `LIKE`).
6. Выведите все сделки за `trade_date` между `'2024-02-01'` и `'2024-02-29'` включительно
   (используйте `BETWEEN`; помните, что даты в этой базе хранятся как текст в формате
   `YYYY-MM-DD`, поэтому текстовое сравнение работает корректно).
7. Выведите 10 сделок с наименьшей комиссией, но только для `side = 'BUY'`.

## Что дальше

В уроке 3 (`lessons/03_агрегаты_group_by.md`) вы научитесь считать агрегированные
показатели — суммарный оборот, количество сделок, среднюю цену — по группам, например по
каждому счёту или инструменту отдельно.
