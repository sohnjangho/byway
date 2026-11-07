#!/usr/bin/env python3
"""코스 정의로 앱 코스 파일을 만든다. 사용: tools/make-course.py [--keep-path] [코스 id ...] (없으면 전부)

정의 파일은 tools/courses/<id>.json, 결과는 app/src/main/assets/courses/<id>.json 이다.
좌표는 모두 [위도, 경도] 순서다. 구간은 경유지 사이마다 하나씩 둔다.
--keep-path 는 경로를 다시 받지 않고 앱 파일에 지역과 고도만 더한다.
충실도를 잰 코스(정의의 fidelity 가 있는 코스)는 측정값과 시험이 경로에 묶여 있어서 --keep-path 로만 고친다.
"""
import json
import math
import sys
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).parent.parent
ROUTER = "https://router.project-osrm.org/route/v1/driving/{}?overview=full&geometries=geojson&steps=true"
ELEVATION = "https://api.open-meteo.com/v1/elevation?latitude={}&longitude={}"
ELEVATION_POINTS = 60
USER_AGENT = "byway-course-tool"
REQUEST_GAP_SECONDS = 2
ATTRIBUTION = "© OpenStreetMap contributors (ODbL)"
MOODS = {"forest", "city", "river"}
REGIONS = {"수도권", "강원", "충청", "경상", "전라", "남해안", "제주"}
SPOT_KINDS = {"view", "food", "rest", "photo"}
SPOTS_PER_COURSE = range(2, 5)
SPOT_MAX_METERS = 500
# 되밟기 판정: 코스를 일정 간격으로 나눠, 코스상 멀리 떨어진 지점이 바로 옆에 있으면 같은 길로 본다.
RETRACE_STEP_METERS = 20
RETRACE_NEAR_METERS = 30
RETRACE_MIN_GAP_METERS = 200


