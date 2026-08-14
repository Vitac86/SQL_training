# Ответы к уроку 2

### 1. SELL, quantity > 100, сортировка по цене убыв.

```sql
SELECT *
FROM trades
WHERE side = 'SELL' AND quantity > 100
ORDER BY price DESC;
```

### 2. Уникальные валюты

```sql
SELECT DISTINCT currency FROM instruments;
```

В учебных данных все инструменты котируются в `RUB`, поэтому результат — одна строка.
Это специально простой, но реалистичный случай: не все запросы должны возвращать много строк.

### 3. equity или bond через IN

```sql
SELECT * FROM instruments WHERE asset_class IN ('equity', 'bond');
```

### 4. Топ-5 сделок по обороту

```sql
SELECT
    trade_id,
    instrument_id,
    quantity,
    price,
    quantity * price AS turnover
FROM trades
ORDER BY turnover DESC
LIMIT 5;
```

### 5. Тикеры, заканчивающиеся на 'M'

```sql
SELECT * FROM instruments WHERE ticker LIKE '%M';
```

В учебных данных под этот шаблон попадают `USD000UTSTOM` и `CNYRUB_TOM` — оба тикера
заканчиваются на `TOM` (расчёты "завтра", tomorrow), поэтому последний символ — латинская
`M`. Это удобный пример того, что `LIKE` ищет буквально по символам, не зная о финансовом
смысле тикера.

### 6. Сделки за февраль 2024

```sql
SELECT *
FROM trades
WHERE trade_date BETWEEN '2024-02-01' AND '2024-02-29';
```

Формат `YYYY-MM-DD` устроен так, что обычное текстовое сравнение (`<`, `>`, `BETWEEN`) даёт
тот же результат, что и сравнение дат — это одна из причин, почему даты принято хранить
именно в таком формате (ISO 8601).

### 7. 10 BUY-сделок с наименьшей комиссией

```sql
SELECT *
FROM trades
WHERE side = 'BUY'
ORDER BY commission ASC
LIMIT 10;
```

### 8. quantity * price без округления и с ROUND

```sql
SELECT
    trade_id,
    quantity * price AS turnover_raw,
    ROUND(quantity * price, 2) AS turnover_rounded
FROM trades
WHERE trade_id = 4;
```

Реальный результат: `turnover_raw = 3602.0000000000005`, `turnover_rounded = 3602.0`. Оба
числа математически означают одно и то же (`50 * 72.04 = 3602`), но `price` хранится как
`REAL` (число с плавающей точкой), а такие числа не всегда можно точно представить в двоичном
виде — отсюда лишние незначащие разряды в "сыром" значении. `ROUND(..., 2)` убирает этот шум
и оставляет только значимые для рубля два знака после запятой.
