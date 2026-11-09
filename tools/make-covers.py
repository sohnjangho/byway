#!/usr/bin/env python3
"""코스 커버 이미지를 만든다. 지도 타일 위에 구간 색 코스 선을 그린다. 사용: tools/make-covers.py [출력 폴더]
지도는 두 가지다. 위성과 일반 지도를 같은 좌표로 만들고 앱이 설정에 따라 고른다.
위성 출처: Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)
2016년판은 CC BY 4.0 이다. 2018년 이후판은 비상업 허가이고 2017년판에는 한국이 없어서 바꿀 수 없다.
일반 지도 출처: Terrain Light, Data © OpenStreetMap contributors and others, Rendering © EOX"""
import io
import json
import math
import sys
import tempfile
import time
import urllib.request
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageEnhance, ImageFilter, ImageOps, ImageStat

ROOT = Path(__file__).parent.parent
LAYERS = {
    "satellite": "https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless_3857/default/g/{z}/{y}/{x}.jpg",
    # 도로와 물, 지형만 있고 지명이 없다. 지명은 앱이 한글로 그린다.
    "map": "https://tiles.maps.eox.at/wmts/1.0.0/terrain-light_3857/default/g/{z}/{y}/{x}.jpg",
}
SIZE = 640
SCALE = 2
FILL = 0.8
CACHE = Path(tempfile.gettempdir()) / "byway-tiles"
FALLBACK = (240, 164, 90)
CASING = (13, 14, 16)
# 야간 커버는 사진만 어둡게 한다. 코스 선까지 어두워지면 코스가 안 보인다.
NIGHT_BRIGHTNESS = 0.62
NIGHT_CONTRAST = 1.12
# 일반 지도 야간판의 밝기. 바탕색에 더하는 값이다. 흰 땅(MAP_PAPER)이 MAP_LAND, 그보다 어두운 도로는 더 밝아진다.
MAP_PAPER = 246
MAP_LAND = 26
MAP_ROAD_SLOPE = 0.7
MAP_DARKEST = 14
MAP_BRIGHTEST = 84
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


def tile_url(layer, zoom, x, y):
    return LAYERS[layer].format(z=zoom, x=x, y=y)


def cache_file(layer, zoom, x, y):
    folder = CACHE if layer == "satellite" else CACHE.with_name(f"{CACHE.name}-{layer}")
    folder.mkdir(exist_ok=True)
    return folder / f"{zoom}_{x}_{y}.jpg"


def file_name(name, layer, night=False):
    """위성은 예전 이름 그대로 두고 일반 지도는 레이어 이름을 붙인다."""
    return f"{name}{'' if layer == 'satellite' else '-' + layer}{'-night' if night else ''}.jpg"


def tile(zoom, x, y, layer="satellite"):
    """받은 타일은 임시 폴더에 두고 다시 받지 않는다. 서버가 끊으면 기다렸다가 다시 시도한다."""
    cached = cache_file(layer, zoom, x, y)
    if not cached.exists():
        request = urllib.request.Request(tile_url(layer, zoom, x, y), headers={"User-Agent": "byway-course-tool"})
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


def section_colours():
    """구간 색의 원본은 design/tokens.json 이다. 사진 위에서 잘 보이는 야간 값을 쓴다."""
    tokens = json.loads((ROOT / "design/tokens.json").read_text(encoding="utf-8"))
    colours = {token["name"]: token["value"]["night"] for token in tokens["color"]["tokens"]}
    rgb = lambda value: tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))
    return {mood: rgb(colours[f"section-{mood}"]) for mood in ("forest", "city", "river")}


def darkened(image):
    return ImageEnhance.Contrast(ImageEnhance.Brightness(image).enhance(NIGHT_BRIGHTNESS)).enhance(NIGHT_CONTRAST)


def night_background():
    """앱 야간 바탕색. 원본은 design/tokens.json 이다."""
    tokens = json.loads((ROOT / "design/tokens.json").read_text(encoding="utf-8"))
    value = next(token["value"]["night"] for token in tokens["color"]["tokens"] if token["name"] == "bg")
    return tuple(int(value[i:i + 2], 16) for i in (1, 3, 5))


def night_map(image):
    """일반 지도의 야간판. 물은 앱 바탕색과 같게 해서 지도 가장자리가 보이지 않게 하고, 땅은 조금 밝게, 도로는 더 밝게 한다."""
    red, _, blue = image.split()
    water = ImageChops.subtract(blue, red).point(lambda v: max(0, min(255, (v - 5) * 255 // 10)))
    lift = ImageOps.grayscale(image).point(
        lambda v: max(MAP_DARKEST, min(MAP_BRIGHTEST, round(MAP_LAND + (MAP_PAPER - v) * MAP_ROAD_SLOPE)))
    )
    background = night_background()
    land = Image.merge("RGB", [lift.point(lambda v, base=base: min(255, v + base)) for base in background])
    return Image.composite(Image.new("RGB", image.size, background), land, water)


def night_of(image, layer):
    return darkened(image) if layer == "satellite" else night_map(image)


def ground(path, layer="satellite"):
    """코스 둘레의 지도와 그 좌표계. 위성은 구름을 뺀다."""
    zoom, left, top, window = frame(path)
    first_x, last_x = int(left // 256), int((left + window) // 256)
    first_y, last_y = int(top // 256), int((top + window) // 256)
    sheet = Image.new("RGB", ((last_x - first_x + 1) * 256, (last_y - first_y + 1) * 256))
    for x in range(first_x, last_x + 1):
        for y in range(first_y, last_y + 1):
            sheet.paste(tile(zoom, x, y, layer), ((x - first_x) * 256, (y - first_y) * 256))
    box = (int(left - first_x * 256), int(top - first_y * 256))
    drawn = SIZE * SCALE
    crop = sheet.crop((box[0], box[1], box[0] + int(window), box[1] + int(window))).resize((drawn, drawn), Image.LANCZOS)
    return (without_clouds(crop) if layer == "satellite" else crop), zoom, left, top, window


def cover(course, night=False, layer="satellite"):
    path = course["path"]
    image, zoom, left, top, window = ground(path, layer)
    drawn = SIZE * SCALE
    if night:
        image = night_of(image, layer)
    ratio = drawn / window
    colours = section_colours()
    pen = ImageDraw.Draw(image)

    def line(section):
        points = path[section["startIndex"]:section["endIndex"] + 2]
        return [((pixel(p, zoom)[0] - left) * ratio, (pixel(p, zoom)[1] - top) * ratio) for p in points]

    for section in course["sections"]:
        pen.line(line(section), fill=CASING, width=22, joint="curve")
    for section in course["sections"]:
        pen.line(line(section), fill=colours.get(section.get("mood"), FALLBACK), width=12, joint="curve")
    return image.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "app/src/main/assets/covers"
    out.mkdir(parents=True, exist_ok=True)
    for file in sorted((ROOT / "app/src/main/assets/courses").glob("*.json")):
        course = json.loads(file.read_text(encoding="utf-8"))
        for layer in LAYERS:
            for night in (False, True):
                target = out / file_name(course["id"], layer, night)
                if target.exists():
                    continue
                cover(course, night, layer).save(target, quality=82)
                print(target.name, target.stat().st_size, flush=True)


if __name__ == "__main__":
    main()
