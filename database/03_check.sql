-- 03 — проверка загрузки исходного набора и ссылочной целостности.
USE TradingTraining;
GO
SET NOCOUNT ON;

SELECT N'instruments' AS table_name, COUNT_BIG(*) AS rows_actual, CAST(12 AS BIGINT) AS rows_expected FROM dbo.instruments
UNION ALL SELECT N'accounts', COUNT_BIG(*), 6 FROM dbo.accounts
UNION ALL SELECT N'trades', COUNT_BIG(*), 230 FROM dbo.trades
UNION ALL SELECT N'daily_prices', COUNT_BIG(*), 429 FROM dbo.daily_prices
UNION ALL SELECT N'liquidity', COUNT_BIG(*), 429 FROM dbo.liquidity;

IF (SELECT COUNT(*) FROM dbo.instruments) <> 12
    OR (SELECT COUNT(*) FROM dbo.accounts) <> 6
    OR (SELECT COUNT(*) FROM dbo.trades) <> 230
    OR (SELECT COUNT(*) FROM dbo.daily_prices) <> 429
    OR (SELECT COUNT(*) FROM dbo.liquidity) <> 429
    THROW 51001, N'Неверное число строк: проверь порядок установки и полную загрузку 02_seed_data.sql', 1;

IF EXISTS (
    SELECT 1 FROM dbo.trades AS t
    LEFT JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
    WHERE i.instrument_id IS NULL
) OR EXISTS (
    SELECT 1 FROM dbo.trades AS t
    LEFT JOIN dbo.accounts AS a ON a.account_id = t.account_id
    WHERE a.account_id IS NULL
)
    THROW 51002, N'Нарушена ссылочная целостность сделок', 1;

-- Два контрольных сценария для урока JOIN:
SELECT i.ticker, COUNT(t.trade_id) AS trades_count
FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
WHERE i.ticker = 'TEST0'
GROUP BY i.ticker;

SELECT a.account_name, COUNT(t.trade_id) AS trades_count
FROM dbo.accounts AS a
LEFT JOIN dbo.trades AS t ON t.account_id = a.account_id
WHERE a.account_name = 'ACC-DORMANT'
GROUP BY a.account_name;

-- Ключевые метаданные:
SELECT t.name AS table_name, c.name AS column_name, ty.name AS data_type,
       c.max_length, c.precision, c.scale, c.is_nullable
FROM sys.tables AS t
JOIN sys.columns AS c ON c.object_id = t.object_id
JOIN sys.types AS ty ON ty.user_type_id = c.user_type_id
WHERE t.name IN (N'instruments', N'accounts', N'trades', N'daily_prices', N'liquidity')
ORDER BY t.name, c.column_id;
PRINT N'Проверки пройдены: можно начинать урок 00.';
GO
