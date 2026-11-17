-- ОПАСНАЯ ОПЕРАЦИЯ: удалить учебные данные, оставив таблицы.
-- По умолчанию отключено. Заменить 0 на 1 вручную, если нужен сброс.
USE TradingTraining;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
DECLARE @ConfirmReset BIT = 0;
IF @ConfirmReset <> 1
BEGIN
    PRINT N'Сброс не выполнен: измените @ConfirmReset на 1, если действительно хотите удалить учебные данные.';
END
ELSE
BEGIN
    BEGIN TRY
        BEGIN TRANSACTION;
        DELETE FROM dbo.liquidity;
        DELETE FROM dbo.daily_prices;
        DELETE FROM dbo.trades;
        DELETE FROM dbo.accounts;
        DELETE FROM dbo.instruments;
        COMMIT TRANSACTION;
        PRINT N'Данные удалены. Снова выполните database/02_seed_data.sql.';
    END TRY
    BEGIN CATCH
        IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
        THROW;
    END CATCH;
END;
GO
