-- Необязательный шаг после урока 12. Изменяет структуру (индексы), но не данные.
USE TradingTraining;
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.trades') AND name = N'IX_trades_instrument_date')
    CREATE NONCLUSTERED INDEX IX_trades_instrument_date
    ON dbo.trades(instrument_id, trade_date)
    INCLUDE (quantity, price, side, commission);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.trades') AND name = N'IX_trades_account_date')
    CREATE NONCLUSTERED INDEX IX_trades_account_date
    ON dbo.trades(account_id, trade_date);
GO

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.daily_prices') AND name = N'IX_daily_prices_instrument_date')
    CREATE NONCLUSTERED INDEX IX_daily_prices_instrument_date
    ON dbo.daily_prices(instrument_id, price_date)
    INCLUDE (close_price);
GO

-- Индексы ускоряют часть чтений, но занимают место и удорожают записи.
-- На учебных сотнях строк оптимизатор вправе выбрать сканирование: это не ошибка.
SELECT OBJECT_NAME(object_id) AS table_name, name, type_desc
FROM sys.indexes WHERE object_id IN (OBJECT_ID(N'dbo.trades'), OBJECT_ID(N'dbo.daily_prices'))
ORDER BY table_name, name;
GO
