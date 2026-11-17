"""
04_import_excel.py
===================

Шаблон импорта: Excel -> pandas -> очистка -> SQLite.

Идея: на практике "чистых" данных не бывает. Файлы из брокерских
терминалов и учётных систем обычно содержат:
    - числа с запятой вместо точки как разделителя дробной части (1234,56);
    - числа с пробелами-разделителями тысяч (1 234,56);
    - лишние пробелы в текстовых полях (" SBER ", "BUY ");
    - даты в формате ДД.ММ.ГГГГ вместо ISO (ГГГГ-ММ-ДД).

Этот скрипт читает Excel-файл с НОВЫМИ сделками, приводит данные
к тому виду, который ожидает таблица trades, и добавляет их в базу.

Как потренироваться самостоятельно:
    1. Создайте файл data/new_trades_to_import.xlsx с колонками:
       trade_date, account_name, ticker, side, quantity, price, commission
       (даты можно писать как 15.01.2024, числа - как 1 234,56).
    2. Запустите: python 04_import_excel.py
    3. Проверьте таблицу trades в базе - новые строки должны появиться.

Если файла data/new_trades_to_import.xlsx нет, скрипт ничего не сломает -
он просто объяснит, что нужно сделать, и завершится.
"""

import sqlite3
from pathlib import Path

import pandas as pd

PROJECT_DIR = Path(__file__).resolve().parent
DB_PATH = PROJECT_DIR / "trading_training.db"

# Файл, который скрипт пытается импортировать. Это не готовые учебные данные
# (они уже загружены через 01_create_database.py), а "входящий" файл, который
# вы создаёте сами для практики импорта.
INPUT_XLSX = PROJECT_DIR / "data" / "new_trades_to_import.xlsx"


def clean_russian_number(value):
    """
    Приводит число в "российском" текстовом формате к float.

    Примеры:
        "1 234,56"  -> 1234.56
        "1234,5"    -> 1234.5
        1234.5      -> 1234.5 (уже число - возвращаем как есть)
    """
    if pd.isna(value):
        return None
    if isinstance(value, (int, float)):
        return float(value)

    text = str(value).strip()
    # Убираем пробелы (в том числе неразрывные), которые используют как
    # разделитель тысяч: "1 234,56" -> "1234,56"
    text = text.replace(" ", "").replace(" ", "")
    # Заменяем десятичную запятую на точку: "1234,56" -> "1234.56"
    text = text.replace(",", ".")
    return float(text)


def clean_text(value):
    """Убирает лишние пробелы по краям текста и приводит к верхнему регистру,
    где это уместно (тикер, сторона сделки BUY/SELL)."""
    if pd.isna(value):
        return None
    return str(value).strip()


def clean_dataframe(df):
    """Очищает и нормализует "сырые" данные из Excel перед загрузкой в SQLite."""

    df = df.copy()

    # Убираем случайные пробелы в названиях колонок ("  price " -> "price")
    df.columns = [str(c).strip() for c in df.columns]

    # Текстовые поля: срезаем пробелы, приводим ticker/side к верхнему регистру
    df["account_name"] = df["account_name"].apply(clean_text)
    df["ticker"] = df["ticker"].apply(clean_text).str.upper()
    df["side"] = df["side"].apply(clean_text).str.upper()

    # Числовые поля: используем нашу функцию очистки "российских" чисел
    df["quantity"] = df["quantity"].apply(clean_russian_number)
    df["price"] = df["price"].apply(clean_russian_number)
    df["commission"] = df["commission"].apply(clean_russian_number)

    # Даты: pandas умеет распознать и "15.01.2024", и "2024-01-15", если явно
    # указать dayfirst=True (день идёт первым - привычный для России формат).
    df["trade_date"] = pd.to_datetime(df["trade_date"], dayfirst=True).dt.strftime("%Y-%m-%d")

    return df


def resolve_ids(conn, df):
    """
    Заменяет читаемые для человека 'ticker' и 'account_name' на числовые
    внешние ключи instrument_id и account_id, как того требует таблица trades.

    Если тикер или счёт не найден в справочниках - строка отбрасывается,
    а причина выводится в консоль (это защищает базу от "сирот" - сделок,
    которые ссылались бы на несуществующий инструмент или счёт).
    """
    instruments = pd.read_sql_query("SELECT instrument_id, ticker FROM instruments;", conn)
    accounts = pd.read_sql_query("SELECT account_id, account_name FROM accounts;", conn)

    merged = df.merge(instruments, on="ticker", how="left")
    merged = merged.merge(accounts, on="account_name", how="left")

    missing_instrument = merged["instrument_id"].isna()
    missing_account = merged["account_id"].isna()

    for _, row in merged[missing_instrument].iterrows():
        print(f"  Пропущена строка: неизвестный тикер '{row['ticker']}'")
    for _, row in merged[missing_account].iterrows():
        print(f"  Пропущена строка: неизвестный счёт '{row['account_name']}'")

    ok = merged[~missing_instrument & ~missing_account].copy()
    ok["instrument_id"] = ok["instrument_id"].astype(int)
    ok["account_id"] = ok["account_id"].astype(int)
    return ok


def main():
    if not INPUT_XLSX.exists():
        print(f"Файл не найден: {INPUT_XLSX}")
        print("Это ожидаемо, если вы ещё не создавали файл для практики импорта.")
        print("Чтобы попробовать импорт самостоятельно:")
        print("  1. Создайте data/new_trades_to_import.xlsx с колонками:")
        print("     trade_date, account_name, ticker, side, quantity, price, commission")
        print("  2. Запустите скрипт заново: python 04_import_excel.py")
        return

    print(f"Читаю файл: {INPUT_XLSX.name}")
    # pandas.read_excel требует пакет openpyxl для чтения .xlsx - он указан в requirements.txt
    raw = pd.read_excel(INPUT_XLSX)
    print(f"Прочитано строк (до очистки): {len(raw)}")

    cleaned = clean_dataframe(raw)

    with sqlite3.connect(DB_PATH) as conn:
        conn.execute("PRAGMA foreign_keys = ON;")

        resolved = resolve_ids(conn, cleaned)
        print(f"Строк готово к загрузке (после проверки тикеров и счетов): {len(resolved)}")

        if resolved.empty:
            print("Нет ни одной валидной строки для загрузки.")
            return

        # Определяем, с какого trade_id продолжать нумерацию
        max_id_row = conn.execute("SELECT COALESCE(MAX(trade_id), 0) FROM trades;").fetchone()
        next_id = max_id_row[0] + 1

        rows_to_insert = []
        for offset, row in enumerate(resolved.itertuples(index=False)):
            rows_to_insert.append((
                next_id + offset,
                row.trade_date,
                row.account_id,
                row.instrument_id,
                row.side,
                int(row.quantity),
                float(row.price),
                float(row.commission),
            ))

        conn.executemany(
            """
            INSERT INTO trades (trade_id, trade_date, account_id, instrument_id, side, quantity, price, commission)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
            """,
            rows_to_insert,
        )
        conn.commit()
        print(f"Загружено новых сделок: {len(rows_to_insert)}")


if __name__ == "__main__":
    main()
