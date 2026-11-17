-- ============================================================
-- answers.sql
-- Решения к exercises.sql
-- ============================================================


-- ============================================================
-- 1. SELECT / WHERE / ORDER BY
-- ============================================================

-- 1.1
SELECT * FROM instruments WHERE currency = 'RUB' ORDER BY ticker;

-- 1.2
SELECT * FROM trades WHERE price > 5000 ORDER BY price DESC;

-- 1.3
SELECT DISTINCT strategy FROM accounts;

-- 1.4
SELECT * FROM trades WHERE side = 'SELL' AND quantity >= 200;

-- 1.5
SELECT * FROM trades ORDER BY price ASC LIMIT 5;


-- ============================================================
-- 2. GROUP BY
-- ============================================================

-- 2.1
SELECT instrument_id, COUNT(*) AS trades_count
FROM trades
GROUP BY instrument_id;

-- 2.2
SELECT side, SUM(quantity * price) AS turnover
FROM trades
GROUP BY side;

-- 2.3
SELECT account_id, AVG(commission) AS avg_commission
FROM trades
GROUP BY account_id
ORDER BY avg_commission DESC;

-- 2.4
SELECT instrument_id, SUM(quantity) AS total_quantity
FROM trades
GROUP BY instrument_id
HAVING SUM(quantity) > 5000;

-- 2.5
-- SUM(CASE ...) — стандартный приём "разворота" (pivot) категорий в колонки:
-- каждая сделка добавляет 1 к своему счётчику и 0 к другому.
SELECT
    instrument_id,
    SUM(CASE WHEN side = 'BUY'  THEN 1 ELSE 0 END) AS buy_count,
    SUM(CASE WHEN side = 'SELL' THEN 1 ELSE 0 END) AS sell_count
FROM trades
GROUP BY instrument_id;


-- ============================================================
-- 3. JOIN
-- ============================================================

-- 3.1
SELECT
    t.trade_id, t.trade_date, i.ticker, a.account_name, t.side, t.quantity, t.price
FROM trades AS t
JOIN instruments AS i ON i.instrument_id = t.instrument_id
JOIN accounts    AS a ON a.account_id    = t.account_id;

-- 3.2
SELECT
    a.strategy,
    SUM(t.quantity * t.price) AS turnover
FROM trades AS t
JOIN accounts AS a ON a.account_id = t.account_id
GROUP BY a.strategy
ORDER BY turnover DESC;

-- 3.3
-- COUNT(t.trade_id), а не COUNT(*): для инструментов без сделок LEFT JOIN даёт
-- NULL в t.trade_id, а COUNT(колонка) не считает NULL-значения -> корректный 0.
SELECT
    i.ticker,
    COUNT(t.trade_id) AS trades_count
FROM instruments AS i
LEFT JOIN trades AS t ON t.instrument_id = i.instrument_id
GROUP BY i.ticker
ORDER BY trades_count DESC;

-- 3.4
SELECT
    a.account_name,
    COUNT(t.trade_id) AS trades_count
FROM accounts AS a
LEFT JOIN trades AS t ON t.account_id = a.account_id
GROUP BY a.account_name
ORDER BY trades_count DESC;

-- 3.5
SELECT
    t.trade_id, i.ticker, a.account_name, t.side, t.quantity, t.price
FROM trades AS t
JOIN instruments AS i ON i.instrument_id = t.instrument_id
JOIN accounts    AS a ON a.account_id    = t.account_id
WHERE i.asset_class = 'bond';


-- ============================================================
-- 4. Подзапросы (subqueries)
-- ============================================================

-- 4.1
SELECT *
FROM trades
WHERE price > (SELECT AVG(price) FROM trades);

-- 4.2
-- NOT IN с подзапросом и LEFT JOIN ... WHERE ... IS NULL (урок 4) — два разных пути
-- к одному и тому же результату. LEFT JOIN обычно предпочтительнее в реальном коде,
-- потому что он яснее читается и лучше ведёт себя, если в подзапросе случайно
-- окажется NULL. Но важно уметь узнавать оба варианта.
SELECT ticker
FROM instruments
WHERE instrument_id NOT IN (SELECT DISTINCT instrument_id FROM trades);

-- 4.3
-- Коррелированный подзапрос: для каждой строки dp внешнего запроса подзапрос
-- пересчитывается заново, с фильтром "тот же instrument_id, что и в текущей строке".
SELECT dp.instrument_id, dp.price_date, dp.close_price
FROM daily_prices AS dp
WHERE dp.price_date = (
    SELECT MAX(dp2.price_date)
    FROM daily_prices AS dp2
    WHERE dp2.instrument_id = dp.instrument_id
)
ORDER BY dp.instrument_id;

-- 4.4
-- Подзапрос в FROM ("производная таблица"): сначала считаем оборот по каждому
-- счёту, из этого результата отдельным подзапросом берём среднее, и сравниваем.
SELECT account_id, turnover
FROM (
    SELECT account_id, SUM(quantity * price) AS turnover
    FROM trades
    GROUP BY account_id
) AS turnover_by_account
WHERE turnover > (
    SELECT AVG(turnover) FROM (
        SELECT SUM(quantity * price) AS turnover
        FROM trades
        GROUP BY account_id
    )
);

