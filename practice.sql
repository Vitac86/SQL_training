-- Личный рабочий файл. Выполняй только выделенный запрос или весь безопасный фрагмент.
-- В SSMS: F5. Сначала запусти database/00..03.
USE TradingTraining;
GO

SELECT TOP (10)
       t.trade_id, t.trade_date, i.ticker, a.account_name,
       t.side, t.quantity, t.price
FROM dbo.trades AS t
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
JOIN dbo.accounts AS a ON a.account_id = t.account_id
ORDER BY t.trade_id;

-- Свои запросы пиши ниже.
