#!/usr/bin/env python3
"""앱 코스 파일에서 시안이 읽는 design/screens/courses.js 를 만든다. 만든 파일은 직접 고치지 않는다.
사용: python3 tools/make-screen-data.py"""
import json
from pathlib import Path

ROOT = Path(__file__).parent.parent
COURSES = ROOT / "app/src/main/assets/courses"
SCENERY = ROOT / "app/src/main/assets/scenery"
METRICS = ROOT / "design/screens/images/course_metrics.json"
OUT = ROOT / "design/screens/courses.js"
SEASONS = {"spring": "봄에", "summer": "여름에", "autumn": "가을에", "winter": "겨울에"}
AGES = {"20s": "20대", "30s": "30대", "40s": "40대", "50s": "50대"}
MOODS = {"alone": "혼자", "date": "데이트", "family": "가족"}


def entry(course, metrics, has_photo):
    tags = course["tags"]
    picks = [f"{SEASONS[tags['seasons'][0]]} 추천", f"{AGES[tags['ages'][0]]} 추천", MOODS[tags["moods"][0]]]
    cid = course["id"]
    return {
        "id": cid, "name": course["name"], "region": course["region"], "summary": course["summary"],
        "km": metrics["km"], "eum": metrics["eum"], "curves": metrics["curves"], "gain": metrics["gain"],
        "mix": metrics["mix"], "themes": tags["themes"], "picks": picks,
        "photo": f"images/scenery/{cid}.jpg" if has_photo else None,
        "credit": course.get("sceneryCredit", ""),
        "coverNight": f"images/covers/{cid}-night.jpg", "coverDay": f"images/covers/{cid}.jpg",
        "soft": f"images/covers/{cid}-soft.jpg",
    }


def script(entries):
    return "window.COURSES = " + json.dumps(entries, ensure_ascii=False, indent=1) + ";\n"


def main():
    metrics = json.loads(METRICS.read_text(encoding="utf-8"))
    entries = []
    for file in sorted(COURSES.glob("*.json")):
        course = json.loads(file.read_text(encoding="utf-8"))
        entries.append(entry(course, metrics[course["id"]], (SCENERY / f"{course['id']}.jpg").exists()))
    OUT.write_text(script(entries), encoding="utf-8")
    print(OUT.relative_to(ROOT), len(entries))


if __name__ == "__main__":
    main()
