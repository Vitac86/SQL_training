-- answers.sql — решения 01–49. №50 — открытый итоговый проект, см. урок 17.
-- Выполняй отдельные запросы в SSMS после загрузки базы. DML всегда откатывается.
USE TradingTraining;
GO

-- 01
SELECT instrument_id, ticker, [name]
FROM dbo.instruments WHERE currency = 'RUB' ORDER BY ticker;

-- 02
SELECT TOP (5) trade_id, price
FROM dbo.trades ORDER BY price DESC, trade_id;

-- 03
SELECT DISTINCT strategy FROM dbo.accounts ORDER BY strategy;

-- 04
SELECT trade_id, trade_date, quantity
FROM dbo.trades WHERE side = 'BUY' AND quantity >= 1000 ORDER BY trade_id;

-- 05
SELECT ticker FROM dbo.instruments WHERE ticker LIKE 'S%' ORDER BY ticker;

-- 06
SELECT trade_id FROM dbo.trades
ORDER BY trade_id OFFSET 10 ROWS FETCH NEXT 10 ROWS ONLY;

-- 07
SELECT side, COUNT(*) AS trades_count FROM dbo.trades GROUP BY side ORDER BY side;

-- 08
SELECT a.account_name, COUNT(t.trade_id) AS trades_count
FROM dbo.accounts AS a
LEFT JOIN dbo.trades AS t ON t.account_id = a.account_id
GROUP BY a.account_name ORDER BY a.account_name;

-- 09
SELECT trade_date, SUM(commission) AS total_fee
FROM dbo.trades GROUP BY trade_date ORDER BY trade_date;

-- 10. Учебный условный notional (инструменты разнородны).
SELECT TOP (5) i.ticker, SUM(t.quantity * t.price) AS raw_notional
FROM dbo.trades AS t
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
GROUP BY i.ticker ORDER BY raw_notional DESC, i.ticker;

-- 11
SELECT i.ticker,
       SUM(CASE WHEN t.side = 'BUY' THEN 1 ELSE 0 END) AS buys,
       SUM(CASE WHEN t.side = 'SELL' THEN 1 ELSE 0 END) AS sells
FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
GROUP BY i.ticker ORDER BY i.ticker;

-- 12
SELECT instrument_id, COUNT(*) AS trades_count
FROM dbo.trades
GROUP BY instrument_id HAVING COUNT(*) > 15
ORDER BY trades_count DESC, instrument_id;

-- 13
SELECT TOP (10) t.trade_id, i.ticker, a.account_name, t.side, t.price
FROM dbo.trades AS t
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
JOIN dbo.accounts AS a ON a.account_id = t.account_id
ORDER BY t.trade_id;

-- 14
SELECT i.ticker
FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
WHERE t.trade_id IS NULL;

-- 15
SELECT a.account_name
FROM dbo.accounts AS a
LEFT JOIN dbo.trades AS t ON t.account_id = a.account_id
WHERE t.trade_id IS NULL;

-- 16. Два поля в ON! Иначе fan-out и ложные комбинации.
SELECT TOP (10) t.trade_id, t.price, p.close_price
FROM dbo.trades AS t
LEFT JOIN dbo.daily_prices AS p
  ON p.instrument_id = t.instrument_id AND p.price_date = t.trade_date
ORDER BY t.trade_id;

-- 17
SELECT COUNT(*) AS unmatched_trade_count
FROM dbo.trades AS t
LEFT JOIN dbo.daily_prices AS p
  ON p.instrument_id = t.instrument_id AND p.price_date = t.trade_date
WHERE p.instrument_id IS NULL;

-- 18. Намеренно НЕВЕРНОЕ соединение для эксперимента.
SELECT COUNT_BIG(*) AS wrong_join_rows
FROM dbo.trades AS t
JOIN dbo.daily_prices AS p ON p.instrument_id = t.instrument_id;
SELECT COUNT_BIG(*) AS actual_trades FROM dbo.trades;

-- 19
SELECT trade_id, price FROM dbo.trades
WHERE price > (SELECT AVG(price) FROM dbo.trades)
ORDER BY price DESC, trade_id;

-- 20
SELECT a.account_name FROM dbo.accounts AS a
WHERE EXISTS (SELECT 1 FROM dbo.trades AS t
              WHERE t.account_id = a.account_id AND t.side = 'SELL');