def distance(a, b):
    """두 [위도, 경도] 사이의 거리(m)."""
    dlat = math.radians(b[0] - a[0])
    dlon = math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.sin(dlon / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


def length(path):
    return sum(distance(a, b) for a, b in zip(path, path[1:]))


def nearest_index(path, point, start=0):
    """start 이후에서 가장 가까운 좌표 번호. 같은 거리면 앞 번호."""
    return min(range(start, len(path)), key=lambda i: distance(path[i], point))


def to_path(coordinates):
    """경로 서버의 [경도, 위도] 열을 소수 6자리 [위도, 경도] 열로 바꾸고 연속 중복을 뺀다."""
    path = []
    for lon, lat in coordinates:
        point = [round(lat, 6), round(lon, 6)]
        if not path or path[-1] != point:
            path.append(point)
    return path


def section_ends(path, waypoints):
    """구간마다 끝 좌표 번호. 되밟는 길에서 뒤쪽을 고르지 않도록 앞 구간 다음부터 찾는다."""
    ends = []
    for waypoint in waypoints[1:-1]:
        ends.append(nearest_index(path, waypoint, ends[-1] + 1 if ends else 1))
    return ends + [len(path) - 1]


def build_sections(definition, path):
    if len(definition["sections"]) != len(definition["waypoints"]) - 1:
        raise ValueError(f"구간 수는 경유지 수보다 하나 적어야 합니다: {definition['id']}")
    sections = []
    start = 0
    for section, end in zip(definition["sections"], section_ends(path, definition["waypoints"])):
        if section["mood"] not in MOODS:
            raise ValueError(f"모르는 분위기: {section['mood']}")
        sections.append({"name": section["name"], "startIndex": start, "endIndex": end, "mood": section["mood"]})
        start = end + 1
    return sections


def build_spots(definition, path):
    if len(definition["spots"]) not in SPOTS_PER_COURSE:
        raise ValueError(f"스팟은 2~4개여야 합니다: {definition['id']}")
    spots = []
    for spot in definition["spots"]:
        if spot["kind"] not in SPOT_KINDS:
            raise ValueError(f"모르는 스팟 종류: {spot['kind']}")
        point = [spot["lat"], spot["lon"]]
        index = nearest_index(path, point)
        off = distance(path[index], point)
        if off > SPOT_MAX_METERS:
            raise ValueError(f"코스에서 {off:.0f}m 떨어진 스팟: {spot['name']}")
        spots.append({**spot, "pathIndex": index})
    return spots


def region_of(definition):
    if definition["region"] not in REGIONS:
        raise ValueError(f"모르는 지역: {definition['region']}")
    return definition["region"]


def build_course(definition, path):
    photo = {"sceneryCredit": definition["sceneryCredit"]} if "sceneryCredit" in definition else {}
    return {
        "schemaVersion": 2,
        "id": definition["id"],
        "name": definition["name"],
        "region": region_of(definition),
        "attribution": ATTRIBUTION,
        "summary": definition["summary"],
        "toleranceMeters": definition["toleranceMeters"],
        "tags": definition["tags"],
        "spots": build_spots(definition, path),
        "path": path,
        "sections": build_sections(definition, path),
        **photo,
    }


def resample(path, step):
    """코스를 step 간격 지점 열로 나눈다."""
    points = [path[0]]
    left = step
    for a, b in zip(path, path[1:]):
        gap = distance(a, b)
        done = 0.0
        while gap - done >= left:
            done += left
            ratio = done / gap
            points.append([a[0] + (b[0] - a[0]) * ratio, a[1] + (b[1] - a[1]) * ratio])
            left = step
        left -= gap - done
    return points


def even_points(path, count):
    """코스를 따라 거리가 고른 count개 지점. 양 끝을 포함한다."""
    return resample(path, length(path) / (count - 1))[:count - 1] + [path[-1]]


def retrace(path):
    """같은 길을 코스 먼 번호에서 다시 지나는 비율(0~1)과 가장 긴 연속 길이(m)."""
    points = resample(path, RETRACE_STEP_METERS)
    cell = RETRACE_NEAR_METERS / 111_000
    grid = {}
    for i, (lat, lon) in enumerate(points):
        grid.setdefault((int(lat / cell), int(lon / cell)), []).append(i)
    min_gap = RETRACE_MIN_GAP_METERS / RETRACE_STEP_METERS

    def repeated(i):
        y, x = int(points[i][0] / cell), int(points[i][1] / cell)
        return any(
            abs(i - j) > min_gap and distance(points[i], points[j]) < RETRACE_NEAR_METERS
            for dy in (-1, 0, 1) for dx in (-2, -1, 0, 1, 2) for j in grid.get((y + dy, x + dx), ())
        )

    flags = [repeated(i) for i in range(len(points))]
    longest = run = 0
    for flag in flags:
        run = run + 1 if flag else 0
        longest = max(longest, run)
    return sum(flags) / len(flags), float(longest * RETRACE_STEP_METERS)


def fetch_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def route_path(reply):
    """경로 서버 응답을 좌표열로 바꾼다. 차가 달리지 못하는 뱃길이 끼면 받지 않는다."""
    route = reply["routes"][0]
    ferries = [step["name"] for leg in route["legs"] for step in leg["steps"] if step["mode"] == "ferry"]
    if ferries:
        raise ValueError(f"뱃길이 낀 경로: {ferries}")
    return to_path(route["geometry"]["coordinates"])


def fetch_route(waypoints):
    joined = ";".join(f"{lon},{lat}" for lat, lon in waypoints)
    return route_path(fetch_json(ROUTER.format(joined)))


def elevation_url(points):
    return ELEVATION.format(",".join(f"{lat:.5f}" for lat, _ in points), ",".join(f"{lon:.5f}" for _, lon in points))


def to_elevation(reply, count):
    if len(reply["elevation"]) != count:
        raise ValueError(f"고도 {count}개를 요청했는데 {len(reply['elevation'])}개가 왔습니다")
    return [round(float(height), 1) for height in reply["elevation"]]


def fetch_elevation(path):
    points = even_points(path, ELEVATION_POINTS)
    return to_elevation(fetch_json(elevation_url(points)), len(points))


def with_region_and_elevation(course, region, elevation):
    return {**course, "region": region, "elevation": elevation}


def to_text(course):
    return json.dumps(course, ensure_ascii=False, separators=(",", ":"))


def report(course):
    path = course["path"]
    print(f"{course['id']}: {length(path) / 1000:.1f}km, 좌표 {len(path)}개")
    for section in course["sections"]:
        part = path[section["startIndex"]:section["endIndex"] + 1]
        print(f"  구간 {section['name']}({section['mood']}): {section['startIndex']}~{section['endIndex']}, {length(part) / 1000:.1f}km")
    for spot in course["spots"]:
        off = distance(path[spot["pathIndex"]], [spot["lat"], spot["lon"]])
        print(f"  스팟 {spot['name']}({spot['kind']}): 번호 {spot['pathIndex']}, 코스에서 {off:.0f}m")
    ratio, longest = retrace(path)
    print(f"  되밟기: {ratio * 100:.1f}%, 최장 연속 {longest:.0f}m")
    print(f"  지역: {course['region']}, 고도 {len(course['elevation'])}개, 최고 {max(course['elevation']):.0f}m")


def make(definition):
    if definition.get("fidelity"):
        raise ValueError(f"충실도를 잰 코스는 경로를 다시 받지 않습니다. --keep-path 를 쓰세요: {definition['id']}")
    course = build_course(definition, fetch_route(definition["waypoints"]))
    return {**course, "elevation": fetch_elevation(course["path"])}


def keep_path(definition, file):
    course = json.loads(file.read_text())
    return with_region_and_elevation(course, region_of(definition), fetch_elevation(course["path"]))


def main():
    arguments = sys.argv[1:]
    keep = "--keep-path" in arguments
    ids = [a for a in arguments if a != "--keep-path"] or sorted(p.stem for p in (ROOT / "tools/courses").glob("*.json"))
    for number, course_id in enumerate(ids):
        if number:
            time.sleep(REQUEST_GAP_SECONDS)
        definition = json.loads((ROOT / "tools/courses" / f"{course_id}.json").read_text())
        file = ROOT / "app/src/main/assets/courses" / f"{course_id}.json"
        course = keep_path(definition, file) if keep else make(definition)
        file.write_text(to_text(course))
        report(course)


if __name__ == "__main__":
    main()
