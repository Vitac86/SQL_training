"""
02_python_and_sql.py
=====================

Самый простой пример работы Python с SQLite.
Цель — не "красивый код", а разбор каждого шага: что это такое и зачем оно нужно.

Запуск:
    python 02_python_and_sql.py
"""

# `import` подключает к текущему файлу код из другого модуля.
# `sqlite3` — модуль из стандартной библиотеки Python: он умеет открывать
# файлы .db и выполнять в них SQL-запросы. Устанавливать ничего не нужно.
import sqlite3

# `pathlib.Path` — удобный способ работать с путями к файлам, независимо от ОС.
from pathlib import Path

# Переменная — это просто имя, под которым мы сохраняем значение,
# чтобы не повторять его много раз. Здесь мы сохраняем путь к нашей базе.
DB_PATH = Path(__file__).resolve().parent / "trading_training.db"


def example_1_connect_and_close():
    """Базовое подключение: открыть соединение и закрыть его вручную."""
    print("\n--- Пример 1: подключение и закрытие ---")

    # sqlite3.connect(...) открывает файл базы данных (или создаёт новый,
    # если файла ещё нет) и возвращает объект "соединение" (connection).
    # Соединение — это канал связи между Python-программой и файлом базы.
    conn = sqlite3.connect(DB_PATH)

    # Через соединение нельзя выполнять SQL напрямую — для этого нужен "курсор" (cursor).
    # Курсор — это объект, который умеет отправлять SQL-команды и получать результат.
    cursor = conn.cursor()

    # execute() отправляет одну SQL-команду (SQL-строку) в базу.
    cursor.execute("SELECT COUNT(*) FROM instruments;")

    # fetchone() возвращает ОДНУ строку результата (здесь результат — одно число).
    # Результат — это кортеж (tuple), даже если в нём одно значение,
    # поэтому мы берём первый элемент через [0].
    row = cursor.fetchone()
    print("Количество инструментов в справочнике:", row[0])

    # Соединение обязательно нужно закрыть, чтобы освободить файл базы.
    conn.close()


def example_2_parameters_and_fetchall():
    """Параметризованный запрос и получение сразу всех строк результата."""
    print("\n--- Пример 2: параметр '?' и fetchall ---")

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Символ '?' — это "параметр": заглушка, вместо которой SQLite безопасно
    # подставит значение из кортежа ('RUB',). Так делать НАМНОГО безопаснее,
    # чем собирать SQL-строку через f-строку/сложение — это защищает от
    # SQL-инъекций и от ошибок с кавычками и типами.
    sql = "SELECT ticker, name FROM instruments WHERE currency = ? ORDER BY ticker;"
    cursor.execute(sql, ("RUB",))

    # fetchall() возвращает СРАЗУ ВСЕ строки результата в виде списка кортежей.
    rows = cursor.fetchall()

    print(f"Инструментов в рублях: {len(rows)}")

    # Цикл `for` перебирает список строк одну за другой.
    # Каждая строка — кортеж (ticker, name), поэтому мы сразу "распаковываем"
    # его в две переменные.
    for ticker, name in rows:
        print(f"  {ticker:15s} {name}")

    conn.close()


def example_3_context_manager():
    """Тот же запрос, но с 'with' — безопасный способ работать с соединением."""
    print("\n--- Пример 3: 'with' (context manager) ---")

    # 'with sqlite3.connect(...) as conn' — это конструкция, которая
    # ГАРАНТИРОВАННО закроет соединение, даже если внутри блока произойдёт ошибка.
    # Это надёжнее, чем вызывать conn.close() вручную в конце функции.
    with sqlite3.connect(DB_PATH) as conn:
        cursor = conn.cursor()
        cursor.execute(
            "SELECT side, COUNT(*) FROM trades GROUP BY side ORDER BY side;"
        )
        rows = cursor.fetchall()

        for side, count in rows:
            print(f"  {side}: {count} сделок")

        # commit() фиксирует изменения в базе. Здесь мы ничего не меняли
        # (только читали данные через SELECT), поэтому commit не обязателен,
        # но если бы мы делали INSERT/UPDATE/DELETE — без commit() изменения
        # не сохранились бы на диск при закрытии соединения без ошибок.
        conn.commit()
    # Здесь, после выхода из блока 'with', соединение уже закрыто автоматически.


if __name__ == "__main__":
    example_1_connect_and_close()
    example_2_parameters_and_fetchall()
    example_3_context_manager()
