# Ответы к уроку 4

### 1. Сделки с тикером и названием счёта

```sql
SELECT
    t.trade_id,
    t.trade_date,
    i.ticker,
    a.account_name,
    t.side,
    t.quantity,
    t.price
FROM trades AS t
JOIN instruments AS i ON i.instrument_id = t.instrument_id
JOIN accounts    AS a ON a.account_id    = t.account_id
ORDER BY t.trade_date;
```

### 2. Оборот по тикеру

```sql
SELECT
    i.ticker,
    SUM(t.quantity * t.price) AS turnover
FROM trades AS t
JOIN instruments AS i ON i.instrument_id = t.instrument_id
GROUP BY i.ticker
ORDER BY turnover DESC;
```

Обратите внимание: `JOIN` выполняется раньше `GROUP BY` — сначала строки "расширяются"
тикером, и только потом группируются уже по этому тикеру.

### 3. Инструменты без сделок, с asset_class

```sql
SELECT i.ticker, i.asset_class
FROM instruments AS i
LEFT JOIN trades AS t ON t.instrument_id = i.instrument_id
WHERE t.trade_id IS NULL;
```

Ожидаемый результат — `TEST0`, `asset_class = 'equity'` (при условии, что база собрана из
штатных CSV без изменений).

### 4. Счета без сделок

```sql
SELECT a.account_name, a.strategy
FROM accounts AS a
LEFT JOIN trades AS t ON t.account_id = a.account_id
WHERE t.trade_id IS NULL;
```

Ожидаемый результат — `'ACC-DORMANT'`.

### 5. Отчёт по счетам, включая ACC-DORMANT с нулём сделок

```sql
SELECT
    a.account_name,
    COUNT(t.trade_id) AS trades_count,
    COALESCE(SUM(t.commission), 0) AS commission_total
FROM accounts AS a
LEFT JOIN trades AS t ON t.account_id = a.account_id
GROUP BY a.account_name
ORDER BY trades_count DESC;
```

Почему `COUNT(t.trade_id)`, а не `COUNT(*)`: `COUNT(*)` считает вообще все строки в группе,
включая ту единственную "пустую" строку с `NULL`-значениями, которую даёт `LEFT JOIN` для
`'ACC-DORMANT'` — и ошибочно показал бы `1` вместо `0`. `COUNT(t.trade_id)` считает только
**не-NULL** значения конкретной колонки — а у `'ACC-DORMANT'` `t.trade_id` как раз и есть
`NULL`, поэтому такая сделка не засчитывается, и результат корректно равен `0`.

`SUM(t.commission)` для группы без единой сделки вернула бы `NULL` (а не `0`) — поэтому
использован `COALESCE(..., 0)`, который подставляет `0` вместо `NULL`.

### 6. Сделки по инструментам fx

```sql
SELECT
    t.trade_id,
    i.ticker,
    t.side,
    t.quantity,
    t.price
FROM trades AS t
JOIN instruments AS i ON i.instrument_id = t.instrument_id
WHERE i.asset_class = 'fx';
```

### 7. Цена сделки против цены закрытия того же дня (SBER)

```sql
SELECT
    t.trade_id,
    t.trade_date,
    t.price AS trade_price,
    p.close_price
FROM trades AS t
JOIN instruments AS i ON i.instrument_id = t.instrument_id
JOIN daily_prices AS p
    ON p.instrument_id = t.instrument_id
   AND p.price_date    = t.trade_date
WHERE i.ticker = 'SBER'
ORDER BY t.trade_date;
```

Возвращает ровно по одной строке на каждую сделку по `SBER` (21 сделка) — `JOIN` по двум
колонкам сразу (`instrument_id` и дате) не даёт fan-out, в отличие от соединения только по
`instrument_id` (см. раздел урока про `JOIN` по нескольким колонкам). `trade_price` и
`close_price` для одной и той же даты обычно немного отличаются — сделка совершается по цене
конкретной сделки, а `close_price` — это цена закрытия торгового дня в целом, это разные вещи,
даже если дата совпадает.
