# Урок 03. Агрегаты, GROUP BY, HAVING и средневзвешенная цена

**Цель:** переходить от отдельных строк к показателям по группам.

## Что считается агрегатом

`COUNT(*)` считает строки, `COUNT(column)` — значения не NULL. `SUM`, `AVG`, `MIN`, `MAX` работают по набору данных. Без `GROUP BY` результат — одна группа всех строк.

```sql
USE TradingTraining;
SELECT COUNT(*) AS trade_count,
       SUM(quantity * price) AS notional_total,
       SUM(commission) AS fees_total
FROM dbo.trades;

SELECT account_id, side,
       COUNT(*) AS trades_count,
       SUM(quantity * price) AS notional
FROM dbo.trades
GROUP BY account_id, side
ORDER BY account_id, side;
```

**Важная оговорка:** `SUM(quantity * price)` по разным инструментам/валютам — учебная арифметическая сумма, а не экономически корректный рублёвый оборот. Реальная модель должна учитывать единицу измерения, валюту цены, множитель инструмента и конвертацию по курсу.

## WHERE против HAVING

`WHERE` отбирает **исходные строки**, `HAVING` — **уже сформированные группы**.

```sql
SELECT instrument_id, COUNT(*) AS buy_count
FROM dbo.trades
WHERE side = 'BUY'
GROUP BY instrument_id
HAVING COUNT(*) > 5
ORDER BY buy_count DESC;
```

При группировке выбирай либо поля из `GROUP BY`, либо агрегаты. `SELECT instrument_id, price FROM ... GROUP BY instrument_id` в SQL Server вызовет ошибку — цена конкретной сделки не определена для группы.

## Условная агрегация

```sql
SELECT instrument_id,
       SUM(CASE WHEN side = 'BUY' THEN 1 ELSE 0 END) AS buys,
       SUM(CASE WHEN side = 'SELL' THEN 1 ELSE 0 END) AS sells
FROM dbo.trades
GROUP BY instrument_id
ORDER BY instrument_id;
```

`CASE` превращает условие в вычисляемую величину. Упрощённые `SUM(CASE ...)` не заменяют полноценную обработку состояния портфеля с учётом начальных позиций, корпоративных действий и валюты расчёта.

## AVG(price) против VWAP

```sql
SELECT instrument_id,
       AVG(price) AS simple_avg_price,
       SUM(CAST(quantity AS DECIMAL(19,4)) * price)
          / NULLIF(SUM(CAST(quantity AS DECIMAL(19,4))), 0) AS weighted_avg_price
FROM dbo.trades
WHERE instrument_id = 3
GROUP BY instrument_id;
```

Простое среднее взвешивает **каждую сделку одинаково**; `SUM(quantity * price) / SUM(quantity)` взвешивает количество. Для реального VWAP сначала определи, к какой стороне, дате, торговому режиму и единице измерения он относится.

## Задания

1. Посчитай сделки BUY и SELL по каждому счёту одной строкой через CASE.
2. Найди инструменты с более чем 15 сделками.
3. Посчитай комиссию и количество сделок по дням; отсортируй по дате.
4. Объясни различие `COUNT(*)` и `COUNT(t.trade_id)` после LEFT JOIN.

**Контроль:** уметь формулировать зерно агрегации: «одна строка результата = один инструмент за одну дату».
