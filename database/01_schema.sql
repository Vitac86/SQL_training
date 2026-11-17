-- 01 — структура учебной БД: SQL Server T-SQL.
-- Повторный запуск не уничтожает данные, а пропускает существующие таблицы.
USE TradingTraining;
GO

IF OBJECT_ID(N'dbo.instruments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.instruments (
        instrument_id INT NOT NULL CONSTRAINT PK_instruments PRIMARY KEY,
        ticker VARCHAR(32) NOT NULL CONSTRAINT UQ_instruments_ticker UNIQUE,
        [name] NVARCHAR(200) NOT NULL,
        asset_class VARCHAR(32) NOT NULL,
        currency CHAR(3) NOT NULL
    );
END;
GO

IF OBJECT_ID(N'dbo.accounts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.accounts (
        account_id INT NOT NULL CONSTRAINT PK_accounts PRIMARY KEY,
        account_name VARCHAR(64) NOT NULL CONSTRAINT UQ_accounts_name UNIQUE,
        strategy VARCHAR(64) NOT NULL,
        risk_limit_rub DECIMAL(19, 2) NOT NULL
            CONSTRAINT CK_accounts_risk_positive CHECK (risk_limit_rub > 0)
    );
END;
GO

IF OBJECT_ID(N'dbo.trades', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.trades (
        trade_id INT NOT NULL CONSTRAINT PK_trades PRIMARY KEY,
        trade_date DATE NOT NULL,
        account_id INT NOT NULL,
        instrument_id INT NOT NULL,
        side VARCHAR(4) NOT NULL
            CONSTRAINT CK_trades_side CHECK (side IN ('BUY', 'SELL')),
        quantity BIGINT NOT NULL
            CONSTRAINT CK_trades_quantity CHECK (quantity > 0),
        price DECIMAL(19, 4) NOT NULL
            CONSTRAINT CK_trades_price CHECK (price > 0),
        commission DECIMAL(19, 4) NOT NULL
            CONSTRAINT CK_trades_commission CHECK (commission >= 0),
        CONSTRAINT FK_trades_account FOREIGN KEY (account_id)
            REFERENCES dbo.accounts(account_id),
        CONSTRAINT FK_trades_instrument FOREIGN KEY (instrument_id)
            REFERENCES dbo.instruments(instrument_id)
    );
END;
GO

IF OBJECT_ID(N'dbo.daily_prices', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.daily_prices (
        price_date DATE NOT NULL,
        instrument_id INT NOT NULL,
        close_price DECIMAL(19, 4) NOT NULL
            CONSTRAINT CK_daily_prices_close CHECK (close_price > 0),
        volume BIGINT NOT NULL
            CONSTRAINT CK_daily_prices_volume CHECK (volume >= 0),
        CONSTRAINT PK_daily_prices PRIMARY KEY (price_date, instrument_id),
        CONSTRAINT FK_daily_prices_instrument FOREIGN KEY (instrument_id)
            REFERENCES dbo.instruments(instrument_id)
    );
END;
GO

IF OBJECT_ID(N'dbo.liquidity', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.liquidity (
        price_date DATE NOT NULL,
        instrument_id INT NOT NULL,
        turnover_rub DECIMAL(24, 2) NOT NULL
            CONSTRAINT CK_liquidity_turnover CHECK (turnover_rub >= 0),
        trades_count BIGINT NOT NULL
            CONSTRAINT CK_liquidity_count CHECK (trades_count >= 0),
        bid_ask_spread_bps DECIMAL(12, 4) NOT NULL
            CONSTRAINT CK_liquidity_spread CHECK (bid_ask_spread_bps >= 0),
        CONSTRAINT PK_liquidity PRIMARY KEY (price_date, instrument_id),
        CONSTRAINT FK_liquidity_instrument FOREIGN KEY (instrument_id)
            REFERENCES dbo.instruments(instrument_id)
    );
END;
GO

SELECT s.name AS schema_name, t.name AS table_name
FROM sys.tables AS t
JOIN sys.schemas AS s ON s.schema_id = t.schema_id
WHERE s.name = N'dbo' AND t.name IN
  (N'instruments', N'accounts', N'trades', N'daily_prices', N'liquidity')
ORDER BY t.name;
GO
