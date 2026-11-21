#!/usr/bin/env python3
"""Guard the simplified mobile curriculum, icon and stable task IDs."""
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
CODE = ROOT / "app" / "src" / "main" / "java" / "ru" / "sqltraining" / "app"
RES = ROOT / "app" / "src" / "main" / "res"

def main():
    catalog = json.loads((ASSETS / "practice_tasks.json").read_text(encoding="utf-8"))
    tasks = catalog["tasks"]
    mobile = [t for t in tasks if 0 <= t["lessonId"] <= 20]
    by_lesson = Counter(t["lessonId"] for t in mobile)

    assert len(tasks) == 198, "The complete repository catalog must remain intact"
    assert len(mobile) == 168, "Unexpected number of mobile SQL exercises"
    assert len(by_lesson) == 21 and all(by_lesson[i] > 0 for i in range(21))
    assert [t["id"] for t in tasks] == list(range(1, 199)), "Do not renumber completed task IDs"

    curriculum = (CODE / "MobileCurriculum.kt").read_text(encoding="utf-8")
    assert "LAST_LESSON = 20" in curriculum
    assert "assigned.all { it.id in completedTaskIds }" in curriculum
    app = (CODE / "MainActivity.kt").read_text(encoding="utf-8")
    assert "MobileCurriculum.masteredLessons(practiceTasks, completedTasks)" in app
    assert "Отметить теорию изученной" not in app
    assert "Написать запрос самому" in app
    assert "Продолжить обучение" in (CODE / "SqlPlayground.kt").read_text(encoding="utf-8")
    assert "onReturnExampleLesson" in app and "onStartExampleTasks" in app

    icon = (RES / "drawable-nodpi" / "sql_slime.webp").read_bytes()
    assert len(icon) > 1000 and icon[:4] == b"RIFF" and icon[8:12] == b"WEBP"
    assert (RES / "mipmap-anydpi-v26" / "ic_launcher.xml").exists()
    manifest = (ROOT / "app" / "src" / "main" / "AndroidManifest.xml").read_text()
    assert 'android:icon="@mipmap/ic_launcher"' in manifest
    print(f"PASS: 21 sequential lessons, {len(mobile)} mobile tasks, original {len(tasks)} IDs preserved")
    print(f"PASS: automatic mastery, practice navigation and slime SQL launcher icon")

if __name__ == "__main__":
    main()
