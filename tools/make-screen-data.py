#!/usr/bin/env python3
"""앱 코스 파일에서 시안이 읽는 design/screens/courses.js 를 만든다. 만든 파일은 직접 고치지 않는다.
사용: python3 tools/make-screen-data.py"""
import importlib.util
import json
import math
from pathlib import Path

ROOT = Path(__file__).parent.parent
COURSES = ROOT / "app/src/main/assets/courses"
SCENERY = ROOT / "app/src/main/assets/scenery"
METRICS = ROOT / "design/screens/images/course_metrics.json"
OUT = ROOT / "design/screens/courses.js"
SCENES = ROOT / "design/screens/images/scene/routes.json"
NEARBY = ROOT / "tools/nearby.json"
KIND_WORDS = {"view": "경치", "photo": "경치", "sight": "명소", "food": "음식"}
spec = importlib.util.spec_from_file_location("make_scenes", Path(__file__).parent / "make-scenes.py")
scenes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scenes)
SEASONS = {"spring": "봄에", "summer": "여름에", "autumn": "가을에", "winter": "겨울에"}
AGES = {"20s": "20대", "30s": "30대", "40s": "40대", "50s": "50대"}
MOODS = {"alone": "혼자", "date": "데이트", "family": "가족"}


def metres(a, b):
    lat = math.radians((a[0] + b[0]) / 2)
    return math.hypot((a[0] - b[0]) * 111_320, (a[1] - b[1]) * 111_320 * math.cos(lat))


def places(path, spots, nearby, view):
    """주행 순서대로 번호를 매긴 코스 주변 정보. 쉼터는 뺀다."""
    along = [0.0]
    for a, b in zip(path, path[1:]):
        along.append(along[-1] + metres(a, b))
    items = []
    for item in [*spots, *nearby]:
        if item["kind"] not in KIND_WORDS:
            continue
        point = [item["lat"], item["lon"]]
        index = min(range(len(path)), key=lambda i: metres(path[i], point))
        x, y = scenes.project(point, view)
        items.append({
            "name": item["name"], "kind": item["kind"], "label": KIND_WORDS[item["kind"]], "note": item.get("note", ""),
            "km": round(along[index] / 1000, 1), "x": round(x, 1), "y": round(y, 1), "at": index,
        })
    items.sort(key=lambda item: item["at"])
    return [{**item, "no": number} for number, item in enumerate(items, 1)]


def entry(course, metrics, has_photo):
    tags = course["tags"]
    picks = [f"{SEASONS[tags['seasons'][0]]} 추천", f"{AGES[tags['ages'][0]]} 추천", MOODS[tags["moods"][0]]]
    cid = course["id"]
    return {
        "id": cid, "name": course["name"], "region": course["region"], "summary": course["summary"],
        "km": metrics["km"], "eum": metrics["eum"], "curves": metrics["curves"], "gain": metrics["gain"],
        "mix": metrics["mix"], "themes": tags["themes"], "times": tags["times"], "seasons": tags["seasons"], "picks": picks,
        "photo": f"images/scenery/{cid}.jpg" if has_photo else None,
        "credit": course.get("sceneryCredit", ""),
        "coverNight": f"images/covers/{cid}-night.jpg", "coverDay": f"images/covers/{cid}.jpg",
        "soft": f"images/covers/{cid}-soft.jpg",
    }


def script(entries):
    return "window.COURSES = " + json.dumps(entries, ensure_ascii=False, indent=1) + ";\n"


def main():
    metrics = json.loads(METRICS.read_text(encoding="utf-8"))
    routes = json.loads(SCENES.read_text(encoding="utf-8"))
    nearby = json.loads(NEARBY.read_text(encoding="utf-8")) if NEARBY.exists() else {}
    entries = []
    for file in sorted(COURSES.glob("*.json")):
        course = json.loads(file.read_text(encoding="utf-8"))
        cid = course["id"]
        scene = routes[cid]
        item = entry(course, metrics[cid], (SCENERY / f"{cid}.jpg").exists())
        item["scene"] = {"night": f"images/scene/{cid}-night.jpg", "day": f"images/scene/{cid}.jpg", "sections": scene["sections"]}
        item["places"] = places(course["path"], course["spots"], nearby.get(cid, []), scene["view"])
        entries.append(item)
    OUT.write_text(script(entries), encoding="utf-8")
    print(OUT.relative_to(ROOT), len(entries))


if __name__ == "__main__":
    main()