-- 21
SELECT i.ticker FROM dbo.instruments AS i
WHERE NOT EXISTS (SELECT 1 FROM dbo.trades AS t
                  WHERE t.instrument_id = i.instrument_id);

-- 22
;WITH fees AS (
 SELECT account_id, SUM(commission) AS total_fee
 FROM dbo.trades GROUP BY account_id
)
SELECT a.account_name, COALESCE(f.total_fee, 0) AS total_fee
FROM dbo.accounts AS a
LEFT JOIN fees AS f ON f.account_id = a.account_id
ORDER BY a.account_name;

-- 23
SELECT a.account_name, last_trade.trade_id, last_trade.trade_date
FROM dbo.accounts AS a
OUTER APPLY (
 SELECT TOP (1) t.trade_id, t.trade_date FROM dbo.trades AS t
 WHERE t.account_id = a.account_id
 ORDER BY t.trade_date DESC, t.trade_id DESC
) AS last_trade;

-- 24
SELECT instrument_id, AVG(commission) AS avg_fee
FROM dbo.trades
GROUP BY instrument_id
HAVING AVG(commission) > (SELECT AVG(commission) FROM dbo.trades)
ORDER BY avg_fee DESC;

-- 25
;WITH numbered AS (
  SELECT trade_id, instrument_id, trade_date,
         ROW_NUMBER() OVER (
           PARTITION BY instrument_id ORDER BY trade_date DESC, trade_id DESC
         ) AS rn
  FROM dbo.trades
)
SELECT * FROM numbered WHERE rn = 1 ORDER BY instrument_id;

-- 26
;WITH ranked AS (
 SELECT trade_id, instrument_id, price,
        ROW_NUMBER() OVER (PARTITION BY instrument_id
                           ORDER BY price DESC, trade_id) AS rn
 FROM dbo.trades
)
SELECT * FROM ranked WHERE rn <= 3 ORDER BY instrument_id, rn;

-- 27
SELECT trade_id, account_id,
       ROW_NUMBER() OVER (PARTITION BY account_id
                          ORDER BY trade_date, trade_id) AS sequence_no
FROM dbo.trades ORDER BY account_id, sequence_no;

-- 28
SELECT p.price_date, p.close_price,
       LAG(p.close_price) OVER (ORDER BY p.price_date) AS prev_price
FROM dbo.daily_prices AS p
JOIN dbo.instruments AS i ON i.instrument_id = p.instrument_id
WHERE i.ticker = 'SBER'
ORDER BY p.price_date;

-- 29
SELECT p.price_date, p.close_price,
       AVG(p.close_price) OVER (
        ORDER BY p.price_date
        ROWS BETWEEN 4 PRECEDING AND CURRENT ROW
       ) AS sma_5
FROM dbo.daily_prices AS p
WHERE p.instrument_id = 1 ORDER BY p.price_date;

-- 30
SELECT trade_id, account_id, commission,
       SUM(commission) OVER (
         PARTITION BY account_id ORDER BY trade_date, trade_id
         ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
       ) AS running_fee
FROM dbo.trades WHERE account_id = 1
ORDER BY trade_date, trade_id;

-- 31
SELECT trade_id, trade_date FROM dbo.trades
WHERE trade_date >= '20240201' AND trade_date < '20240301'
ORDER BY trade_date, trade_id;

-- 32
SELECT DATEFROMPARTS(YEAR(trade_date), MONTH(trade_date), 1) AS month_start,
       COUNT(*) AS trades_count, SUM(commission) AS fee_total
FROM dbo.trades
GROUP BY YEAR(trade_date), MONTH(trade_date)
ORDER BY month_start;

-- 33
SELECT instrument_id, AVG(price) AS simple_avg,
       SUM(CAST(quantity AS DECIMAL(19,4)) * price)
       / NULLIF(SUM(CAST(quantity AS DECIMAL(19,4))), 0) AS weighted_avg
FROM dbo.trades
WHERE instrument_id = 3 GROUP BY instrument_id;

-- 34
SELECT TRY_CONVERT(date, N'31.12.2024', 104) AS parsed_date;

-- 35. Только чистый поток учебных сделок; без начальной позиции, корпоративных
-- действий, депозитарных операций, валютных переоценок и расчётного статуса.
SELECT a.account_name,
       SUM(CASE WHEN t.side = 'BUY' THEN t.quantity ELSE -t.quantity END) AS net_quantity
