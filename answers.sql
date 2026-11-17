-- answers.sql — решения к упражнениям 01–50 (exercises.sql).
-- Открывай ТОЛЬКО после самостоятельной попытки.
-- Выделяй и выполняй нужный пример, не весь файл сразу.
-- Задания по SSMS и теории решаются комментариями рядом с номером.
-- Для учебных изменений используем ROLLBACK. Создание VIEW — учебный объект.
USE TradingTraining;
GO

-- ===== Урок 00 =====

-- 01. Все счета, ожидается 6 строк.
SELECT * FROM dbo.accounts;

-- 02. 12 тикеров и названий.
SELECT ticker, [name] FROM dbo.instruments;

-- 03. Первые три по trade_id.
SELECT TOP (3) * FROM dbo.trades ORDER BY trade_id;

-- 04. Ответ — 429.
SELECT COUNT(*) AS price_rows FROM dbo.daily_prices;

-- ===== Урок 01 =====

-- 05. Самая высокая цена.
SELECT TOP (5) trade_id, price
FROM dbo.trades
ORDER BY price DESC, trade_id ASC;

-- 06. DISTINCT убирает дубли комбинаций выбранных полей.
SELECT DISTINCT asset_class FROM dbo.instruments ORDER BY asset_class;

-- 07. AS меняет только заголовки результата, не схему БД.
SELECT ticker AS symbol, [name] AS instrument_name
FROM dbo.instruments;

-- 08. Арифметический пример, не готовый показатель рублёвого оборота.
SELECT TOP (5) trade_id, quantity, price,
       quantity * price AS example_notional
FROM dbo.trades ORDER BY trade_id;

-- ===== Урок 02 =====

-- 09. Оба условия обязательны.
SELECT trade_id, side, quantity
FROM dbo.trades
WHERE side = 'SELL' AND quantity >= 200
ORDER BY trade_id;

-- 10
SELECT ticker, asset_class
FROM dbo.instruments
WHERE asset_class IN ('fx', 'bond')
ORDER BY ticker;

-- 11
SELECT ticker FROM dbo.instruments WHERE ticker LIKE 'S%' ORDER BY ticker;

-- 12. BETWEEN включает оба края.
SELECT trade_id, price FROM dbo.trades
WHERE price BETWEEN 100 AND 500
ORDER BY price, trade_id;

-- 13
SELECT trade_id, side, price
FROM dbo.trades
WHERE side = 'BUY' AND price > 1000
ORDER BY price DESC, trade_id;

-- ===== Урок 03 =====

-- 14. В Object Explorer → dbo.accounts → Columns:
-- account_id = INT; account_name = VARCHAR(64);
-- risk_limit_rub = DECIMAL(19,2).
-- Целый идентификатор, текстовое имя и точная десятичная сумма — разные типы.

-- 15. Числа складываются, две строки текста объединяются:
SELECT 2 + 3 AS numeric_result;  -- 5
SELECT '2' + '3' AS text_result;  -- '23'

-- ===== Урок 04 =====

-- 16. Первая сделка: instrument_id=3 (LKOH), account_id=5 (ACC-FIXEDINCOME).
SELECT trade_id, instrument_id, account_id
FROM dbo.trades WHERE trade_id = 1;
SELECT ticker FROM dbo.instruments WHERE instrument_id = 3;
SELECT account_name FROM dbo.accounts WHERE account_id = 5;

-- 17. В Object Explorer у dbo.trades → Keys:
-- PK_trades определяет уникальный trade_id.
-- FK_trades_account ссылается на accounts; FK_trades_instrument на instruments.
-- FK защищают допустимость ссылки, но сами не делают JOIN.

-- ===== Урок 05 =====

-- 18. Выполняй по шагам в отдельном окне. Только учебная таблица.
-- ШАГ 1: если таблицу уже создал самостоятельно, НЕ запускай CREATE второй раз.
-- CREATE TABLE dbo.exercise18_orders (
--     order_id INT NOT NULL PRIMARY KEY,
--     ticker VARCHAR(20) NOT NULL,
--     quantity INT NOT NULL
--         CONSTRAINT CK_exercise18_quantity CHECK (quantity > 0)
-- );
--
-- ШАГ 2: корректный INSERT:
-- INSERT INTO dbo.exercise18_orders (order_id, ticker, quantity)
-- VALUES (1, 'SBER', 10);
--
-- ШАГ 3: отдельно пробуй недопустимый INSERT и читай вкладку «Сообщения»:
-- INSERT INTO dbo.exercise18_orders (order_id, ticker, quantity)
-- VALUES (2, 'GAZP', -10);
-- Ожидается отклонение из-за CK_exercise18_quantity.
--
-- ШАГ 4: SELECT * FROM dbo.exercise18_orders;
-- Результат — только order_id=1, quantity=10.
-- ШАГ 5: когда завершишь упражнение, удали ТОЛЬКО учебную таблицу:
-- DROP TABLE dbo.exercise18_orders;

-- 19. Системный каталог — дополнительный способ найти то, что видно в SSMS.
SELECT name, definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.trades')
ORDER BY name;
-- CK_trades_quantity содержит CHECK(quantity > 0).

