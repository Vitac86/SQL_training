# Ответы к уроку 0

Не подглядывайте сюда, пока не попробовали выполнить задания сами в `lesson_00.db`.
Ошибка, которую вы вызвали и увидели своими глазами — часть обучения, а не сбой.

---

### 1. Таблица accounts_demo

```sql
CREATE TABLE accounts_demo (
    account_id      INTEGER PRIMARY KEY,
    account_name    TEXT NOT NULL UNIQUE,
    risk_limit_rub  REAL NOT NULL CHECK (risk_limit_rub > 0)
);

INSERT INTO accounts_demo (account_name, risk_limit_rub) VALUES ('ACC-DEMO-1', 1000000);
INSERT INTO accounts_demo (account_name, risk_limit_rub) VALUES ('ACC-DEMO-2', 2000000);
```

`account_id` не указан явно — так как это `INTEGER PRIMARY KEY`, SQLite назначит его сам
(1 и 2 соответственно), см. раздел урока про `rowid`.

---

### 2. Повторный account_name

```sql
INSERT INTO accounts_demo (account_name, risk_limit_rub) VALUES ('ACC-DEMO-1', 500000);
```

Ожидаемая ошибка:

```text
Runtime error: UNIQUE constraint failed: accounts_demo.account_name
```

Защищает правило `UNIQUE` на `account_name`. Без него в базе могли бы существовать два
разных счёта с одинаковым именем `'ACC-DEMO-1'` — и любой отчёт или запрос "найди счёт
ACC-DEMO-1" стал бы неоднозначным: непонятно, какой из двух счетов имелся в виду.

---

### 3. account_name = NULL

```sql
INSERT INTO accounts_demo (account_name, risk_limit_rub) VALUES (NULL, 500000);
```

Ожидаемая ошибка:

```text
Runtime error: NOT NULL constraint failed: accounts_demo.account_name
```

Защищает правило `NOT NULL`. Счёт без имени бессмысленен для практической работы — его
невозможно было бы ни найти, ни сослаться на него в отчётах.

---

### 4. risk_limit_rub = -500

```sql
INSERT INTO accounts_demo (account_name, risk_limit_rub) VALUES ('ACC-DEMO-3', -500);
```

Ожидаемая ошибка:

```text
Runtime error: CHECK constraint failed: accounts_demo
```

Защищает `CHECK (risk_limit_rub > 0)`. Отрицательный лимит риска не имеет экономического
смысла: лимит — это ограничение "сверху", а не долг или обязательство, поэтому он не может
быть отрицательным числом.

---

### 5. Транзакция с ROLLBACK

```sql
SELECT risk_limit_rub FROM accounts_demo WHERE account_name = 'ACC-DEMO-1'; -- запомнить значение, например 1000000

BEGIN;
UPDATE accounts_demo SET risk_limit_rub = 9999999 WHERE account_name = 'ACC-DEMO-1';
SELECT risk_limit_rub FROM accounts_demo WHERE account_name = 'ACC-DEMO-1'; -- покажет 9999999
ROLLBACK;

SELECT risk_limit_rub FROM accounts_demo WHERE account_name = 'ACC-DEMO-1'; -- снова 1000000
```

После `ROLLBACK` значение возвращается к тому, что было до `BEGIN` — как будто `UPDATE`
никогда не выполнялся.

---

### 6. Транзакция с COMMIT

```sql
BEGIN;
UPDATE accounts_demo SET risk_limit_rub = 9999999 WHERE account_name = 'ACC-DEMO-1';
COMMIT;

SELECT risk_limit_rub FROM accounts_demo WHERE account_name = 'ACC-DEMO-1'; -- 9999999, зафиксировано навсегда
```

После `COMMIT` изменение сохранено окончательно. `ROLLBACK` после `COMMIT` уже ничего не
отменит — транзакция закрыта.

---

### 7. Своими словами про PRIMARY KEY

Пример ожидаемого ответа (формулировка может отличаться, важна суть):

> Если бы в `instruments` разрешалось иметь две строки с одинаковым `instrument_id = 1`
> (например, одну для SBER, другую для ROSN), то любая сделка со ссылкой
> `trades.instrument_id = 1` стала бы неоднозначной: невозможно было бы определить, идёт
> ли речь о покупке Сбербанка или Роснефти, не зная дополнительно, какая именно из двух
> одинаковых строк "правильная". `PRIMARY KEY` на `instrument_id` физически не позволяет
> такой ситуации возникнуть — SQLite отклоняет вторую попытку записать `instrument_id = 1`
> с ошибкой `UNIQUE constraint failed`, что мы и увидели в разделе 3 урока.
