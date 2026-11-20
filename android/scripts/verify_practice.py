#!/usr/bin/env python3
"""Offline exercise-catalog validation against the exact bundled seed data.

This script deliberately uses only Python's standard library. It runs every
reference SELECT, validates content metadata and guards against duplicate tasks.
"""
from __future__ import annotations

import collections
import json
import re
import sqlite3
from pathlib import Path

ANDROID = Path(__file__).resolve().parents[1]
APP = ANDROID / "app" / "src" / "main"
DATA = APP / "assets" / "practice_tasks.json"
SEED = APP / "assets" / "seed.sql"
SCHEMA_KT = APP / "java" / "ru" / "sqltraining" / "app" / "SqlDatabase.kt"


def main() -> None:
    catalog = json.loads(DATA.read_text(encoding="utf-8"))
    assert catalog["version"] == 1, "Unexpected catalog version"
    tasks = catalog["tasks"]
    assert len(tasks) >= 198, "Expected the complete 198-task course catalog"
    ids = [t["id"] for t in tasks]
    assert ids == list(range(1, len(tasks) + 1)), "Task IDs must be stable, consecutive and unique"
    # Historical task IDs are not the curriculum sequence. Lessons and
    # prerequisite SQL constructs determine the pedagogical order.
    lesson_order = sorted(tasks, key=lambda item: (item["lessonId"], item["difficulty"], item["id"]))
    assert [item["id"] for item in lesson_order[:3]] == [10, 11, 3], "Broken first lessons"
    prerequisites = (
        (2, r"\b(?:LIMIT|DISTINCT)\b|\bORDER\s+BY\b"),
        (3, r"\bWHERE\b"),
        (10, r"\b(?:COUNT|SUM|AVG|MIN|MAX|ROUND)\s*\("),
        (11, r"\bGROUP\s+BY\b|\bHAVING\b"),
        (12, r"\bJOIN\b"),
        (15, r"^\s*WITH\b"),
        (16, r"\bCASE\b"),
        (17, r"\bSTRFTIME\s*\("),
        (18, r"\b(?:ROW_NUMBER|RANK|DENSE_RANK)\s*\("),
        (19, r"\b(?:LAG|LEAD)\s*\("),
    )
    for item in tasks:
        for min_lesson, pattern in prerequisites:
            if re.search(pattern, item["expectedSql"], flags=re.I) and item["lessonId"] < min_lesson:
                raise AssertionError(
                    f"Task #{item['id']} uses a concept from lesson {min_lesson + 1} before it is taught"
                )
    refs = set()
    for item in tasks:
        assert 1 <= item["difficulty"] <= 3, item["id"]
        assert 0 <= item["lessonId"] <= 31, item["id"]
        assert item["topic"] and item["title"] and item["description"], item["id"]
        assert len(item["hints"]) == 3 and all(item["hints"]), item["id"]
        assert item["explanation"].strip(), item["id"]
        assert item["expectedSql"].strip(), item["id"]
        assert re.match(r"(?is)^\s*(SELECT|WITH)\b", item["expectedSql"]), item["id"]
        signature = re.sub(r"\s+", " ", item["expectedSql"].strip().lower())
        assert signature not in refs, f"Duplicate reference SQL: #{item['id']}"
        refs.add(signature)
        assert item["starter"].strip() != item["expectedSql"].strip(), item["id"]

    sql_database = SCHEMA_KT.read_text(encoding="utf-8")
    schemas = re.findall(r'db\.execSQL\("(CREATE TABLE [^"]+)"\)', sql_database)
    assert len(schemas) == 5, f"Expected five embedded SQLite schemas, got {len(schemas)}"
    conn = sqlite3.connect(":memory:")
    conn.execute("PRAGMA foreign_keys=ON")
    for sql in schemas:
        conn.execute(sql)
    conn.executescript(SEED.read_text(encoding="utf-8"))

    expected_counts = {"instruments": 12, "accounts": 6, "trades": 230, "daily_prices": 429, "liquidity": 429}
    for name, expected in expected_counts.items():
        actual = conn.execute(f"SELECT COUNT(*) FROM {name}").fetchone()[0]
        assert actual == expected, f"Seed {name}: expected {expected}, got {actual}"
    assert not conn.execute("PRAGMA foreign_key_check").fetchall(), "Broken seed foreign keys"

    empty = []
    max_rows = 0
    max_id = None
    for task in tasks:
        id_ = task["id"]
        try:
            cursor = conn.execute(task["expectedSql"])
            rows = cursor.fetchmany(502)
        except sqlite3.Error as error:
            raise AssertionError(f"Invalid SQL in task #{id_} ({task['title']}): {error}") from error
        assert cursor.description, f"Task #{id_} has no result columns"
        assert len(rows) <= 500, f"Task #{id_} returns >500 rows; requires a narrower result"
        if len(rows) > max_rows:
            max_rows, max_id = len(rows), id_
        if not rows:
            empty.append((id_, task["topic"], task["title"]))

    by_topic = collections.Counter(item["topic"] for item in tasks)
    by_level = collections.Counter(item["difficulty"] for item in tasks)
    guides = json.loads((APP / "assets" / "lesson_guides.json").read_text(encoding="utf-8"))
    assert guides["version"] == 1
    assert [g["id"] for g in guides["guides"]] == list(range(32))
    assert all(len(g["steps"]) >= 2 for g in guides["guides"])

    print(f"PASS: {len(tasks)} unique reference SQL tasks; {len(by_topic)} topics; lesson prerequisites OK.")
    print(f"Levels: {dict(sorted(by_level.items()))}")
    print(f"Maximum reference rows: {max_rows} (task #{max_id})")
    print(f"Intentionally empty reference results: {len(empty)}")
    for id_, topic, title in empty:
        print(f"  {id_:3d} {topic}: {title}")
    print("Seed counts and foreign-key integrity: OK")
    print("32 lesson guides: OK")


if __name__ == "__main__":
    main()