-- 20. WHERE выбирает строки при ЧТЕНИИ.
-- CHECK запрещает недопустимые значения при СОХРАНЕНИИ.

-- ===== Урок 06 =====

-- 21. Ожидаются BUY и SELL (в сумме 230).
SELECT side, COUNT(*) AS trade_count
FROM dbo.trades GROUP BY side ORDER BY side;

-- 22
SELECT account_id, AVG(commission) AS avg_fee
FROM dbo.trades GROUP BY account_id ORDER BY account_id;

-- 23. WHERE фильтрует строки, HAVING — группы после COUNT.
SELECT trade_date, COUNT(*) AS trade_count
FROM dbo.trades GROUP BY trade_date
HAVING COUNT(*) > 5
ORDER BY trade_date;

-- 24
SELECT instrument_id, SUM(commission) AS fees
FROM dbo.trades GROUP BY instrument_id
ORDER BY fees DESC, instrument_id;

-- ===== Урок 07 =====

-- 25. Важно правильно соединить ID с ID.
SELECT TOP (10) t.trade_id, i.ticker, a.account_name
FROM dbo.trades AS t
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
JOIN dbo.accounts AS a ON a.account_id = t.account_id
ORDER BY t.trade_id;

-- 26. Ожидается TEST0.
SELECT i.ticker
FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
WHERE t.trade_id IS NULL;

-- 27. Ожидается ACC-DORMANT.
SELECT a.account_name
FROM dbo.accounts AS a
LEFT JOIN dbo.trades AS t ON t.account_id = a.account_id
WHERE t.trade_id IS NULL;

-- 28. COUNT(t.trade_id) даёт 0 для TEST0, COUNT(*) — ошибочную единицу.
SELECT i.ticker, COUNT(t.trade_id) AS trade_count
FROM dbo.instruments AS i
LEFT JOIN dbo.trades AS t ON t.instrument_id = i.instrument_id
GROUP BY i.ticker ORDER BY i.ticker;

-- 29. ДВА поля в ON: инструмент и дата.
SELECT TOP (10) t.trade_id, t.price, p.close_price
FROM dbo.trades AS t
LEFT JOIN dbo.daily_prices AS p
  ON p.instrument_id = t.instrument_id AND p.price_date = t.trade_date
ORDER BY t.trade_id;

-- ===== Урок 08 =====

-- 30. Выполнять блок целиком; изменения НЕ сохраняются.
BEGIN TRANSACTION;
UPDATE dbo.accounts
SET risk_limit_rub = risk_limit_rub + 1000
WHERE account_id = 6;
SELECT account_id, risk_limit_rub FROM dbo.accounts WHERE account_id = 6;
ROLLBACK TRANSACTION;
SELECT account_id, risk_limit_rub FROM dbo.accounts WHERE account_id = 6;
GO

-- 31. ACC-DORMANT — счёт без сделок. После ROLLBACK возвращается.
BEGIN TRANSACTION;
DELETE FROM dbo.accounts WHERE account_id = 6;
SELECT COUNT(*) AS during_delete FROM dbo.accounts WHERE account_id = 6;
ROLLBACK TRANSACTION;
SELECT COUNT(*) AS after_rollback FROM dbo.accounts WHERE account_id = 6;
GO

-- ===== Урок 09 =====

-- 32
SELECT trade_id, commission
FROM dbo.trades
WHERE commission > (SELECT AVG(commission) FROM dbo.trades)
ORDER BY commission DESC;

-- 33. NOT EXISTS не требует извлекать строки правой таблицы.
SELECT a.account_name
FROM dbo.accounts AS a
WHERE NOT EXISTS (
    SELECT 1 FROM dbo.trades AS t WHERE t.account_id = a.account_id
);

-- 34
WITH fees_by_account AS (
    SELECT account_id, SUM(commission) AS fees
    FROM dbo.trades GROUP BY account_id
)
SELECT a.account_name, COALESCE(f.fees, 0) AS fees
FROM dbo.accounts AS a
LEFT JOIN fees_by_account AS f ON f.account_id = a.account_id
ORDER BY a.account_name;

-- ===== Урок 10 =====

-- 35. Полуоткрытый диапазон.
SELECT trade_id, trade_date
FROM dbo.trades
WHERE trade_date >= '20240201' AND trade_date < '20240301'
ORDER BY trade_date, trade_id;

-- 36. LKOH — одна однородная группа. Средняя по сделкам ≠ взвешенная по объёму.
SELECT AVG(price) AS simple_avg,
       SUM(CAST(quantity AS DECIMAL(19,4)) * price)
          / NULLIF(SUM(CAST(quantity AS DECIMAL(19,4))), 0) AS weighted_avg
FROM dbo.trades
WHERE instrument_id = 3;

-- 37
SELECT YEAR(trade_date) AS trade_year,
       MONTH(trade_date) AS trade_month,
       SUM(commission) AS fees
FROM dbo.trades
GROUP BY YEAR(trade_date), MONTH(trade_date)
ORDER BY trade_year, trade_month;

-- ===== Урок 11 =====

