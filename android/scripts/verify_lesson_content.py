#!/usr/bin/env python3
"""Check lesson depth, desktop-source fidelity and explicit SQL-dialect notes."""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"


def main() -> None:
    content = json.loads((ASSETS / "lesson_readings.json").read_text(encoding="utf-8"))
    dialects = json.loads((ASSETS / "lesson_dialects.json").read_text(encoding="utf-8"))
    assert content["version"] == dialects["version"] == 1
    chapters = content["lessons"]
    differences = dialects["lessons"]
    assert len(chapters) == len(differences) == 32, "Every course lesson requires both resources"
    assert [row["id"] for row in chapters] == list(range(32))
    assert [row["id"] for row in differences] == list(range(32))
    assert "SQLite" in dialects["globalNotice"] and "SQL Server" in dialects["globalNotice"]
    origin_cache: dict[str, str] = {}
    total_sections = source_sections = total_code = 0
    for chapter, dialect in zip(chapters, differences):
        id_ = chapter["id"]
        parts = chapter["sections"]
        assert len(parts) >= 2, f"Lesson {id_} lacks detailed sections"
        characters = sum(len(p["markdown"]) for p in parts)
        assert characters >= 1200, f"Lesson {id_} too shallow: {characters}"
        assert len(chapter["origin"]) >= 1
        assert dialect["kind"] in ("same", "adapter", "different", "sqlite")
        assert len(dialect["summary"]) >= 70 and len(dialect["warning"]) >= 50
        if dialect["serverSql"]:
            assert dialect["androidSql"], f"Lesson {id_}: a T-SQL example needs an Android equivalent"
        for part in parts:
            assert part["title"].strip() and len(part["markdown"]) >= 60, f"Empty section in {id_}"
            total_sections += 1
            total_code += part["markdown"].count("```") // 2
            if part["source"].startswith("Урок "):
                filename = part["source"].split(" · ", 1)[-1]
                relative = "lessons/" + filename
                assert relative in chapter["origin"], (id_, relative)
                if relative not in origin_cache:
                    origin_cache[relative] = (ROOT / relative).read_text(encoding="utf-8")
                assert part["markdown"] in origin_cache[relative], (
                    f"Lesson {id_}: excerpt differs from exact desktop source {relative}"
                )
                source_sections += 1
            else:
                assert part["source"] == "Дополнение мобильного курса"
    assert total_sections >= 150 and source_sections >= 100
    print(
        f"PASS: 32 detailed lessons, {total_sections} sections, "
        f"{source_sections} verbatim desktop excerpts and {total_code} code examples."
    )
    print("All 32 lessons have explicit SQLite / SQL Server differences and checked sources.")


if __name__ == "__main__":
    main()
