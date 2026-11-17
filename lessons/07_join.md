# Урок 07. JOIN: как объединять несколько таблиц

**Знакомо:** `trades` содержит номера счёта и инструмента (урок 04), а названия лежат в `accounts` и `instruments`. В уроке 06 мы считали сделки по числовым id. **Теперь** научимся показывать рядом нормальные названия.

## 1. Вспомним ручную работу

Мы знаем, что у первой сделки `instrument_id=3`. Раньше нужно было два запроса:

```sql
USE TradingTraining;
SELECT trade_id, instrument_id FROM dbo.trades WHERE trade_id = 1;
SELECT instrument_id, ticker FROM dbo.instruments WHERE instrument_id = 3;
```

Нам хочется увидеть всё **сразу**. Для этого служит `JOIN` («соедини»).

## 2. Первый JOIN, очень медленно

```sql
SELECT
    t.trade_id,
    t.instrument_id,
    i.ticker
FROM dbo.trades AS t
JOIN dbo.instruments AS i
    ON t.instrument_id = i.instrument_id
WHERE t.trade_id = 1;
```

Результат — одна сделка с тикером `LKOH`.

Разбор:
- `FROM dbo.trades AS t` — берём сделки и коротко называем таблицу `t`.
- `JOIN dbo.instruments AS i` — присоединяем инструменты, называем их `i`.
- `ON t.instrument_id = i.instrument_id` — **правило сопоставления**: соединяй там, где совпадают номера инструментов.
- `t.trade_id` — берём поле `trade_id` из `t`, а `i.ticker` — поле `ticker` из `i`.
- `WHERE t.trade_id = 1` — только первая сделка.

Здесь `JOIN` без слова `INNER` означает **INNER JOIN**. Он оставляет строки, для которых есть соответствие с другой таблицей.

**Не путай**: FK (урок 04) — правило корректности **хранения данных**. JOIN — способ **получить результат из нескольких таблиц**. Один не заменяет другой.

## 3. Два JOIN в одном запросе

```sql
SELECT TOP (10)
    t.trade_id,
    a.account_name,
    i.ticker,
    t.side,
    t.quantity,
    t.price
FROM dbo.trades AS t
JOIN dbo.accounts AS a
    ON t.account_id = a.account_id
JOIN dbo.instruments AS i
    ON t.instrument_id = i.instrument_id
ORDER BY t.trade_id;
```

Теперь напротив каждой сделки отображаются **и счёт, и тикер**. Мы не записали названия внутрь `trades`: они подтягиваются при чтении.

## 4. INNER JOIN и LEFT JOIN — разница на «пустом» инструменте

В справочнике есть специально созданный `TEST0`, у которого **нет сделок**.

```sql
SELECT i.ticker, t.trade_id
FROM dbo.instruments AS i
JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
WHERE i.ticker = 'TEST0';
```

Результат пустой: INNER JOIN оставляет только найденные пары.

```sql
SELECT i.ticker, t.trade_id
FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
WHERE i.ticker = 'TEST0';
```

Результат — `TEST0` и `NULL` в `trade_id`. **LEFT JOIN сохраняет все строки таблицы слева**, даже когда справа не нашлось ни одной. Отсутствующие значения справа заполнены NULL.

## 5. Почему COUNT(*) здесь обманывает

```sql
SELECT i.ticker,
       COUNT(*) AS wrong_count,
       COUNT(t.trade_id) AS correct_count
FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
WHERE i.ticker = 'TEST0'
GROUP BY i.ticker;
```

Получишь `wrong_count = 1`, `correct_count = 0`. Почему? LEFT JOIN выдал **одну строку** с пустыми полями сделок. `COUNT(*)` её посчитал, а `COUNT(t.trade_id)` игнорирует NULL. Для числа **найденных сделок** нам нужен `COUNT(t.trade_id)`.

## 6. Условие JOIN иногда состоит из двух полей

`daily_prices` хранит цену инструмента **на конкретную дату**. Если соединить только по инструменту, сделка размножится на множество дат:

```sql
SELECT TOP (10) t.trade_id, t.price, p.close_price
FROM dbo.trades AS t
LEFT JOIN dbo.daily_prices AS p
    ON p.instrument_id = t.instrument_id
   AND p.price_date = t.trade_date
ORDER BY t.trade_id;
```

Условие `ON` использует **и инструмент, и дату**, поэтому подходящая дневная цена не путается с другими датами. Не всегда достаточно одного поля.

## 7. Важный порядок мышления

Прежде чем писать JOIN, ответь на три вопроса: «Что означает одна строка итогового отчёта? По каким полям совпадают записи? Может ли справа найтись несколько строк на одну слева?» Последний случай способен «размножить» сделки и ошибочно увеличить суммарную комиссию.

## Задания

1. Покажи 20 сделок с именем счёта и тикером.
2. Найди все инструменты без сделок через LEFT JOIN и `WHERE t.trade_id IS NULL`.
3. Найди все счета без сделок (подсказка — `ACC-DORMANT`).
4. Посчитай сделки по тикерам, **включая** `TEST0` с нулём.
5. Объясни, почему для дневной цены недостаточно только `instrument_id` в условии JOIN.

**Самопроверка:** у `TEST0` — 0 сделок, у `ACC-DORMANT` — 0; всегда считай `COUNT(t.trade_id)` после LEFT JOIN, если ищешь число фактически найденных сделок.

**Дальше:** [урок 08](08_изменение_данных.md) — будем аккуратно менять данные и научимся отменять учебные изменения.