-- 38. Самая поздняя дата и максимальный ID при совпадении дат.
WITH ranked AS (
    SELECT trade_id, instrument_id, trade_date,
           ROW_NUMBER() OVER (
               PARTITION BY instrument_id
               ORDER BY trade_date DESC, trade_id DESC
           ) AS rn
    FROM dbo.trades
)
SELECT trade_id, instrument_id, trade_date
FROM ranked WHERE rn = 1 ORDER BY instrument_id;

-- 39. Первая prev_close будет NULL.
SELECT price_date, close_price,
       LAG(close_price) OVER (ORDER BY price_date) AS prev_close
FROM dbo.daily_prices
WHERE instrument_id = 1
ORDER BY price_date;

-- 40. Пять наблюдений, а не пять календарных дней.
SELECT price_date, close_price,
       AVG(close_price) OVER (
           ORDER BY price_date
           ROWS BETWEEN 4 PRECEDING AND CURRENT ROW
       ) AS sma_5
FROM dbo.daily_prices
WHERE instrument_id = 1
ORDER BY price_date;

-- ===== Урок 12 =====

-- 41. На бумаге:
-- orders(order_id PK, account_id FK, instrument_id FK, side, quantity, ...);
-- executions(execution_id PK, order_id FK, executed_qty, price, ...).
-- Одно поручение → 0..N исполнений; нельзя хранить fill1/fill2 в orders.
-- Неизвестная лимитная цена рыночного поручения может быть NULL.

-- ===== Урок 13 =====

-- 42. Только учебная БД; не оставлять незавершённую транзакцию.
BEGIN TRY
    BEGIN TRANSACTION;

    INSERT INTO dbo.trades
        (trade_id, trade_date, account_id, instrument_id,
         side, quantity, price, commission)
    VALUES (900042, '20240109', 1, 1, 'BUY', -10, 100, 0);

    ROLLBACK TRANSACTION;
END TRY
BEGIN CATCH
    SELECT ERROR_NUMBER() AS error_no, ERROR_MESSAGE() AS error_message;
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
END CATCH;
GO

-- 43. Например, перевод: списать со счёта A и зачислить на счёт B
-- нужно как единую атомарную операцию. COMMIT фиксирует, ROLLBACK отменяет.

-- ===== Урок 14 =====

-- 44. При 0 чтение READ COMMITTED обычно блокируется незавершённым UPDATE,
-- при 1 RCSI часто читает предыдущую подтверждённую версию.
SELECT name, is_read_committed_snapshot_on
FROM sys.databases WHERE name = N'TradingTraining';

-- ===== Урок 15 =====

-- 45. Метаданные индексов.
SELECT name, type_desc, is_unique
FROM sys.indexes
WHERE object_id = OBJECT_ID(N'dbo.trades')
ORDER BY index_id;

-- 46. Первый запрос с диапазоном часто лучше соответствует поиску по индексу.
SELECT trade_id FROM dbo.trades
WHERE trade_date >= '20240201' AND trade_date < '20240301';

SELECT trade_id FROM dbo.trades
WHERE YEAR(trade_date) = 2024 AND MONTH(trade_date) = 2;
-- Оба должны выбрать февраль; производительность оценивается реальным планом.

-- ===== Урок 16 =====

-- 47. VIEW — определение запроса. GO отделяет его в отдельный пакет SSMS.
GO
CREATE OR ALTER VIEW dbo.v_exercise47_trades
AS
SELECT t.trade_id, i.ticker, a.account_name
FROM dbo.trades AS t
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
JOIN dbo.accounts AS a ON a.account_id = t.account_id;
GO
SELECT TOP (5) * FROM dbo.v_exercise47_trades ORDER BY trade_id;
GO

-- ===== Урок 17 =====

-- 48. Имя входа на сервер ≠ имя пользователя внутри базы.
SELECT ORIGINAL_LOGIN() AS login_name,
       USER_NAME() AS database_user;

-- ===== Урок 18 =====

-- 49. SQL показывает папку; сам backup выполняй в SSMS Tasks → Back Up.
SELECT CAST(SERVERPROPERTY('InstanceDefaultBackupPath') AS NVARCHAR(4000))
       AS backup_folder;
-- Файл .bak пишет служба SQL Server. Папка должна быть доступна именно ей.

-- ===== Урок 19 =====

-- 50a. Ожидается 230.
SELECT COUNT(*) AS base_rows FROM dbo.trades;

-- 50b. Ожидается 0 строк: PK не допускает дублей trade_id.
SELECT trade_id, COUNT(*) AS repeated
FROM dbo.trades
GROUP BY trade_id HAVING COUNT(*) > 1;

-- 50c. Ожидается 230 — каждый trade ссылается на существующие ключи.
SELECT COUNT(*) AS joined_rows
FROM dbo.trades AS t
JOIN dbo.instruments AS i ON i.instrument_id = t.instrument_id
JOIN dbo.accounts AS a ON a.account_id = t.account_id;

-- Урок 20 — открытый итоговый проект (lesson20_orders и lesson20_executions):
-- lessons/20_итоговый_проект.md. Там есть этапы и критерии приёмки.
