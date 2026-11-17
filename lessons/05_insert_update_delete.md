# Урок 05. Изменение данных без потерь: INSERT, UPDATE, DELETE

**Цель:** контролировать запись данных, ограничения и откаты.

## Три оператора DML

`INSERT` добавляет строки, `UPDATE` меняет существующие, `DELETE` удаляет строки. **Без WHERE UPDATE и DELETE затронут всю таблицу**. Отмена не гарантирована, если транзакция уже зафиксирована.

Учебная база не production, но привычку безопасной работы формируем сразу: `SELECT` → `BEGIN TRAN` → проверка числа затронутых строк → `ROLLBACK` либо осознанный `COMMIT`.

## Безопасный INSERT

```sql
USE TradingTraining;
BEGIN TRANSACTION;
INSERT INTO dbo.instruments (instrument_id, ticker, [name], asset_class, currency)
VALUES (90000, 'DEMO9', N'Временный инструмент', 'equity', 'RUB');

SELECT * FROM dbo.instruments WHERE instrument_id = 90000;
ROLLBACK TRANSACTION;
SELECT * FROM dbo.instruments WHERE instrument_id = 90000;
```

После `ROLLBACK` строка пропадает. Литерал `N'...'` сохраняет Unicode. Колонки перечисляем явно, не полагаемся на порядок определения таблицы.

## UPDATE/DELETE и OUTPUT

```sql
BEGIN TRANSACTION;
SELECT * FROM dbo.accounts WHERE account_id = 6;

UPDATE dbo.accounts
SET risk_limit_rub = risk_limit_rub + 1000
OUTPUT deleted.risk_limit_rub AS old_limit,
       inserted.risk_limit_rub AS new_limit
WHERE account_id = 6;

SELECT @@ROWCOUNT AS affected_rows; -- сразу после UPDATE? Нет: уже после SELECT!
ROLLBACK TRANSACTION;
```

**Тонкость:** `@@ROWCOUNT` относится к *последнему* оператору. Сохрани его **немедленно после UPDATE**, прежде чем запускать SELECT. Исправленный шаблон:

```sql
BEGIN TRANSACTION;
UPDATE dbo.accounts SET risk_limit_rub = risk_limit_rub + 1000 WHERE account_id = 6;
DECLARE @changed INT = @@ROWCOUNT;
SELECT @changed AS changed_rows;
ROLLBACK TRANSACTION;
```

Для DELETE так же: сначала SELECT с будущим WHERE, затем DELETE в транзакции. FK может запрещать удаление родительской записи, пока на неё есть ссылки.

## INSERT SELECT

```sql
-- Пример формирования результирующего набора без записи:
SELECT instrument_id, ticker INTO #demo_instruments
FROM dbo.instruments WHERE asset_class = 'fx';
SELECT * FROM #demo_instruments;
DROP TABLE #demo_instruments;
```

`SELECT INTO` создаёт таблицу (здесь временную `#demo_instruments`); `INSERT ... SELECT` загружает данные в существующую таблицу, которую ты создал ранее.

## Задания

1. В транзакции добавь тестовый счёт с ID 90000 и откати.
2. Обнови лимит у `ACC-DORMANT` на 5% и выведи старое/новое значение через OUTPUT. Откати.
3. Попробуй удалить `SBER` из справочника инструментов в транзакции. Как FK защищает дочерние сделки? Откати.
4. Почему `TRUNCATE TABLE` не эквивалентен `DELETE`? Разбери запреты с FK, логирование, разрешения и возможность отката внутри транзакции.

**Контроль:** ни одно упражнение не должно менять исходные 230 сделок после завершения работы.
