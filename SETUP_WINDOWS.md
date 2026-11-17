# Установка SQL Server и первый запуск в SSMS (Windows)

Цель: поднять **локальный SQL Server Database Engine**, подключиться через уже установленный SSMS, создать и заполнить тренировочную БД. SQL Server хранит и обрабатывает данные как служба Windows; **SSMS — интерфейс управления**. Одна установленная программа SSMS без службы SQL Server недостаточна.

## 1. Проверить, установлен ли сервер

Открой `Win + R` → `services.msc`. Найди **SQL Server (SQLEXPRESS)**, **SQL Server (MSSQLSERVER)** или другую службу вида **SQL Server (имя_экземпляра)**. Если служба есть и запущена, установку сервера можно пропустить. Имя в скобках = имя экземпляра. Иначе сервер ещё нужно установить.

В PowerShell можно посмотреть службы:
```powershell
Get-Service -Name 'MSSQL*' -ErrorAction SilentlyContinue
```
`MSSQLSERVER` — экземпляр по умолчанию; `MSSQL$SQLEXPRESS` — именованный экземпляр Express.

## 2. Если сервера нет — установить бесплатный Express

1. Открой [официальную страницу SQL Server 2025 Express](https://www.microsoft.com/en-us/download/details.aspx?id=108836). Запусти установщик.
2. Выбери установку Database Engine (обычно Basic либо Custom). При Custom убедись, что выбран компонент **Database Engine Services**.
3. Для понятного учебного подключения рекомендуем **именованный экземпляр `SQLEXPRESS`** (если используется Basic, проверь имя экземпляра в финальном окне).
4. На шаге Database Engine Configuration выбери **Windows authentication mode** и добавь текущего пользователя Windows в SQL Server administrators (если этот шаг доступен).
5. Заверши установку, проверь, что служба `SQL Server (SQLEXPRESS)` запущена. При другом имени экземпляра используй его во всех шагах ниже.

Для обучения подойдёт и установленный ранее **SQL Server Developer** или SQL Server 2019/2022+. Не устанавливай второй экземпляр, если локальный сервер уже есть и к нему можно подключиться.

## 3. Подключиться в SSMS

Запусти SSMS → **Connect > Database Engine** (название пункта может отличаться в версии интерфейса).

- **Server type:** Database Engine.
- **Server name:** `.\\SQLEXPRESS` (экземпляр Express) или `localhost\\SQLEXPRESS`; для экземпляра по умолчанию — `localhost` или `.`.
- **Authentication:** Windows Authentication.
- **Database:** пока `master` / Default.

В новых версиях SSMS подключение может требовать шифрование. Для **локального учебного экземпляра с самоподписанным сертификатом** при ошибке проверки цепочки можно включить **Trust server certificate** в параметрах соединения. Для удалённых/производственных серверов не отключай проверку сертификата; настрой доверенный сертификат.

Нажми **Connect**. В Object Explorer должен появиться сервер и узел **Databases**. Щёлкни **New Query**, запусти через F5:

```sql
SELECT @@SERVERNAME AS server_name,
       @@VERSION AS server_version,
       DB_NAME() AS current_database;
GO
```

**`GO` — команда SSMS для разделения пакетов, не оператор SQL Server.** Для SQL Server запрос `SELECT ...` выполняется на сервере; SSMS лишь отправляет текст.

## 4. Забрать курс в C:\\SQL

В PowerShell:

```powershell
git clone https://github.com/Vitac86/SQL_training.git C:\\SQL
```

Если `C:\\SQL` уже является рабочей копией репозитория:

```powershell
Set-Location C:\\SQL
git pull origin main
```

Если Git ещё не установлен, скачай его с [git-scm.com](https://git-scm.com/download/win). Команда `git clone` с целевой папкой требует, чтобы она не существовала или была пустой. Если `C:\\SQL` уже непустая папка **без Git**, сначала выбери другую папку или перемести её содержимое.

## 5. Создать базу данных и импортировать учебный набор

Через **File → Open → File** открой в SSMS **каждый** файл и запусти **F5** в следующем порядке:

1. `C:\\SQL\\database\\00_create_database.sql`
2. `C:\\SQL\\database\\01_schema.sql`
3. `C:\\SQL\\database\\02_seed_data.sql`
4. `C:\\SQL\\database\\03_check.sql`

Первый скрипт создаёт БД `TradingTraining`, второй — таблицы, третий — исходные данные из CSV в готовых T-SQL INSERT, четвёртый — их проверку. **Импорт с диска SQL Server через BULK INSERT не требуется**, поэтому разрешения SQL-службы на `C:\\SQL` не мешают запуску.

Открой **Object Explorer → Databases → TradingTraining → Tables**. Если база не видна, выбери **Refresh**. Для ручной проверки:

```sql
USE TradingTraining;
GO
SELECT TOP (10) * FROM dbo.trades ORDER BY trade_id;
SELECT COUNT(*) AS trades_count FROM dbo.trades;
GO
```

Ожидается **230 сделок**.

## 6. Типовые проблемы

- **Не найден сервер / error 40 / network-related:** служба SQL Server не запущена либо введено неверное имя экземпляра. Проверь имя через `services.msc` и строку Server name.
- **Login failed for user / 18456:** проверь Windows Authentication и права пользователя на экземпляре. Если при установке не добавил себя как SQL admin, исправь конфигурацию авторизованным администратором.
- **Certificate chain was issued by an authority that is not trusted:** для локальной тренировочной БД — флажок **Trust server certificate**; для production нужно правильное доверие сертификату.
- **Cannot open database / invalid object name:** сначала запусти `00_create_database.sql` и `01_schema.sql`; убедись в `USE TradingTraining;`.
- **Выполнен второй seed:** он намеренно прерывается, чтобы не получить дубли. Для повторной загрузки используй защищённый скрипт `99_reset_data.sql`, затем `02_seed_data.sql`.
- **Текст на русском выводится некорректно:** используется тип `NVARCHAR` и Unicode-литералы `N'...'` — не заменяй их на обычные `VARCHAR`/литералы при создании таблиц.

## 7. Точки проверки перед обучением

Убедись, что `DB_NAME()` возвращает `TradingTraining`, в базе пять таблиц, `trades` содержит 230 строк, `instruments` — 12, `accounts` — 6. Далее переходи к [плану курса](LEARNING_PATH.md).
