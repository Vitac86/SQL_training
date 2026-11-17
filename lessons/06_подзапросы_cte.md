# Урок 06. Подзапросы, EXISTS, CTE и APPLY

**Цель:** комбинировать условия из связанных таблиц без потери логики.

## Скалярный подзапрос

```sql
USE TradingTraining;
SELECT trade_id, price
FROM dbo.trades
WHERE price > (SELECT AVG(price) FROM dbo.trades)
ORDER BY price DESC;
```

Внутренний `SELECT` должен вернуть **одно значение** в контексте сравнения `price > (...)`. Если вернёт несколько строк — SQL Server выдаст ошибку.

## EXISTS и NOT EXISTS

```sql
SELECT a.account_name
FROM dbo.accounts AS a
WHERE EXISTS (
  SELECT 1 FROM dbo.trades AS t
  WHERE t.account_id = a.account_id AND t.side = 'SELL'
);

SELECT a.account_name
FROM dbo.accounts AS a
WHERE NOT EXISTS (
  SELECT 1 FROM dbo.trades AS t WHERE t.account_id = a.account_id
);
```

Подзапрос с внешней ссылкой (`a.account_id`) — **коррелированный**. `EXISTS` отвечает на вопрос «есть хотя бы одна строка?»; в SELECT внутри можно написать `1`, потому что важен только факт существования. `NOT EXISTS` обычно предпочтительнее `NOT IN`, если в возвращаемом наборе потенциально могут быть NULL: `NOT IN` с NULL может дать UNKNOWN для всех сравнений.

## CTE: именованный шаг запроса

```sql
WITH trades_by_account AS (
    SELECT account_id,
           COUNT(*) AS trade_count,
           SUM(quantity * price) AS raw_notional
    FROM dbo.trades
    GROUP BY account_id
)
SELECT a.account_name, COALESCE(x.trade_count, 0) AS trade_count
FROM dbo.accounts AS a
LEFT JOIN trades_by_account AS x ON x.account_id = a.account_id
ORDER BY a.account_name;
```

`WITH ... AS` определяет **Common Table Expression**: логическое именованное выражение для **одного следующего оператора**. Не предполагай, что оно физически материализовано и вычисляется лишь один раз; это решает оптимизатор. Для многошаговой обработки можно использовать временную таблицу `#temp`.

## APPLY: связанный табличный подзапрос

```sql
SELECT a.account_name, x.trade_id, x.trade_date, x.price
FROM dbo.accounts AS a
OUTER APPLY (
    SELECT TOP (1) t.trade_id, t.trade_date, t.price
    FROM dbo.trades AS t
    WHERE t.account_id = a.account_id
    ORDER BY t.trade_date DESC, t.trade_id DESC
) AS x;
```

`OUTER APPLY` сохранит даже счёт без сделок (`ACC-DORMANT`), а `CROSS APPLY` исключит его, если правая часть не вернула строк.

## Задания

1. Найди инструменты, для которых вообще есть сделки BUY, через EXISTS.
2. Найди все инструменты без сделок через NOT EXISTS и сверяй с результатом LEFT JOIN.
3. С помощью CTE найди счета, у которых сделок больше среднего числа сделок **на активный счёт**. Затем объясни, как изменится среднее, если учитывать пустые счета.
4. Для каждого тикера выведи одну последнюю сделку через OUTER APPLY, включая `TEST0`.

**Контроль:** можешь объяснить, когда нужен `JOIN` (строки справа), а когда `EXISTS` (только условие наличия).
