#!/usr/bin/env python3
"""앱이 읽는 장면 자료를 시안과 같은 원본에서 만든다. 만든 파일은 직접 고치지 않는다.
사용: python3 tools/make-app-scenes.py"""
import importlib.util
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).parent.parent
ASSETS = ROOT / "app/src/main/assets"
IMAGES = ROOT / "design/screens/images"
SCENE_SIZE = [2404, 1210]
NUMBER = re.compile(r"-?\d+(?:\.\d+)?")
PLACE_KEYS = ("no", "name", "kind", "label", "note", "km", "x", "y")


def load(name, file):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).parent / file)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


effects = load("make_effects", "make-effects.py")


def points(d):
    numbers = [float(value) for value in NUMBER.findall(d)]
    return [[numbers[i], numbers[i + 1]] for i in range(0, len(numbers), 2)]


def scene_line(item):
    return {"mood": item["mood"], "name": item["name"], "points": points(item["d"])}


def atlas_of(atlas):
    lines = [{"mood": line["mood"], "points": points(line["d"])} for line in atlas["lines"]]
    return {"x": atlas["x"], "y": atlas["y"], "box": atlas["box"], "frame": atlas["frame"], "lines": lines}


def course_of(entry):
    return {
        "lines": [scene_line(section) for section in entry["scene"]["sections"]],
        "places": [{key: place[key] for key in PLACE_KEYS} for place in entry["places"]],
        "atlas": atlas_of(entry["atlas"]),
    }


def moods():
    table = {}
    for mood, (night, day, wash, (dx, dy, near, far)) in effects.LOOKS.items():
        draw, count = effects.SHAPES[mood]
        table[mood] = {
            "shape": draw.__name__, "count": count, "night": night, "day": day, "wash": wash,
            "dx": dx, "dy": dy, "near": near, "far": far,
        }
    return table


def build_index(entries, korea):
    return {
        "sceneSize": SCENE_SIZE,
        "koreaSize": korea["size"],
        "labels": korea["labels"],
        "moods": moods(),
        "courses": {entry["id"]: course_of(entry) for entry in entries},
    }


def copy_pictures(entries):
    scene = ASSETS / "scene"
    atlas = ASSETS / "atlas"
    scene.mkdir(parents=True, exist_ok=True)
    atlas.mkdir(parents=True, exist_ok=True)
    for entry in entries:
        for suffix in ("", "-night"):
            shutil.copyfile(IMAGES / f"scene/{entry['id']}{suffix}.jpg", scene / f"{entry['id']}{suffix}.jpg")
    for name in ("korea.jpg", "korea-night.jpg"):
        shutil.copyfile(IMAGES / f"atlas/{name}", atlas / name)


def main():
    data = load("make_screen_data", "make-screen-data.py")
    entries, korea = data.build_entries()
    copy_pictures(entries)
    (ASSETS / "scene").mkdir(parents=True, exist_ok=True)
    out = ASSETS / "scene/index.json"
    out.write_text(json.dumps(build_index(entries, korea), ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    print(out.relative_to(ROOT), out.stat().st_size, len(entries))


if __name__ == "__main__":
    main()
