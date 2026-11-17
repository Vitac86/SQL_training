# Урок 13. Представления, процедуры и временные таблицы

**Цель:** разделить логику запроса, повторно используемый интерфейс и промежуточный набор данных.

## VIEW — сохранённый запрос

`VIEW` хранит определение SELECT, а не обязательно его результаты. Обычный VIEW не гарантирует физическую материализацию и не определяет порядок данных.

```sql
USE TradingTraining;
GO
CREATE OR ALTER VIEW dbo.v_trades_enriched
AS
SELECT t.trade_id, t.trade_date, a.account_name, i.ticker,
       i.asset_class, t.side, t.quantity, t.price, t.commission
FROM dbo.trades AS t
JOIN dbo.accounts AS a ON a.account_id = t.account_id
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id;
GO

SELECT TOP (10) * FROM dbo.v_trades_enriched ORDER BY trade_id;
```

`CREATE OR ALTER VIEW` должен быть отдельным batch (здесь это обеспечивает `GO`). VIEW удобен для согласованного описания полей и отчётности, но не заменяет модель полномочий и не гарантирует безопасность автоматически.

## PROCEDURE — серверный контракт

```sql
CREATE OR ALTER PROCEDURE dbo.usp_TradesByAccount
    @AccountId INT
AS
BEGIN
    SET NOCOUNT ON;

    SELECT t.trade_id, t.trade_date, i.ticker, t.side, t.quantity, t.price
    FROM dbo.trades AS t
    JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
    WHERE t.account_id = @AccountId
    ORDER BY t.trade_date, t.trade_id;
END;
GO

EXEC dbo.usp_TradesByAccount @AccountId = 1;
```

Процедура может изменять данные, управлять транзакциями и скрывать реализацию от клиента. Параметры не надо подставлять строковой конкатенацией в динамический SQL; при необходимости используй `sp_executesql` с типизированными параметрами.

## Временные таблицы

```sql
DROP TABLE IF EXISTS #trade_totals;
SELECT account_id, COUNT(*) AS trade_count,
       SUM(commission) AS fee_total
INTO #trade_totals
FROM dbo.trades
GROUP BY account_id;

SELECT * FROM #trade_totals ORDER BY account_id;
DROP TABLE #trade_totals;
```

Локальная `#temp` видна в рамках соединения/области видимости, физически размещается в `tempdb`. В отличие от CTE, это **реально созданная таблица** с данными; на неё при необходимости можно добавить индекс. Табличные переменные `@table` имеют иные свойства и ограничения.

## Задания

1. Создай VIEW с оборотом и комиссией по счёту, выведи топ по комиссии.
2. Измени `usp_TradesByAccount`, добавив необязательный фильтр по стороне BUY/SELL.
3. Сравни CTE и #temp для двух обращений к промежуточному набору.
4. Объясни, почему `ORDER BY` в конце потребляющего SELECT необходим даже при наличии сортировки в источнике.

**Контроль:** знаешь различие между VIEW, PROCEDURE, CTE и #temp, можешь выбрать подходящий инструмент.
