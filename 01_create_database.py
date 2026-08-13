"""
01_create_database.py
======================

Пересоздаёт учебную базу `trading_training.db` из CSV-файлов в папке data/.

Запуск:
    python 01_create_database.py

Что делает скрипт:
    1. Удаляет старый файл базы, если он есть (чтобы всегда стартовать с чистого листа).
    2. Создаёт таблицы instruments, accounts, trades, daily_prices, liquidity
       с первичными ключами, внешними ключами и ограничениями (CHECK, NOT NULL, UNIQUE).
    3. Загружает данные из data/*.csv.
    4. Проверяет целостность базы (PRAGMA integrity_check, PRAGMA foreign_key_check).

Используется только стандартная библиотека Python (модуль sqlite3 и csv) —
никаких внешних зависимостей для этого шага не требуется.
"""

import csv
import sqlite3
from pathlib import Path

# Папка проекта и путь к файлам
PROJECT_DIR = Path(__file__).resolve().parent
DB_PATH = PROJECT_DIR / "trading_training.db"
DATA_DIR = PROJECT_DIR / "data"

SCHEMA_SQL = """
CREATE TABLE instruments (
    instrument_id INTEGER PRIMARY KEY,
    ticker        TEXT NOT NULL UNIQUE,
    name          TEXT NOT NULL,
    asset_class   TEXT NOT NULL,
    currency      TEXT NOT NULL
);

CREATE TABLE accounts (
    account_id      INTEGER PRIMARY KEY,
    account_name    TEXT NOT NULL UNIQUE,
    strategy        TEXT NOT NULL,
    risk_limit_rub  REAL NOT NULL CHECK (risk_limit_rub > 0)
);

CREATE TABLE trades (
    trade_id       INTEGER PRIMARY KEY,
    trade_date     TEXT NOT NULL,
    account_id     INTEGER NOT NULL,
    instrument_id  INTEGER NOT NULL,
    side           TEXT NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity       INTEGER NOT NULL CHECK (quantity > 0),
    price          REAL NOT NULL CHECK (price > 0),
    commission     REAL NOT NULL CHECK (commission >= 0),

    FOREIGN KEY (account_id)    REFERENCES accounts(account_id),
    FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
);

CREATE TABLE daily_prices (
    price_date     TEXT NOT NULL,
    instrument_id  INTEGER NOT NULL,
    close_price    REAL NOT NULL CHECK (close_price > 0),
    volume         INTEGER NOT NULL CHECK (volume >= 0),

    PRIMARY KEY (price_date, instrument_id),
    FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
);

CREATE TABLE liquidity (
    price_date           TEXT NOT NULL,
    instrument_id         INTEGER NOT NULL,
    turnover_rub          REAL NOT NULL CHECK (turnover_rub >= 0),
    trades_count           INTEGER NOT NULL CHECK (trades_count >= 0),
    bid_ask_spread_bps    REAL NOT NULL CHECK (bid_ask_spread_bps >= 0),

    PRIMARY KEY (price_date, instrument_id),
    FOREIGN KEY (instrument_id) REFERENCES instruments(instrument_id)
);
"""

# Какие CSV в какие таблицы грузим и в каком порядке (важно из-за внешних ключей:
# сначала справочники instruments/accounts, потом таблицы, которые на них ссылаются)
TABLES_IN_LOAD_ORDER = [
    ("instruments", "instruments.csv"),
    ("accounts", "accounts.csv"),
    ("trades", "trades.csv"),
    ("daily_prices", "daily_prices.csv"),
    ("liquidity", "liquidity.csv"),
]


def load_csv_into_table(conn, table_name, csv_path):
    with open(csv_path, encoding="utf-8", newline="") as f:
        reader = csv.reader(f)
        header = next(reader)
        placeholders = ", ".join("?" for _ in header)
        columns = ", ".join(header)
        sql = f"INSERT INTO {table_name} ({columns}) VALUES ({placeholders})"
        rows = list(reader)
        conn.executemany(sql, rows)
    print(f"  {table_name}: загружено строк -> {len(rows)}")


def main():
    if DB_PATH.exists():
        DB_PATH.unlink()
        print(f"Старый файл базы удалён: {DB_PATH.name}")

    # sqlite3.connect создаёт новый пустой файл базы, если его ещё нет
    conn = sqlite3.connect(DB_PATH)
    try:
        # Внешние ключи в SQLite по умолчанию выключены для каждого нового соединения —
        # включаем их явно, иначе FOREIGN KEY в схеме будет просто "декорацией".
        conn.execute("PRAGMA foreign_keys = ON;")

        print("Создание таблиц...")
        conn.executescript(SCHEMA_SQL)

        print("Загрузка данных из CSV:")
        for table_name, csv_file in TABLES_IN_LOAD_ORDER:
            load_csv_into_table(conn, table_name, DATA_DIR / csv_file)

        conn.commit()

        print("\nПроверка целостности базы:")
        integrity = conn.execute("PRAGMA integrity_check;").fetchone()[0]
        print(f"  PRAGMA integrity_check -> {integrity}")

        fk_problems = conn.execute("PRAGMA foreign_key_check;").fetchall()
        print(f"  PRAGMA foreign_key_check -> нарушений: {len(fk_problems)}")
        if fk_problems:
            for row in fk_problems:
                print("   ", row)

        print(f"\nГотово. База создана: {DB_PATH}")
    finally:
        # Соединение обязательно закрываем, даже если что-то пошло не так
        conn.close()


if __name__ == "__main__":
    main()
