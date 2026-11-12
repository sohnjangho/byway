#!/usr/bin/env python3
"""코스 훑어보기에 쓰는 사진 띠와 지점 자료를 만든다. 사진은 코스를 따라 고른 지점 둘레의 위성 조각이다.
차량 카메라 사진이 생기면 같은 이름의 파일만 바꾸면 된다. 만든 파일은 직접 고치지 않는다.
사용: python3 tools/make-frames.py"""
import importlib.util
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent.parent
COURSES = ROOT / "app/src/main/assets/courses"
OUT = ROOT / "design/screens/images/frames"
DATA = OUT / "frames.json"
COUNT = 16
ZOOM = 14
SIZE = (320, 200)
TRACK_POINTS = 200
# 사진 지점에서 이 거리 안에 코스 주변 정보가 있으면 그 번호를 함께 적는다.
NEAR_KM = 1.2
EARTH_RADIUS_METERS = 6_371_000.0
spec = importlib.util.spec_from_file_location("make_screen_data", Path(__file__).parent / "make-screen-data.py")
data = importlib.util.module_from_spec(spec)
spec.loader.exec_module(data)
scenes = data.scenes
covers = scenes.covers


def metres(a, b):
    """두 좌표 사이 거리(m). 앱(core 의 Geo.kt)과 같은 식을 써야 홈의 거리와 띠 끝의 km가 같다."""
    lat1, lon1, lat2, lon2 = (math.radians(value) for value in (a[0], a[1], b[0], b[1]))
    h = math.sin((lat2 - lat1) / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2
    return 2 * EARTH_RADIUS_METERS * math.asin(math.sqrt(h))


def distances(path):
    """출발점에서 각 지점까지 달린 거리(m)."""
    along = [0.0]
    for a, b in zip(path, path[1:]):
        along.append(along[-1] + metres(a, b))
    return along


def picks(along, count):
    """출발부터 도착까지 고르게 나눈 지점. 짧은 코스에서 같은 지점이 두 번 나오지 않게 한다."""
    chosen = []
    for k in range(count):
        target = along[-1] * k / (count - 1)
        index = min(range(len(along)), key=lambda i: abs(along[i] - target))
        if index not in chosen:
            chosen.append(index)
    return chosen


def track(path, along, project, points):
    """장면 그림 위의 코스 자리와 그때까지 달린 거리(km). 앱이 그 사이를 이어 점을 옮긴다."""
    step = max(1, len(path) // points)
    indexes = list(range(0, len(path), step))
    if indexes[-1] != len(path) - 1:
        indexes.append(len(path) - 1)
    result = []
    for index in indexes:
        x, y = project(path[index])
        result.append([round(x, 1), round(y, 1), round(along[index] / 1000, 2)])
    return result


def info(course, places, index, km, total_km):
    sections = course["sections"]
    section = next((s for s in sections if s["startIndex"] <= index <= s["endIndex"]), sections[-1])
    near = min(places, key=lambda place: abs(place["km"] - km), default=None)
    heights = course.get("elevation") or []
    # 고도는 코스를 따라 거리가 고른 표본이다(make-course.py). 좌표 번호가 아니라 달린 거리로 찾는다.
    sample = round(km / total_km * (len(heights) - 1)) if total_km > 0 else 0
    height = round(heights[min(len(heights) - 1, sample)]) if heights else None
    return {
        "km": round(km, 1), "section": section["name"], "mood": section.get("mood"), "elev": height,
        "placeNo": near["no"] if near and abs(near["km"] - km) <= NEAR_KM else None,
    }


def picture(point):
    x, y = covers.pixel(point, ZOOM)
    tile_x, tile_y = int(x // 256), int(y // 256)
    sheet = Image.new("RGB", (768, 768))
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            sheet.paste(covers.tile(ZOOM, tile_x + dx, tile_y + dy), ((dx + 1) * 256, (dy + 1) * 256))
    cx, cy = x - (tile_x - 1) * 256, y - (tile_y - 1) * 256
    return sheet.crop((int(cx - SIZE[0] / 2), int(cy - SIZE[1] / 2), int(cx + SIZE[0] / 2), int(cy + SIZE[1] / 2)))


def main():
    entries, _ = data.build_entries()
    result = {}
    for entry in entries:
        course = json.loads((COURSES / f"{entry['id']}.json").read_text(encoding="utf-8"))
        path = course["path"]
        along = distances(path)
        view = scenes.fit(path)
        chosen = picks(along, COUNT)
        folder = OUT / entry["id"]
        folder.mkdir(parents=True, exist_ok=True)
        for number, index in enumerate(chosen):
            target = folder / f"{number:02d}.jpg"
            if not target.exists():
                picture(path[index]).save(target, quality=78)
        result[entry["id"]] = {
            "total": round(along[-1] / 1000, 1),
            "frames": [info(course, entry["places"], index, along[index] / 1000, along[-1] / 1000) for index in chosen],
            "track": track(path, along, lambda point: scenes.project(point, view), TRACK_POINTS),
        }
        print(entry["id"], len(chosen), flush=True)
    DATA.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


if __name__ == "__main__":
    main()
