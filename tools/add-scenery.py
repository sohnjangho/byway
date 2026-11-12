#!/usr/bin/env python3
"""tools/scenery.json 의 풍경 사진을 내려받아 앱 자산으로 넣고 코스 파일에 출처를 적는다.
이용 조건을 확인한 사진만 목록에 넣는다. 출처가 없는 사진은 앱이 보이지 않는다.
그다음 python3 tools/make-covers.py 를 돌리면 흐린 배경이 사진으로 바뀐다.
사용: python3 tools/add-scenery.py [코스 id ...]"""
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

ROOT = Path(__file__).parent.parent
PLAN = ROOT / "tools/scenery.json"
OUT = ROOT / "app/src/main/assets/scenery"
COURSES = ROOT / "app/src/main/assets/courses"
DEFINITIONS = ROOT / "tools/courses"
SIZE = (1280, 960)
LICENCE_LINKS = {
    "CC0": "https://creativecommons.org/publicdomain/zero/1.0/",
    "CC BY 2.0": "https://creativecommons.org/licenses/by/2.0/",
    "CC BY-SA 3.0": "https://creativecommons.org/licenses/by-sa/3.0/",
    "CC BY-SA 4.0": "https://creativecommons.org/licenses/by-sa/4.0/",
    "Public domain": "",
}


def credit_of(entry):
    return f"사진: {entry['artist']} ({entry['license']}, Wikimedia Commons)"


def detail_of(entry):
    """앱 정보에 적는 전문. 저작자, 제목, 라이선스와 그 주소, 원본 주소, 변형 사실을 모두 담는다."""
    licence = f"{entry['license']} {LICENCE_LINKS[entry['license']]}".rstrip()
    share = " 변형본도 같은 라이선스를 따릅니다." if "-SA" in entry["license"] else ""
    return (
        f"사진: {entry['artist']}, 「{Path(entry['file']).stem}」, {licence}, 원본 {urllib.parse.unquote(entry['page'])}. "
        f"화면에 맞춰 자르고 주행 화면 배경은 흐리게 변형했습니다.{share}"
    )


def source_url(entry):
    """원본 대신 앱 크기의 썸네일을 받는다. 위키미디어가 원본 요청을 제한한다."""
    return f"https://commons.wikimedia.org/wiki/Special:FilePath/{urllib.parse.quote(entry['file'])}?width=1600"


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "byway-course-tool/0.1"})
    for _ in range(4):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code != 429:
                raise
            time.sleep(int(error.headers.get("Retry-After") or 60) + 5)
    raise SystemExit(f"요청 제한이 풀리지 않습니다: {url}")


def fitted(image):
    return ImageOps.fit(image.convert("RGB"), SIZE, Image.LANCZOS)


def with_field(text, name, value):
    text = re.sub(rf'"{name}":"[^"]*",', "", text, count=1)
    return text.replace('"summary":', f'"{name}":{json.dumps(value, ensure_ascii=False)},"summary":', 1)


def with_credit(text, credit):
    return with_field(text, "sceneryCredit", credit)


def with_detail(text, detail):
    return with_field(text, "sceneryDetail", detail)


def write_credits(course_id, entry):
    file = COURSES / f"{course_id}.json"
    file.write_text(with_detail(with_credit(file.read_text(encoding="utf-8"), credit_of(entry)), detail_of(entry)), encoding="utf-8")
    source = DEFINITIONS / f"{course_id}.json"
    definition = json.loads(source.read_text(encoding="utf-8"))
    definition["sceneryCredit"] = credit_of(entry)
    definition["sceneryDetail"] = detail_of(entry)
    source.write_text(json.dumps(definition, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main():
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    for course_id in sys.argv[1:] or plan:
        entry = plan[course_id]
        fitted(Image.open(io.BytesIO(fetch(source_url(entry))))).save(OUT / f"{course_id}.jpg", quality=80)
        write_credits(course_id, entry)
        print(course_id, (OUT / f"{course_id}.jpg").stat().st_size, flush=True)
        time.sleep(12)


if __name__ == "__main__":
    main()