-- 4.5
SELECT instrument_id, price_date, close_price
FROM daily_prices
WHERE close_price = (SELECT MAX(close_price) FROM daily_prices);


-- ============================================================
-- 5. Временные ряды
-- ============================================================

-- 5.1
SELECT dp.price_date, dp.close_price
FROM daily_prices AS dp
JOIN instruments AS i ON i.instrument_id = dp.instrument_id
WHERE i.ticker = 'SBER'
ORDER BY dp.price_date;

-- 5.2
SELECT instrument_id, AVG(volume) AS avg_volume
FROM daily_prices
GROUP BY instrument_id;

-- 5.3
SELECT dp.price_date, dp.volume
FROM daily_prices AS dp
JOIN instruments AS i ON i.instrument_id = dp.instrument_id
WHERE i.ticker = 'GAZP'
ORDER BY dp.volume DESC
LIMIT 1;

-- 5.4
SELECT instrument_id, SUM(turnover_rub) AS turnover_total
FROM liquidity
GROUP BY instrument_id
ORDER BY turnover_total DESC
LIMIT 5;

-- 5.5
SELECT i.ticker, l.price_date, l.bid_ask_spread_bps
FROM liquidity AS l
JOIN instruments AS i ON i.instrument_id = l.instrument_id
WHERE l.bid_ask_spread_bps > 10
ORDER BY l.bid_ask_spread_bps DESC;


-- ============================================================
-- 6. INSERT / UPDATE / DELETE
-- ============================================================

-- 6.1
-- Сначала посмотреть существующие ID, если нужно:
-- SELECT instrument_id, ticker FROM instruments;
-- SELECT account_id, account_name FROM accounts;
INSERT INTO trades (trade_date, account_id, instrument_id, side, quantity, price, commission)
VALUES ('2024-03-05', 1, 1, 'BUY', 15, 275.40, 4.13);

-- 6.2 (подставьте реальный trade_id, который вернул предыдущий INSERT,
-- например через: SELECT last_insert_rowid();)
UPDATE trades SET commission = 5.00 WHERE trade_id = (SELECT MAX(trade_id) FROM trades);

-- 6.3
DELETE FROM trades WHERE trade_id = (SELECT MAX(trade_id) FROM trades);

-- 6.4
INSERT INTO trades (trade_date, account_id, instrument_id, side, quantity, price, commission)
VALUES ('2024-03-05', 1, 1, 'HOLD', 15, 275.40, 4.13);
-- Ожидаемая ошибка: "CHECK constraint failed: side IN ('BUY', 'SELL')"
-- Правило: side TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')) допускает только
-- два конкретных значения; 'HOLD' в их число не входит.


-- ============================================================
-- 7. Транзакции
-- ============================================================

-- 7.1
BEGIN;
INSERT INTO trades (trade_date, account_id, instrument_id, side, quantity, price, commission)
VALUES ('2024-03-06', 1, 1, 'BUY', 10, 270.00, 1.08);
INSERT INTO trades (trade_date, account_id, instrument_id, side, quantity, price, commission)
VALUES ('2024-03-06', 2, 2, 'SELL', 100, 132.00, 5.28);
ROLLBACK;
-- SELECT COUNT(*) FROM trades; -- вернёт то же число, что было до BEGIN

-- 7.2
BEGIN;
INSERT INTO trades (trade_date, account_id, instrument_id, side, quantity, price, commission)
VALUES ('2024-03-06', 1, 1, 'BUY', 10, 270.00, 1.08);
INSERT INTO trades (trade_date, account_id, instrument_id, side, quantity, price, commission)
VALUES ('2024-03-06', 2, 2, 'SELL', 100, 132.00, 5.28);
COMMIT;
-- Проверка: SELECT * FROM trades WHERE trade_date = '2024-03-06';
-- Возврат базы в исходное состояние (чтобы не мешать следующим урокам):
DELETE FROM trades WHERE trade_date = '2024-03-06';

-- 7.3
BEGIN;
INSERT INTO trades (trade_date, account_id, instrument_id, side, quantity, price, commission)
VALUES ('2024-03-07', 1, 1, 'BUY', -1, 270.00, 1.08);
-- Ожидаемая ошибка: "CHECK constraint failed: quantity > 0"
-- Важный вывод: сама неудачная команда откатывается автоматически (эта конкретная
-- строка не добавляется), НО транзакция, открытая через BEGIN, остаётся ОТКРЫТОЙ.
-- Убедиться в этом можно так: попробуйте сразу после ошибки выполнить ещё раз BEGIN;
--     BEGIN;
--     -- Ошибка: "cannot start a transaction within a transaction"
-- Это доказывает, что предыдущая транзакция всё ещё не закрыта. Значит, нужно явно
-- выполнить ROLLBACK (или COMMIT, если по смыслу нужно сохранить то, что уже успело
-- выполниться до ошибки) - "само" оно не закроется.
ROLLBACK;
