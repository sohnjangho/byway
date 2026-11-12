#!/usr/bin/env python3
"""시안 메인 화면의 오른쪽 장면을 코스마다 만든다. 위성 바탕과 그 위에 그릴 코스 선을 함께 낸다.
코스는 왼쪽 글과 아래 코스 줄을 피해 ROUTE_BOX 안에 맞춘다. 앱은 실제 지도로 같은 일을 한다.
사용: python3 tools/make-scenes.py"""
import importlib.util
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).parent.parent
OUT = ROOT / "design/screens/images/scene"
COURSES = ROOT / "app/src/main/assets/courses"
# 전체 창의 장면 칸 크기다. 2/3 창과 1/3 창은 같은 장면을 오른쪽에 맞춰 잘라 쓴다.
SIZE = (2404, 1210)
# 경로는 어느 창에서도 글과 겹치지 않는 오른쪽 영역에만 둔다.
ROUTE_BOX = (1540, 110, 2344, 1000)
# 바탕은 흐릿해도 되므로 파일을 줄인다. 코스 선은 벡터로 또렷하게 그린다.
SAVE_RATIO = 0.6
EVERY = 4
spec = importlib.util.spec_from_file_location("make_covers", Path(__file__).parent / "make-covers.py")
covers = importlib.util.module_from_spec(spec)
spec.loader.exec_module(covers)


def fit(path):
    """코스가 ROUTE_BOX 를 채우는 줌과 배율, 왼쪽 위 픽셀을 준다."""
    left, top, right, bottom = ROUTE_BOX
    for zoom in range(14, 6, -1):
        xs, ys = zip(*(covers.pixel(p, zoom) for p in path))
        width, height = max(xs) - min(xs), max(ys) - min(ys)
        if width <= right - left and height <= bottom - top:
            break
    scale = min((right - left) / max(width, 1), (bottom - top) / max(height, 1))
    middle_x, middle_y = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    return {
        "zoom": zoom, "scale": scale,
        "left": middle_x - (left + right) / 2 / scale,
        "top": middle_y - (top + bottom) / 2 / scale,
    }


def project(point, view):
    x, y = covers.pixel(point, view["zoom"])
    return (x - view["left"]) * view["scale"], (y - view["top"]) * view["scale"]


def number(value):
    return f"{value:.1f}".removesuffix(".0")


def line_text(points):
    return "M" + " L".join(f"{number(x)} {number(y)}" for x, y in points)


def ground(view):
    width, height = SIZE[0] / view["scale"], SIZE[1] / view["scale"]
    first_x, last_x = int(view["left"] // 256), int((view["left"] + width) // 256)
    first_y, last_y = int(view["top"] // 256), int((view["top"] + height) // 256)
    sheet = Image.new("RGB", ((last_x - first_x + 1) * 256, (last_y - first_y + 1) * 256))
    for x in range(first_x, last_x + 1):
        for y in range(first_y, last_y + 1):
            sheet.paste(covers.tile(view["zoom"], x, y), ((x - first_x) * 256, (y - first_y) * 256))
    box = (view["left"] - first_x * 256, view["top"] - first_y * 256)
    crop = sheet.crop((int(box[0]), int(box[1]), int(box[0] + width), int(box[1] + height)))
    return covers.without_clouds(crop.resize(SIZE, Image.LANCZOS))


def sections(course, view):
    path = course["path"]
    result = []
    for section in course["sections"]:
        points = path[section["startIndex"]:section["endIndex"] + 2]
        thin = points[::EVERY] + points[-1:]
        result.append({"mood": section.get("mood"), "name": section["name"], "d": line_text([project(p, view) for p in thin])})
    return result


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    data = {}
    for file in sorted(COURSES.glob("*.json")):
        course = json.loads(file.read_text(encoding="utf-8"))
        view = fit(course["path"])
        data[course["id"]] = {"view": view, "sections": sections(course, view)}
        day, night = OUT / f"{course['id']}.jpg", OUT / f"{course['id']}-night.jpg"
        if not day.exists():
            image = ground(view).resize((int(SIZE[0] * SAVE_RATIO), int(SIZE[1] * SAVE_RATIO)), Image.LANCZOS)
            image.save(day, quality=74)
            covers.darkened(image).save(night, quality=74)
        print(course["id"], view["zoom"], round(view["scale"], 2), flush=True)
    (OUT / "routes.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    main()
