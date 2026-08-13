"""
03_pandas_analysis.py
======================

Знакомство с pandas на торговых данных из trading_training.db.

Важно чётко разделять четыре разные вещи, которые легко перепутать в начале обучения:

    SQLite    = файл базы данных (движок, который хранит таблицы на диске)
    SQL       = язык запросов, на котором мы просим у SQLite нужные данные
    Python    = язык программирования, в котором мы пишем этот скрипт
    DataFrame = объект pandas, который живёт в ОПЕРАТИВНОЙ ПАМЯТИ Python,
                после того как данные уже прочитаны из SQLite

То есть путь данных такой:

    trading_training.db (SQLite, на диске)
            |  SQL-запрос
            v
       pandas.DataFrame (в памяти Python)
            |  .groupby(), .sum(), .mean() и т.д.
            v
       результат анализа (тоже DataFrame или Series)

После того как данные попали в DataFrame, SQLite и SQL нам уже не нужны —
дальнейшая работа идёт средствами pandas, а не SQL.

Запуск:
    python 03_pandas_analysis.py
"""

import sqlite3
from pathlib import Path

import pandas as pd

DB_PATH = Path(__file__).resolve().parent / "trading_training.db"


def load_dataframe(conn, sql):
    """SQL -> DataFrame. pandas сам выполнит запрос и соберёт результат в таблицу."""
    return pd.read_sql_query(sql, conn)


def main():
    conn = sqlite3.connect(DB_PATH)

    # ------------------------------------------------------------------
    # Шаг 1. SQL -> DataFrame
    # ------------------------------------------------------------------
    # pd.read_sql_query() отправляет SQL в SQLite и возвращает результат
    # уже в виде pandas.DataFrame — таблицы, с которой удобно работать в Python.
    sql = """
        SELECT
            t.trade_id,
            t.trade_date,
            t.side,
            t.quantity,
            t.price,
            t.commission,
            t.quantity * t.price AS turnover,
            i.ticker,
            i.asset_class,
            a.account_name
        FROM trades AS t
        JOIN instruments AS i ON i.instrument_id = t.instrument_id
        JOIN accounts    AS a ON a.account_id    = t.account_id;
    """
    trades = load_dataframe(conn, sql)

    print("--- DataFrame с сделками ---")
    print("Размер (строки, колонки):", trades.shape)
    print(trades.head(5))

    # ------------------------------------------------------------------
    # Шаг 2. Базовые операции DataFrame (уже не SQL, а pandas)
    # ------------------------------------------------------------------
    print("\n--- Общий оборот по всем сделкам ---")
    total_turnover = trades["turnover"].sum()
    print(f"Суммарный оборот: {total_turnover:,.2f} RUB")

    # ------------------------------------------------------------------
    # Шаг 3. groupby — аналог SQL GROUP BY, но на стороне pandas
    # ------------------------------------------------------------------
    print("\n--- Оборот по тикерам (top 5) ---")
    by_ticker = (
        trades.groupby("ticker")["turnover"]
        .sum()
        .sort_values(ascending=False)
        .head(5)
    )
    print(by_ticker)

    print("\n--- Количество сделок и оборот по счетам ---")
    by_account = trades.groupby("account_name").agg(
        trades_count=("trade_id", "count"),
        turnover_sum=("turnover", "sum"),
        commission_sum=("commission", "sum"),
    )
    print(by_account)

    print("\n--- Покупки против продаж по объёму сделок ---")
    by_side = trades.groupby("side")["turnover"].agg(["count", "sum", "mean"])
    print(by_side)

    # ------------------------------------------------------------------
    # Шаг 4. Работа с датами: временной ряд цен
    # ------------------------------------------------------------------
    print("\n--- Дневные цены SBER: последние 5 наблюдений ---")
    prices_sql = """
        SELECT dp.price_date, dp.close_price, dp.volume
        FROM daily_prices AS dp
        JOIN instruments AS i ON i.instrument_id = dp.instrument_id
        WHERE i.ticker = 'SBER'
        ORDER BY dp.price_date;
    """
    sber_prices = load_dataframe(conn, prices_sql)

    # pd.to_datetime превращает текстовую колонку с датами в настоящий тип "дата",
    # с которым pandas умеет правильно сортировать и считать интервалы.
    sber_prices["price_date"] = pd.to_datetime(sber_prices["price_date"])
    print(sber_prices.tail(5))

    # pct_change() считает изменение цены в процентах к предыдущему дню —
    # типичная задача для анализа временного ряда котировок.
    sber_prices["daily_return"] = sber_prices["close_price"].pct_change()
    print("\nСредняя дневная доходность SBER за период:",
          f"{sber_prices['daily_return'].mean():.4%}")

    conn.close()


if __name__ == "__main__":
    main()
