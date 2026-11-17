# Урок 04. JOIN: соединения и контроль потери строк

**Цель:** видеть связи между таблицами и понимать, когда соединение меняет количество строк.

## INNER JOIN

```sql
USE TradingTraining;
SELECT TOP (10)
       t.trade_id, t.trade_date, a.account_name, i.ticker,
       t.side, t.quantity, t.price
FROM dbo.trades AS t
JOIN dbo.accounts AS a ON a.account_id = t.account_id
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
ORDER BY t.trade_id;
```

`INNER JOIN` возвращает строки с совпадениями с обеих сторон. Это **операция чтения**; FK — ограничение **записи**. Если правая сторона не уникальна по полю соединения, одна строка слева может размножиться — проблема **fan-out**, способная завысить суммы.

## LEFT JOIN и отсутствующие сделки

```sql
SELECT i.ticker, COUNT(t.trade_id) AS trades_count
FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
GROUP BY i.ticker
ORDER BY i.ticker;
```

Даже инструмент `TEST0` остаётся в выдаче. `COUNT(*)` для него дал бы **1** из-за NULL-дополненной строки, а `COUNT(t.trade_id)` — правильный **0**.

Типовая ловушка:

```sql
-- Фильтр ниже УДАЛИТ NULL-строки без совпадения:
SELECT i.ticker, t.side FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
WHERE t.side = 'BUY';

-- Фильтр в ON сохранит все инструменты:
SELECT i.ticker, t.side FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t
  ON t.instrument_id = i.instrument_id AND t.side = 'BUY';
```

Это **разные задачи**, а не синтаксический стиль.

## JOIN по составному ключу

Цену закрытия нужно сопоставлять **по инструменту и дате**; соединение только по инструменту создаст много ложных комбинаций:

```sql
SELECT TOP (10) t.trade_id, i.ticker, t.price, p.close_price
FROM dbo.trades AS t
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
LEFT JOIN dbo.daily_prices AS p
  ON p.instrument_id = t.instrument_id AND p.price_date = t.trade_date
ORDER BY t.trade_id;
```

**RIGHT JOIN** эквивалентен перестановке сторон LEFT JOIN, **FULL OUTER JOIN** сохраняет несовпадения обеих сторон; **CROSS JOIN** делает декартово произведение и быстро увеличивает объём результата.

## Антисоединение

```sql
SELECT a.account_name
FROM dbo.accounts AS a
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.trades AS t WHERE t.account_id = a.account_id
);
```

Результат содержит `ACC-DORMANT`. Во многих задачах `NOT EXISTS` проще и безопаснее `NOT IN` при потенциальных NULL.

## Задания

1. Найди счета без сделок через LEFT JOIN и `IS NULL`, затем через NOT EXISTS.
2. Получи торговый оборот по `asset_class`; объясни, почему его нельзя автоматически считать рублёвым.
3. Проверь, что число строк `trades JOIN instruments JOIN accounts` равно 230 (ключи обеспечивают допустимые ссылки).
4. Покажи ошибку при JOIN `daily_prices` только по `instrument_id`, сравнив `COUNT(*)` до и после.

**Контроль:** всегда формулируй ключ соединения и ожидаемую кратность строк до написания отчёта.
