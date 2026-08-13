# Урок 3. Агрегаты и GROUP BY

```cmd
sqlite3 trading_training.db
.headers on
.mode column
```

## Агрегатные функции без GROUP BY

Агрегатная функция превращает **много строк в одно число**.

```sql
SELECT COUNT(*) FROM trades;

SELECT SUM(quantity * price) AS total_turnover FROM trades;

SELECT AVG(price) AS avg_price FROM trades;

SELECT MIN(price) AS min_price, MAX(price) AS max_price FROM trades;
```

- `COUNT(*)` — сколько всего строк (сделок).
- `SUM(...)` — сумма значений (общий оборот).
- `AVG(...)` — среднее значение.
- `MIN(...)` / `MAX(...)` — минимум и максимум.

Без `GROUP BY` агрегат считается **по всей таблице целиком** — вы получаете ровно одну
строку результата.

## GROUP BY — тот же агрегат, но по группам

Реальный вопрос трейдера обычно звучит не "сколько всего сделок", а "сколько сделок **по
каждому инструменту**". Вот тут и нужен `GROUP BY`:

```sql
SELECT
    instrument_id,
    COUNT(*) AS trades_count
FROM trades
GROUP BY instrument_id
ORDER BY trades_count DESC;
```

Как это работает: SQLite сначала мысленно "раскладывает" все строки `trades` по стопкам —
в одной стопке все строки с `instrument_id = 1`, в другой — все с `instrument_id = 2`, и
так далее. Затем `COUNT(*)` считается **отдельно для каждой стопки**, а не для таблицы
целиком.

Оборот по каждому инструменту:

```sql
SELECT
    instrument_id,
    SUM(quantity * price) AS turnover
FROM trades
GROUP BY instrument_id
ORDER BY turnover DESC;
```

Покупки и продажи отдельно:

```sql
SELECT
    side,
    COUNT(*) AS trades_count,
    SUM(quantity * price) AS turnover
FROM trades
GROUP BY side;
```

Оборот по каждому счёту:

```sql
SELECT
    account_id,
    SUM(quantity * price) AS turnover,
    SUM(commission) AS commission_total
FROM trades
GROUP BY account_id
ORDER BY turnover DESC;
```

Обратите внимание: колонка `account_id` показывает только число, а не название счёта — мы
ещё не соединили `trades` со справочником `accounts`. Это сделаем в уроке 4 через `JOIN`.
Здесь важно другое: сам механизм группировки уже работает и без `JOIN`.

### Правило, которое часто ломает новичков

В `SELECT` вместе с `GROUP BY` можно указывать **только** те колонки, по которым
группируем, и агрегатные функции. Так работать нельзя по смыслу:

```sql
SELECT instrument_id, price FROM trades GROUP BY instrument_id;
```

SQLite (в отличие от некоторых строгих СУБД) не всегда выдаст здесь явную ошибку, но
результат будет **непредсказуемым** — какая именно `price` попадёт в строку, если
инструменту соответствует 20 разных сделок с разными ценами? Правильный вопрос — не
"какая цена", а "какая **сумма/среднее/минимум** цен". Формулируйте вопрос через агрегатную
функцию явно: `AVG(price)`, `MIN(price)`, `MAX(price)`.

## HAVING — фильтр после группировки

`WHERE` фильтрует строки **до** группировки. `HAVING` фильтрует уже готовые группы —
**после** того, как агрегаты посчитаны.

```sql
SELECT
    instrument_id,
    COUNT(*) AS trades_count
FROM trades
GROUP BY instrument_id
HAVING COUNT(*) > 15;
```

Этот запрос оставляет только те инструменты, у которых **больше 15 сделок**. Написать
`WHERE COUNT(*) > 15` нельзя — на момент вычисления `WHERE` агрегат `COUNT(*)` ещё не
существует, он появляется только после группировки.

## CASE — условная логика внутри запроса

`CASE` работает как if/elif/else внутри `SELECT`:

```sql
SELECT
    trade_id,
    side,
    quantity,
    CASE
        WHEN side = 'BUY' THEN quantity
        WHEN side = 'SELL' THEN -quantity
        ELSE 0
    END AS signed_quantity
FROM trades
LIMIT 10;
```

Здесь мы превращаем `BUY`/`SELL` в положительное/отрицательное число — классический приём
для расчёта **условной позиции**: если сложить `signed_quantity` по всем сделкам одного
инструмента на одном счёте, получится примерная текущая позиция (без учёта начального
остатка, комиссий и прочих тонкостей реального учёта).

```sql
SELECT
    instrument_id,
    SUM(
        CASE
            WHEN side = 'BUY' THEN quantity
            WHEN side = 'SELL' THEN -quantity
            ELSE 0
        END
    ) AS net_position
FROM trades
GROUP BY instrument_id
ORDER BY net_position DESC;
```

## Самостоятельные задания

Решений здесь нет — они в `lessons/answers/03_answers.md`.

1. Посчитайте общее количество сделок и суммарную комиссию по всей таблице `trades` (без
   группировки, одной строкой).
2. Посчитайте количество сделок и суммарный оборот отдельно для `side = 'BUY'` и
   `side = 'SELL'`.
3. Найдите инструмент (или инструменты) с максимальным суммарным оборотом
   (`SUM(quantity * price)`), сгруппировав по `instrument_id`.
4. Используя `HAVING`, найдите счета (`account_id`), у которых суммарная комиссия
   (`SUM(commission)`) больше `5000`.
5. Посчитайте среднюю цену сделки (`AVG(price)`) отдельно по каждому `instrument_id`,
   отсортировав результат по убыванию средней цены.
6. С помощью `CASE` посчитайте условную позицию (`net_position`, как в примере выше) для
   каждой пары `account_id` + `instrument_id` (подсказка: `GROUP BY account_id, instrument_id`
   — можно группировать сразу по нескольким колонкам).
7. Найдите инструменты, у которых было совершено меньше 5 сделок (используйте `HAVING`, и
   обратите внимание, что инструмент `TEST0` вообще не встретится в этом запросе — почему?).

## Что дальше

В уроке 4 (`lessons/04_join.md`) вы наконец соедините `trades` с `instruments` и `accounts`
по-настоящему — через `JOIN` — и увидите тикеры и названия счетов прямо в результатах
запросов, а не просто числовые ID.
