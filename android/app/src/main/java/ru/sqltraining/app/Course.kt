package ru.sqltraining.app

data class Lesson(val id:Int,val title:String,val level:String,val description:String,val example:String)
val lessons=listOf(
Lesson(0,"Знакомство с базой данных","Основы","Таблица — строки и столбцы. SQL позволяет задавать вопросы к данным.","SELECT * FROM instruments;"),
Lesson(1,"SELECT и столбцы","Основы","SELECT выбирает нужные столбцы, FROM указывает таблицу.","SELECT ticker,currency FROM instruments;"),
Lesson(2,"Сортировка и TOP","Основы","ORDER BY сортирует строки. В офлайн-движке используется LIMIT.","SELECT ticker,name FROM instruments ORDER BY ticker LIMIT 5;"),
Lesson(3,"WHERE","Основы","WHERE оставляет строки, подходящие под условие.","SELECT * FROM trades WHERE side='BUY';"),
Lesson(4,"AND, OR, IN, BETWEEN","Основы","Логические условия объединяют несколько проверок.","SELECT * FROM trades WHERE side='BUY' AND quantity BETWEEN 10 AND 100;"),
Lesson(5,"NULL","Основы","NULL означает отсутствие значения. Проверяй его через IS NULL.","SELECT * FROM trades WHERE commission IS NOT NULL;"),
Lesson(6,"Типы данных","Основы","Тип столбца определяет допустимые значения: числа, текст, даты и десятичные числа.","SELECT typeof(price),price FROM trades LIMIT 5;"),
Lesson(7,"Первичный ключ","Основы","PRIMARY KEY однозначно определяет запись таблицы.","SELECT * FROM accounts ORDER BY account_id;"),
Lesson(8,"Внешний и составной ключ","Основы","FOREIGN KEY связывает таблицы. Составной ключ уникален как комбинация столбцов.","SELECT price_date,instrument_id FROM daily_prices LIMIT 10;"),
Lesson(9,"Ограничения","Основы","NOT NULL, UNIQUE, CHECK и FOREIGN KEY защищают данные.","SELECT sql FROM sqlite_master WHERE type='table' AND name='trades';"),
Lesson(10,"Агрегаты","Практика","COUNT, SUM и AVG считают показатели по набору строк.","SELECT side,COUNT(*) cnt FROM trades GROUP BY side;"),
Lesson(11,"GROUP BY и HAVING","Практика","GROUP BY формирует группы, HAVING фильтрует рассчитанные группы.","SELECT account_id,COUNT(*) cnt FROM trades GROUP BY account_id HAVING COUNT(*)>10;"),
Lesson(12,"INNER JOIN","Практика","JOIN соединяет записи по общему ключу.","SELECT t.trade_id,a.account_name FROM trades t JOIN accounts a ON a.account_id=t.account_id LIMIT 10;"),
Lesson(13,"LEFT JOIN","Практика","LEFT JOIN сохраняет строки левой таблицы, даже если справа нет пары.","SELECT i.ticker,COUNT(t.trade_id) cnt FROM instruments i LEFT JOIN trades t ON t.instrument_id=i.instrument_id GROUP BY i.ticker;"),
Lesson(14,"Подзапросы","Практика","Подзапрос — SELECT внутри другого запроса.","SELECT trade_id,price FROM trades WHERE price>(SELECT AVG(price) FROM trades) LIMIT 10;"),
Lesson(15,"CTE","Практика","CTE задаёт имя промежуточному результату одного запроса.","WITH fees AS (SELECT account_id,SUM(commission) total_fee FROM trades GROUP BY account_id) SELECT * FROM fees;"),
Lesson(16,"CASE","Практика","CASE выбирает значение по условию, как если/иначе.","SELECT trade_id,CASE WHEN side='BUY' THEN 'Покупка' ELSE 'Продажа' END side_name FROM trades LIMIT 10;"),
Lesson(17,"Даты","Практика","Для периода используй начало периода включительно и начало следующего периода не включительно.","SELECT * FROM trades WHERE trade_date>='2024-01-10' AND trade_date<'2024-01-15';"),
Lesson(18,"Оконные функции","Продвинутый","Оконная функция считает значение по связанным строкам, не объединяя их в одну.","SELECT trade_id,price,ROW_NUMBER() OVER(ORDER BY price DESC) rn FROM trades LIMIT 10;"),
Lesson(19,"LAG и накопительные суммы","Продвинутый","LAG получает предыдущее значение; SUM OVER считает накопительный итог.","SELECT trade_date,commission,SUM(commission) OVER(ORDER BY trade_date,trade_id) running_fee FROM trades LIMIT 20;"),
Lesson(20,"UNION","Продвинутый","UNION объединяет выборки и удаляет дубли; UNION ALL сохраняет все строки.","SELECT ticker code FROM instruments UNION SELECT account_name FROM accounts;"),
Lesson(21,"VIEW","Продвинутый","VIEW — сохранённый запрос, к которому можно обращаться как к таблице.","CREATE VIEW v_trade_accounts AS SELECT t.trade_id,a.account_name FROM trades t JOIN accounts a ON a.account_id=t.account_id;"),
Lesson(22,"Индексы","Продвинутый","Индекс ускоряет поиск, но требует места и обновляется при изменении данных.","CREATE INDEX ix_trades_account_date ON trades(account_id,trade_date);"),
Lesson(23,"Транзакции","Продвинутый","Транзакция объединяет изменения; ROLLBACK отменяет их до фиксации.","BEGIN; UPDATE accounts SET risk_limit_rub=risk_limit_rub+100 WHERE account_id=6; ROLLBACK;"),
Lesson(24,"Итоговая аналитика","Продвинутый","Объедини JOIN и агрегаты для отчёта по активности счетов.","SELECT a.account_name,COUNT(t.trade_id) trades_count,SUM(t.commission) fees FROM accounts a LEFT JOIN trades t ON t.account_id=a.account_id GROUP BY a.account_id,a.account_name ORDER BY fees DESC;")
)