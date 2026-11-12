#!/usr/bin/env python3
"""시안의 한국 전체 지도를 만든다. 바탕 그림(위성과 일반 지도)과, 그 위에 그릴 코스 선, 지역 이름 자리를 함께 낸다.
둘러보기와 내 코스는 이 지도를 코스나 지역으로 확대해 보이고, 랭킹은 작게 줄여 코스 위치를 짚는다.
출처는 장면과 같다. 위성: Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)
일반 지도: Terrain Light · Data © OpenStreetMap contributors and others · Rendering © EOX
사용: python3 tools/make-atlas.py"""
import importlib.util
import json
import math
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent.parent
OUT = ROOT / "design/screens/images/atlas"
COURSES = ROOT / "app/src/main/assets/courses"
ZOOM = 9
# 남, 북, 서, 동. 가로 창에서도 바다가 화면을 채우도록 동서와 제주 남쪽을 넉넉히 잡는다.
BOUNDS = (31.9, 38.9, 123.6, 131.0)
# 파일 크기를 줄인다. 좌표는 줄이기 전 크기 그대로 쓰고 화면이 이미지를 늘려 맞춘다.
SAVE_RATIO = 0.75
EVERY = 2
SCENE = (2404, 1210)
# 지역 이름은 코스 점을 가리지 않도록 코스가 없는 쪽(바다나 외곽)에 둔다.
REGIONS = {
    "수도권": (37.62, 126.55), "강원": (37.75, 128.35), "충청": (36.55, 127.25), "경상": (36.2, 128.75),
    "전라": (35.35, 126.95), "남해안": (34.42, 127.75), "제주": (33.05, 126.55),
}
CITIES = {
    "서울": (37.566, 126.978), "인천": (37.456, 126.705), "수원": (37.263, 127.029), "양평": (37.49, 127.49),
    "용인": (37.24, 127.18), "춘천": (37.881, 127.73), "강릉": (37.752, 128.876), "대전": (36.35, 127.385),
    "대구": (35.871, 128.601), "부산": (35.18, 129.075), "광주": (35.16, 126.853), "전주": (35.824, 127.148),
}
spec = importlib.util.spec_from_file_location("make_covers", Path(__file__).parent / "make-covers.py")
covers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(covers)


def pixel_at(point, zoom):
    return covers.pixel(point, zoom)


def origin():
    south, north, west, east = BOUNDS
    return pixel_at([north, west], ZOOM)


def to_atlas(point):
    x, y = pixel_at(point, ZOOM)
    left, top = origin()
    return x - left, y - top


def size():
    south, north, west, east = BOUNDS
    return to_atlas([south, east])


def number(value):
    return round(value, 1)


def box(path):
    """코스를 담는 상자. 반올림에 끝점이 빠지지 않도록 바깥쪽 정수로 잡는다."""
    xs, ys = zip(*(to_atlas(p) for p in path))
    x, y = math.floor(min(xs)), math.floor(min(ys))
    return {"x": x, "y": y, "w": math.ceil(max(xs)) - x, "h": math.ceil(max(ys)) - y}


def frame(view):
    """장면 한 장이 지도 위에서 차지하는 자리. 장면 그림을 이 자리에 얹으면 지도와 맞물린다."""
    ratio = 2 ** (ZOOM - view["zoom"])
    left, top = origin()
    return {
        "x": number(view["left"] * ratio - left), "y": number(view["top"] * ratio - top),
        "w": number(SCENE[0] / view["scale"] * ratio), "h": number(SCENE[1] / view["scale"] * ratio),
    }


def lines(course):
    path = course["path"]
    result = []
    for section in course["sections"]:
        points = path[section["startIndex"]:section["endIndex"] + 2]
        thin = points[::EVERY] + points[-1:]
        text = "M" + " L".join(f"{x:.1f} {y:.1f}" for x, y in (to_atlas(p) for p in thin))
        result.append({"mood": section.get("mood"), "d": text})
    return result


def labels():
    def made(kind, places):
        return [{"name": name, "kind": kind, "x": number(to_atlas(p)[0]), "y": number(to_atlas(p)[1])} for name, p in places.items()]
    return made("region", REGIONS) + made("city", CITIES)


def ground(layer="satellite"):
    left, top = origin()
    width, height = size()
    first_x, last_x = int(left // 256), int((left + width) // 256)
    first_y, last_y = int(top // 256), int((top + height) // 256)
    sheet = Image.new("RGB", ((last_x - first_x + 1) * 256, (last_y - first_y + 1) * 256))
    for x in range(first_x, last_x + 1):
        for y in range(first_y, last_y + 1):
            sheet.paste(covers.tile(ZOOM, x, y, layer), ((x - first_x) * 256, (y - first_y) * 256))
    crop = sheet.crop((int(left - first_x * 256), int(top - first_y * 256), int(left - first_x * 256 + width), int(top - first_y * 256 + height)))
    return covers.without_clouds(crop) if layer == "satellite" else crop


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    scenes = json.loads((ROOT / "design/screens/images/scene/routes.json").read_text(encoding="utf-8"))
    width, height = size()
    data = {"size": [number(width), number(height)], "labels": labels(), "courses": {}}
    for file in sorted(COURSES.glob("*.json")):
        course = json.loads(file.read_text(encoding="utf-8"))
        middle = to_atlas([sum(p[0] for p in course["path"]) / len(course["path"]), sum(p[1] for p in course["path"]) / len(course["path"])])
        data["courses"][course["id"]] = {
            "x": number(middle[0]), "y": number(middle[1]), "box": box(course["path"]),
            "frame": frame(scenes[course["id"]]["view"]), "lines": lines(course),
        }
    for layer in covers.LAYERS:
        day, night = OUT / covers.file_name("korea", layer), OUT / covers.file_name("korea", layer, night=True)
        if not day.exists():
            image = ground(layer).resize((int(width * SAVE_RATIO), int(height * SAVE_RATIO)), Image.LANCZOS)
            image.save(day, quality=72)
            covers.night_of(image, layer).save(night, quality=72)
    (OUT / "atlas.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
    print(OUT.relative_to(ROOT), data["size"], len(data["courses"]))


if __name__ == "__main__":
    main()
