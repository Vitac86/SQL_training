# Ответы к уроку 1

Не подглядывайте сюда, пока не попробовали выполнить задания сами в SQLTools, на
`practice.sql`.

---

### 1. Все строки instruments

```sql
SELECT *
FROM instruments;
```

Вернёт все 12 строк со всеми колонками: `instrument_id`, `ticker`, `name`, `asset_class`,
`currency`.

---

### 2. ticker, name, asset_class

```sql
SELECT
    ticker,
    name,
    asset_class
FROM instruments;
```

Те же 12 строк, но только три указанные колонки, в указанном порядке — `currency` и
`instrument_id` в результат не попадают, хотя в таблице они есть.

---

### 3. account_name, strategy, risk_limit_rub

```sql
SELECT
    account_name,
    strategy,
    risk_limit_rub
FROM accounts;
```

Вернёт 6 строк — по одной на каждый счёт, включая `'ACC-DORMANT'`, у которого нет ни одной
сделки.

---

### 4. Основные колонки trades

Задание открытое — конкретный набор колонок мог отличаться. Разумный вариант:

```sql
SELECT
    trade_id,
    trade_date,
    side,
    quantity,
    price
FROM trades;
```

Здесь выбраны колонки, которые описывают саму сделку по существу (когда, что за сторона,
сколько, по какой цене). `account_id`, `instrument_id` и `commission` тоже можно было включить
— это уже вопрос вкуса, а не единственно верный ответ.

---

### 5. Список таблиц через sqlite_schema

```sql
SELECT
    name,
    type
FROM sqlite_schema
WHERE type = 'table'
ORDER BY name;
```

Вернёт 5 строк, в алфавитном порядке: `accounts`, `daily_prices`, `instruments`, `liquidity`,
`trades`.

---

### 6. Первичный ключ daily_prices

```sql
PRAGMA table_info('daily_prices');
```

В результате колонка `pk` ненулевая сразу у двух строк: `price_date` (`pk = 1`) и
`instrument_id` (`pk = 2`). Это и есть признак составного первичного ключа — он состоит не из
одной колонки, а из пары `(price_date, instrument_id)`. Экономически это означает: у одного
инструмента не может быть двух разных цен закрытия в один и тот же день, но при этом ни
`price_date`, ни `instrument_id` сами по себе не обязаны быть уникальными.

---

### 7. Внешние ключи trades

```sql
PRAGMA foreign_key_list('trades');
```

Вернёт 2 строки — у `trades` два внешних ключа:

- `instrument_id` → `instruments.instrument_id`;
- `account_id` → `accounts.account_id`.

(Порядок строк в результате может не совпадать с порядком объявления `FOREIGN KEY` в
`CREATE TABLE` — это нормально, ориентируйтесь на колонки `table`/`from`/`to`, а не на позицию
строки.)

---

### 8. Ручное сопоставление без JOIN

Пример для `trade_id = 1` (у вас мог быть выбран любой другой — принцип тот же).

Сначала смотрим саму сделку:

```sql
SELECT
    trade_id,
    account_id,
    instrument_id,
    side
FROM trades
WHERE trade_id = 1;
```

(`WHERE` подробно разберём в уроке 2 — здесь он используется только для того, чтобы указать
на одну конкретную сделку.) Результат: `account_id = 5`, `instrument_id = 3`, `side = 'SELL'`.

Дальше — тикер, отдельным запросом к `instruments`:

```sql
SELECT
    instrument_id,
    ticker,
    name
FROM instruments;
```

Среди строк находим `instrument_id = 3` → `ticker = 'LKOH'`.

И название счёта, отдельным запросом к `accounts`:

```sql
SELECT
    account_id,
    account_name,
    strategy
FROM accounts;
```

Среди строк находим `account_id = 5` → `account_name = 'ACC-FIXEDINCOME'`.

Итог: сделка №1 — это продажа (`SELL`) `LKOH` на счёте `ACC-FIXEDINCOME`. Ни один из трёх
запросов не использовал `JOIN` — сопоставление сделано глазами, по совпадающим `instrument_id`
и `account_id`.

---

### Задание повышенной сложности

Продолжая пример выше, для `trade_id = 1`:

```text
Сделка №1
Счёт: ACC-FIXEDINCOME
Инструмент: LKOH
Сторона: SELL
```

Если вы выбрали другую сделку — структура ответа та же, поменяются только конкретные значения.
Это упражнение специально сделано без `JOIN`: цель — почувствовать вручную ту работу
(«найти совпадающий ID и подставить рядом нужные колонки»), которую позже в уроке 4 возьмёт на
себя оператор `JOIN`.
