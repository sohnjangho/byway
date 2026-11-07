#!/usr/bin/env python3
"""코스 커버 이미지를 만든다. 위성 타일 위에 구간 색 코스 선을 그린다. 사용: tools/make-covers.py [출력 폴더]
위성 출처: Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)
2016년판은 CC BY 4.0 이다. 2018년 이후판은 비상업 허가이고 2017년판에는 한국이 없어서 바꿀 수 없다."""
import io
import json
import math
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageStat

ROOT = Path(__file__).parent.parent
TILE = "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/{z}/{y}/{x}.jpg"
SIZE = 640
SCALE = 2
FILL = 0.8
CACHE = Path(tempfile.gettempdir()) / "byway-tiles"
CACHE.mkdir(exist_ok=True)
COLORS = {"forest": (124, 196, 143), "city": (214, 222, 238), "river": (95, 198, 216)}
FALLBACK = (240, 168, 104)
# 세 색 중 가장 어두운 값이 이보다 밝으면 구름으로 본다. 흰 지붕이나 옅은 모래, 콘크리트도 함께 덮인다.
CLOUD_LEVEL = 150


def pixel(point, zoom):
    size = 2 ** zoom * 256
    s = math.sin(math.radians(point[0]))
    return (point[1] + 180) / 360 * size, (0.5 - math.log((1 + s) / (1 - s)) / (4 * math.pi)) * size


def zoom_for(path):
    for zoom in range(14, 6, -1):
        xs, ys = zip(*(pixel(p, zoom) for p in path))
        if max(max(xs) - min(xs), max(ys) - min(ys)) <= SIZE:
            return zoom
    return 7


def tile(zoom, x, y):
    """받은 타일은 임시 폴더에 두고 다시 받지 않는다. 서버가 끊으면 기다렸다가 다시 시도한다."""
    cached = CACHE / f"{zoom}_{x}_{y}.jpg"
    if not cached.exists():
        request = urllib.request.Request(TILE.format(z=zoom, x=x, y=y), headers={"User-Agent": "byway-course-tool"})
        for attempt in range(5):
            try:
                cached.write_bytes(urllib.request.urlopen(request, timeout=30).read())
                break
            except OSError:
                if attempt == 4:
                    raise
                time.sleep(3 * (attempt + 1))
        time.sleep(0.25)
    return Image.open(io.BytesIO(cached.read_bytes())).convert("RGB")


def without_clouds(image):
    """2016년판에는 구름이 남은 지역이 있다. 구름 자리를 주변 땅색으로 덮어 코스 선이 묻히지 않게 한다."""
    red, green, blue = image.split()
    darkest = ImageChops.darker(ImageChops.darker(red, green), blue)
    cloud = darkest.point(lambda v: 255 if v > CLOUD_LEVEL else 0).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(6))
    clear = ImageChops.invert(cloud).point(lambda v: 255 if v > 250 else 0)
    if not clear.getbbox():
        return image
    land = tuple(int(v) for v in ImageStat.Stat(image, clear).mean)
    filled = Image.composite(Image.new("RGB", image.size, land), image, cloud)
    for radius in (48, 24, 12):
        filled = Image.composite(filled.filter(ImageFilter.GaussianBlur(radius)), image, cloud)
    return filled


def frame(path):
    """코스가 프레임의 FILL 비율을 채우는 정사각 창. 줌과 왼쪽 위 픽셀, 한 변 길이를 준다."""
    zoom = zoom_for(path)
    xs, ys = zip(*(pixel(p, zoom) for p in path))
    window = max(max(xs) - min(xs), max(ys) - min(ys)) / FILL
    return zoom, (min(xs) + max(xs)) / 2 - window / 2, (min(ys) + max(ys)) / 2 - window / 2, window


def cover(course):
    path = course["path"]
    zoom, left, top, window = frame(path)
    first_x, last_x = int(left // 256), int((left + window) // 256)
    first_y, last_y = int(top // 256), int((top + window) // 256)
    sheet = Image.new("RGB", ((last_x - first_x + 1) * 256, (last_y - first_y + 1) * 256))
    for x in range(first_x, last_x + 1):
        for y in range(first_y, last_y + 1):
            sheet.paste(tile(zoom, x, y), ((x - first_x) * 256, (y - first_y) * 256))
    box = (int(left - first_x * 256), int(top - first_y * 256))
    drawn = SIZE * SCALE
    image = without_clouds(sheet.crop((box[0], box[1], box[0] + int(window), box[1] + int(window))).resize((drawn, drawn), Image.LANCZOS))
    ratio = drawn / window
    pen = ImageDraw.Draw(image)

    def line(section):
        points = path[section["startIndex"]:section["endIndex"] + 2]
        return [((pixel(p, zoom)[0] - left) * ratio, (pixel(p, zoom)[1] - top) * ratio) for p in points]

    for section in course["sections"]:
        pen.line(line(section), fill=(19, 20, 23), width=22, joint="curve")
    for section in course["sections"]:
        pen.line(line(section), fill=COLORS.get(section.get("mood"), FALLBACK), width=12, joint="curve")
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/src/main/assets/covers"
    out.mkdir(parents=True, exist_ok=True)
    for file in sorted((ROOT / "app/src/main/assets/courses").glob("*.json")):
        course = json.loads(file.read_text(encoding="utf-8"))
        target = out / f"{course['id']}.jpg"
        if target.exists():
            continue
        cover(course).save(target, quality=82)
        print(target.name, target.stat().st_size, flush=True)


if __name__ == "__main__":
    main()
