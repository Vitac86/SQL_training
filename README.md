# SQL Training — Microsoft SQL Server + SSMS

Практический курс **реляционных баз данных и T-SQL** на учебных финансовых данных. Основная среда — **Microsoft SQL Server (Database Engine)** и **SQL Server Management Studio (SSMS)** на Windows. Python, SQLite и сторонние библиотеки для основной программы **не требуются**.

> Важно: **SSMS не является сервером баз данных**. Если установлен только SSMS, сначала нужно установить SQL Server Express или Developer. Смотри [SETUP_WINDOWS.md](SETUP_WINDOWS.md).

## Быстрый старт

1. Установить или проверить локальный экземпляр SQL Server и подключиться к нему через SSMS: [SETUP_WINDOWS.md](SETUP_WINDOWS.md).
2. Скачать репозиторий в `C:\\SQL`:
   ```powershell
   git clone https://github.com/Vitac86/SQL_training.git C:\\SQL
   ```
   Если репозиторий уже склонирован в эту папку:
   ```powershell
   cd C:\\SQL
   git pull origin main
   ```
3. В SSMS открыть и **по очереди** выполнить (клавиша **F5**):
   - `database/00_create_database.sql` — создать учебную базу `TradingTraining`.
   - `database/01_schema.sql` — создать пять таблиц со связями и ограничениями.
   - `database/02_seed_data.sql` — загрузить сохранённые финансовые данные из исходного курса.
   - `database/03_check.sql` — проверить количество строк и целостность.
4. Открыть [LEARNING_PATH.md](LEARNING_PATH.md), начать с [урока 00](lessons/00_введение_в_субд.md) и выполнять задания в своём файле `practice.sql`.
5. Для дополнительной практики — [exercises.sql](exercises.sql); решения — [answers.sql](answers.sql). Не открывай решения до попытки решить самостоятельно.

**Никаких `sqlite3`, `LIMIT`, `PRAGMA`, `.tables` и файлов `.db` в основном курсе нет.** Запросы рассчитаны на диалект **T-SQL (SQL Server 2019+; рекомендован SQL Server 2025 Express)**.

## Состав учебной БД

База `TradingTraining` содержит пять взаимосвязанных таблиц:

- `dbo.instruments`: финансовые инструменты и тикеры (12 записей);
- `dbo.accounts`: учебные клиентские/торговые счета (6);
- `dbo.trades`: сделки, сторона BUY/SELL, количество, цена, комиссия (230);
- `dbo.daily_prices`: дневные цены закрытия и объёмы;
- `dbo.liquidity`: оборот, число сделок и спреды.

В отличие от финансовой production-БД, данные **синтетические**, не предназначены для торговых решений. Файл `database/02_seed_data.sql` содержит готовые T-SQL INSERT, собранные из оригинальных CSV в `data/`; загрузка сервером локальных CSV не требуется. Пара `TEST0` (инструмент без сделок) и `ACC-DORMANT` (счёт без сделок) нужна для задач на `LEFT JOIN`.

### Схема связей

```text
instruments (instrument_id PK) ----< trades (instrument_id FK)
           |                       trades (account_id FK) >---- accounts (account_id PK)
           +--------------------< daily_prices (instrument_id FK, price_date+instrument_id PK)
           +--------------------< liquidity    (instrument_id FK, price_date+instrument_id PK)
```

Связи **один-ко-многим**: одна запись справочника может ссылаться из нескольких сделок. Ключи ограничивают допустимые данные; `JOIN` читает связанные строки и не создаёт физическую связь.

## Что ты изучишь

Фундамент: устройство СУБД, клиент/сервер, реляционная модель, схемы, типы данных, ключи и ограничения, нормализация, NULL, трёхзначная логика, транзакции/ACID, изоляция и блокировки, индексы и планы выполнения, безопасность и резервирование.

Практика: `SELECT`, фильтрация, агрегаты, `JOIN`, `INSERT/UPDATE/DELETE`, `CTE`, подзапросы, оконные функции, временные таблицы, представления, процедуры, проверка качества, анализ финансовых данных и итоговый проект.

## Расположение файлов

```text
SQL_training/
├── README.md, SETUP_WINDOWS.md, LEARNING_PATH.md
├── database/
│   ├── 00_create_database.sql
│   ├── 01_schema.sql
│   ├── 02_seed_data.sql
│   ├── 03_check.sql
│   ├── 04_indexes_optional.sql
│   └── 99_reset_data.sql
├── lessons/00_...md — 18 последовательных уроков
├── exercises.sql — задания без решений
├── answers.sql — самостоятельная проверка
├── practice.sql — личная песочница
├── data/*.csv — исходный синтетический датасет (справочно)
└── archive/sqlite/ — предыдущая версия курса, не нужна для выполнения
```

## Правила безопасности

Все эксперименты выполняй **только на `TradingTraining`**. Перед `UPDATE`/`DELETE` используй `SELECT` с тем же `WHERE`, а изменение для учебного задания оборачивай в транзакцию с `ROLLBACK`. `database/99_reset_data.sql` по умолчанию **ничего не удаляет**, пока ты не включишь явное подтверждение.

## Первоисточники

- [Microsoft: загрузка SQL Server Express](https://www.microsoft.com/en-us/download/details.aspx?id=108836)
- [Microsoft: установка SSMS](https://learn.microsoft.com/en-us/ssms/install/install)
- [Microsoft Learn: Transact-SQL](https://learn.microsoft.com/en-us/sql/t-sql/language-reference)
- [Microsoft Learn: Database Engine](https://learn.microsoft.com/en-us/sql/relational-databases/databases/databases)
