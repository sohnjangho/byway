#!/usr/bin/env python3
"""앱 코스 파일로 작동 목업을 만든다. 사용: tools/make-mockup.py <출력.html> [--bare]
--bare 는 문서 머리를 뺀 조각을 만든다."""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
DEFAULT_TOLERANCE = 1200.0
R = 6371000


def distance(a, b):
    la, lb = math.radians(a[0]), math.radians(b[0])
    h = math.sin((lb - la) / 2) ** 2 + math.cos(la) * math.cos(lb) * math.sin(math.radians(b[1] - a[1]) / 2) ** 2
    return 2 * R * math.asin(math.sqrt(h))


def to_segment(p, a, b):
    per_lon = math.radians(1) * R * math.cos(math.radians(p[0]))
    per_lat = math.radians(1) * R
    ax, ay = (a[1] - p[1]) * per_lon, (a[0] - p[0]) * per_lat
    bx, by = (b[1] - p[1]) * per_lon, (b[0] - p[0]) * per_lat
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    t = 0 if length == 0 else max(0, min(1, -(ax * dx + ay * dy) / length))
    return math.hypot(ax + t * dx, ay + t * dy)


def key_points(path, tolerance):
    kept, pending = {0, len(path) - 1}, [(0, len(path) - 1)]
    while pending:
        first, last = pending.pop()
        farthest, far = -1, tolerance
        for i in range(first + 1, last):
            d = to_segment(path[i], path[first], path[last])
            if d > far:
                farthest, far = i, d
        if farthest >= 0:
            kept.add(farthest)
            pending += [(first, farthest), (farthest, last)]
    return sorted(kept)


def course(file):
    d = json.loads(file.read_text(encoding="utf-8"))
    path = d["path"]
    sums = [0.0]
    for a, b in zip(path, path[1:]):
        sums.append(sums[-1] + distance(a, b))
    for s in d["sections"]:
        s["km"] = (sums[s["endIndex"]] - sums[s["startIndex"]]) / 1000
    return {
        "id": d["id"], "name": d["name"], "summary": d.get("summary", ""), "path": path, "km": sums[-1] / 1000,
        "sections": d["sections"], "tags": d.get("tags", {}), "spots": d.get("spots", []),
        "stops": key_points(path, d.get("toleranceMeters") or DEFAULT_TOLERANCE)[1:],
    }


def main():
    out = Path(sys.argv[1])
    courses = [course(f) for f in sorted((ROOT / "app/src/main/assets/courses").glob("*.json"))]
    courses.sort(key=lambda c: c["id"] != "paldang-sunset")
    print({c["id"]: len(c["stops"]) for c in courses})
    sim = (ROOT / "tools/mockup/sim.js").read_text(encoding="utf-8")
    page = (ROOT / "tools/mockup/template.html").read_text(encoding="utf-8")
    page = page.replace("/*SIM*/", sim).replace("/*DATA*/", json.dumps(courses, ensure_ascii=False, separators=(",", ":")))
    if "--bare" not in sys.argv:
        page = f'<!doctype html>\n<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">\n{page}\n</html>\n'
    out.write_text(page, encoding="utf-8")
    print(out, out.stat().st_size)


main()
