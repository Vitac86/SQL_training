-- 00 — SQL Server / SSMS. Выполнить первым.
-- Команда GO разделяет пакеты SSMS, она не является оператором T-SQL.
USE master;
GO
IF DB_ID(N'TradingTraining') IS NULL
BEGIN
    CREATE DATABASE TradingTraining;
    PRINT N'База TradingTraining создана';
END
ELSE
    PRINT N'База TradingTraining уже существует';
GO

USE TradingTraining;
GO
SELECT DB_NAME() AS database_name, @@SERVERNAME AS server_name;
GO
