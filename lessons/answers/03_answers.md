# Ответы к уроку 3

### 1. Общее количество сделок и комиссия

```sql
SELECT
    COUNT(*) AS trades_count,
    SUM(commission) AS commission_total
FROM trades;
```

### 2. BUY/SELL — количество и оборот

```sql
SELECT
    side,
    COUNT(*) AS trades_count,
    SUM(quantity * price) AS turnover
FROM trades
GROUP BY side;
```

### 3. Инструмент(ы) с максимальным оборотом

```sql
SELECT
    instrument_id,
    SUM(quantity * price) AS turnover
FROM trades
GROUP BY instrument_id
ORDER BY turnover DESC
LIMIT 1;
```

`LIMIT 1` берёт только самую первую строку уже отсортированного по убыванию результата —
то есть инструмент с максимальным оборотом. Если хочется увидеть все инструменты и сравнить
самим — уберите `LIMIT 1`.

### 4. Счета с суммарной комиссией больше 5000

```sql
SELECT
    account_id,
    SUM(commission) AS commission_total
FROM trades
GROUP BY account_id
HAVING SUM(commission) > 5000
ORDER BY commission_total DESC;
```

### 5. Средняя цена по каждому instrument_id

```sql
SELECT
    instrument_id,
    AVG(price) AS avg_price
FROM trades
GROUP BY instrument_id
ORDER BY avg_price DESC;
```

### 6. Условная позиция по account_id + instrument_id

```sql
SELECT
    account_id,
    instrument_id,
    SUM(
        CASE
            WHEN side = 'BUY' THEN quantity
            WHEN side = 'SELL' THEN -quantity
            ELSE 0
        END
    ) AS net_position
FROM trades
GROUP BY account_id, instrument_id
ORDER BY account_id, instrument_id;
```

### 7. Инструменты с менее чем 5 сделками

```sql
SELECT
    instrument_id,
    COUNT(*) AS trades_count
FROM trades
GROUP BY instrument_id
HAVING COUNT(*) < 5
ORDER BY trades_count;
```

`TEST0` не появится в этом результате (и вообще ни в одном запросе, который строится
`FROM trades ... GROUP BY instrument_id`), потому что `GROUP BY` группирует только те
строки, которые физически существуют в `trades`. У `TEST0` нет ни одной строки в `trades` —
значит, для него не формируется вообще никакой группы, даже "группы с нулём сделок". Чтобы
увидеть инструменты без единой сделки явно, нужен `LEFT JOIN` от `instruments` к `trades` —
это тема урока 4.