FROM dbo.trades AS t
JOIN dbo.accounts AS a ON a.account_id = t.account_id
WHERE t.instrument_id = 1
GROUP BY a.account_name ORDER BY a.account_name;
GO

-- 36. INSERT + ROLLBACK
BEGIN TRANSACTION;
INSERT INTO dbo.accounts (account_id, account_name, strategy, risk_limit_rub)
VALUES (90000, 'ACC-TEST-90000', 'demo', 100000);
SELECT * FROM dbo.accounts WHERE account_id = 90000;
ROLLBACK TRANSACTION;
GO

-- 37. UPDATE + OUTPUT + ROLLBACK
BEGIN TRANSACTION;
UPDATE dbo.accounts
SET risk_limit_rub = risk_limit_rub * 1.05
OUTPUT deleted.risk_limit_rub AS was, inserted.risk_limit_rub AS became
WHERE account_name = 'ACC-DORMANT';
ROLLBACK TRANSACTION;
GO

-- 38. Проверка CHECK
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO dbo.trades
       (trade_id, trade_date, account_id, instrument_id, side, quantity, price, commission)
    VALUES (900038, '20240109', 1, 1, 'BUY', -1, 100, 0);
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    SELECT ERROR_NUMBER() AS error_no, ERROR_MESSAGE() AS error_message;
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- 39. Проверка FK
BEGIN TRY
    BEGIN TRANSACTION;
    INSERT INTO dbo.trades
       (trade_id, trade_date, account_id, instrument_id, side, quantity, price, commission)
    VALUES (900039, '20240109', 1, 99999, 'BUY', 1, 100, 0);
    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    SELECT ERROR_NUMBER() AS error_no, ERROR_MESSAGE() AS error_message;
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- 40
DROP TABLE IF EXISTS #trade_count;
SELECT instrument_id, COUNT(*) AS trades_count
INTO #trade_count
FROM dbo.trades GROUP BY instrument_id;
SELECT * FROM #trade_count ORDER BY instrument_id;
DROP TABLE #trade_count;

-- 41
SELECT OBJECT_NAME(parent_object_id) AS table_name, name AS pk_name
FROM sys.key_constraints
WHERE type = 'PK'
  AND parent_object_id IN (OBJECT_ID(N'dbo.trades'), OBJECT_ID(N'dbo.accounts'),
      OBJECT_ID(N'dbo.instruments'), OBJECT_ID(N'dbo.daily_prices'), OBJECT_ID(N'dbo.liquidity'))
ORDER BY table_name;

-- 42
SELECT name, OBJECT_NAME(referenced_object_id) AS parent_table
FROM sys.foreign_keys WHERE parent_object_id = OBJECT_ID(N'dbo.trades');

-- 43
SELECT name, type_desc, is_unique
FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.trades')
ORDER BY index_id;

-- 44
SELECT name, is_read_committed_snapshot_on
FROM sys.databases WHERE name = N'TradingTraining';

-- 45
SELECT name, recovery_model_desc, state_desc
FROM sys.databases WHERE name = N'TradingTraining';

-- 46. Пусто: ограничение UNIQUE.
SELECT ticker, COUNT(*) AS cnt
FROM dbo.instruments GROUP BY ticker HAVING COUNT(*) > 1;

-- 47
SELECT t.trade_id, t.trade_date, t.instrument_id
FROM dbo.trades AS t
LEFT JOIN dbo.daily_prices AS p
  ON p.instrument_id = t.instrument_id AND p.price_date = t.trade_date
WHERE p.instrument_id IS NULL
ORDER BY t.trade_id;

-- 48
SELECT a.account_name, COUNT(t.trade_id) AS trade_count
FROM dbo.accounts AS a
LEFT JOIN dbo.trades AS t ON t.account_id = a.account_id
GROUP BY a.account_name
ORDER BY a.account_name;
-- Сумма trade_count по счетам = 230 при целостных FK.

-- 49. Должно быть 0 из-за CHECK в нашей схеме.
SELECT COUNT(*) AS violations FROM dbo.trades
WHERE price <= 0 OR quantity <= 0 OR commission < 0;

-- 50. Открытое задание на проектирование: lessons/17_итоговый_проект.md.
-- Контрольные требования: два ключа сущностей (order_id, execution_id),
-- 1:N через FK order_id, CHECK quantity/price, уникальный external_order_id,
-- отчёт по исполнению с LEFT JOIN, HAVING/NULLIF/VWAP и защита гонки.
