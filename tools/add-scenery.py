#!/usr/bin/env python3
"""tools/scenery.json 의 풍경 사진을 내려받아 앱 자산으로 넣고 코스 파일에 출처를 적는다.
이용 조건을 확인한 사진만 목록에 넣는다. 출처가 없는 사진은 앱이 보이지 않는다.
그다음 python3 tools/make-covers.py 를 돌리면 흐린 배경이 사진으로 바뀐다.
사용: python3 tools/add-scenery.py [코스 id ...]"""
import io
import json
import re
import sys
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).parent.parent
PLAN = ROOT / "tools/scenery.json"
OUT = ROOT / "app/src/main/assets/scenery"
COURSES = ROOT / "app/src/main/assets/courses"
DEFINITIONS = ROOT / "tools/courses"
SIZE = (1280, 960)


def credit_of(entry):
    return f"사진: {entry['artist']} ({entry['license']}, Wikimedia Commons)"


def fitted(image):
    return ImageOps.fit(image.convert("RGB"), SIZE, Image.LANCZOS)


def with_credit(text, credit):
    text = re.sub(r'"sceneryCredit":"[^"]*",', "", text, count=1)
    return text.replace('"summary":', f'"sceneryCredit":{json.dumps(credit, ensure_ascii=False)},"summary":', 1)


def main():
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    for course_id in sys.argv[1:] or plan:
        entry = plan[course_id]
        request = urllib.request.Request(entry["url"], headers={"User-Agent": "byway-course-tool/0.1"})
        with urllib.request.urlopen(request, timeout=60) as response:
            fitted(Image.open(io.BytesIO(response.read()))).save(OUT / f"{course_id}.jpg", quality=80)
        file = COURSES / f"{course_id}.json"
        file.write_text(with_credit(file.read_text(encoding="utf-8"), credit_of(entry)), encoding="utf-8")
        source = DEFINITIONS / f"{course_id}.json"
        definition = json.loads(source.read_text(encoding="utf-8"))
        definition["sceneryCredit"] = credit_of(entry)
        source.write_text(json.dumps(definition, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(course_id, (OUT / f"{course_id}.jpg").stat().st_size, flush=True)


if __name__ == "__main__":
    main()
