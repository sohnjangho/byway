#!/usr/bin/env python3
"""make-course.py 시험. 사용: python3 tools/test_make_course.py"""
import importlib.util
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock
from urllib.parse import parse_qs, urlparse

spec = importlib.util.spec_from_file_location("make_course", Path(__file__).parent / "make-course.py")
mc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mc)

# 동쪽으로 약 88m 간격인 직선 도로
LINE = [[37.5, 127.0 + i * 0.001] for i in range(11)]


def definition(**changes):
    base = {
        "id": "t", "name": "시험", "region": "강원", "summary": "짧은 설명", "toleranceMeters": 800.0,
        "tags": {"themes": ["river"], "moods": ["alone"], "times": ["day"]},
        "waypoints": [[37.5, 127.0], [37.5002, 127.0041], [37.5, 127.01]],
        "sections": [{"name": "앞", "mood": "city"}, {"name": "뒤", "mood": "river"}],
        "spots": [{"name": "쉼터", "kind": "rest", "lat": 37.5005, "lon": 127.0072},
                  {"name": "전망", "kind": "view", "lat": 37.5, "lon": 127.0}],
    }
    return {**base, **changes}


class SceneryCreditTest(unittest.TestCase):
    def test_a_course_keeps_its_scenery_credit(self):
        course = mc.build_course(definition(sceneryCredit="사진: 가 (CC0)"), LINE)
        self.assertEqual("사진: 가 (CC0)", course["sceneryCredit"])

    def test_a_course_keeps_the_full_credit_of_its_photo(self):
        course = mc.build_course(definition(sceneryCredit="사진: 가 (CC0)", sceneryDetail="전문"), LINE)
        self.assertEqual("전문", course["sceneryDetail"])

    def test_a_course_without_a_photo_has_no_credit_field(self):
        self.assertNotIn("sceneryCredit", mc.build_course(definition(), LINE))


class SectionTest(unittest.TestCase):
    def test_sections_split_at_the_point_nearest_each_waypoint(self):
        sections = mc.build_course(definition(), LINE)["sections"]
        self.assertEqual([(0, 4), (5, 10)], [(s["startIndex"], s["endIndex"]) for s in sections])

    def test_a_retraced_road_uses_the_first_pass_after_the_previous_section(self):
        out_and_back = LINE + LINE[-2::-1]
        ends = mc.section_ends(out_and_back, [LINE[0], LINE[5], LINE[8], LINE[0]])
        self.assertEqual([5, 8, 20], ends)

    def test_a_section_count_that_does_not_match_the_waypoints_is_rejected(self):
        with self.assertRaises(ValueError):
            mc.build_course(definition(sections=[{"name": "하나", "mood": "city"}]), LINE)

    def test_an_unknown_mood_is_rejected(self):
        with self.assertRaises(ValueError):
            mc.build_course(definition(sections=[{"name": "앞", "mood": "lake"}, {"name": "뒤", "mood": "river"}]), LINE)


class SpotTest(unittest.TestCase):
    def test_spot_path_index_is_the_nearest_point(self):
        self.assertEqual([7, 0], [s["pathIndex"] for s in mc.build_course(definition(), LINE)["spots"]])

    def test_a_spot_more_than_500m_from_the_course_is_rejected(self):
        far = {"name": "먼 곳", "kind": "view", "lat": 37.51, "lon": 127.0}
        with self.assertRaises(ValueError):
            mc.build_course(definition(spots=[far, definition()["spots"][0]]), LINE)

    def test_an_unknown_kind_is_rejected(self):
        odd = {"name": "이상", "kind": "shop", "lat": 37.5, "lon": 127.0}
        with self.assertRaises(ValueError):
            mc.build_course(definition(spots=[odd, definition()["spots"][0]]), LINE)

    def test_spot_count_must_be_two_to_four(self):
        with self.assertRaises(ValueError):
            mc.build_course(definition(spots=definition()["spots"][:1]), LINE)


class CourseFileTest(unittest.TestCase):
    def test_file_carries_schema_version_and_attribution(self):
        course = mc.build_course(definition(), LINE)
        self.assertEqual(2, course["schemaVersion"])
        self.assertEqual("© OpenStreetMap contributors (ODbL)", course["attribution"])
        self.assertEqual(LINE, course["path"])
        self.assertEqual(800.0, course["toleranceMeters"])

    def test_route_points_become_lat_lon_without_repeats(self):
        self.assertEqual([[37.5, 127.0], [37.6, 127.1]],
                         mc.to_path([[127.0, 37.5], [127.0, 37.5], [127.1000001, 37.6]]))


class RegionTest(unittest.TestCase):
    def test_the_course_file_carries_the_region(self):
        self.assertEqual("강원", mc.build_course(definition(), LINE)["region"])

    def test_an_unknown_region_is_rejected(self):
        with self.assertRaises(ValueError):
            mc.build_course(definition(region="서해안"), LINE)


class EvenPointsTest(unittest.TestCase):
    def test_the_count_is_exact_and_both_ends_are_kept(self):
        points = mc.even_points(LINE, 7)
        self.assertEqual(7, len(points))
        self.assertEqual(LINE[0], points[0])
        self.assertEqual(LINE[-1], points[-1])

    def test_spacing_follows_distance_not_the_number_of_coordinates(self):
        # 앞쪽 100m에 좌표가 몰려 있어도 지점은 거리로 고르게 놓인다
        crowded = [[37.5, 127.0 + i * 0.00001] for i in range(100)] + [[37.5, 127.0 + i * 0.001] for i in range(1, 11)]
        points = mc.even_points(crowded, 11)
        gaps = [mc.distance(a, b) for a, b in zip(points, points[1:])]
        self.assertLess(max(gaps) - min(gaps), 1.0)


    def test_the_count_is_exact_on_a_crooked_road_too(self):
        # 간격이 들쭉날쭉한 길에서는 나눗셈 오차로 끝점 앞 지점이 하나 모자랄 수 있다
        crooked = [[37.5 + (i % 7) * 0.0013, 127.0 + i * i * 0.00003] for i in range(80)]
        for count in (7, 13, 60):
            points = mc.even_points(crooked, count)
            self.assertEqual(count, len(points))
            self.assertEqual(crooked[-1], points[-1])


class ElevationRequestTest(unittest.TestCase):
    def test_one_url_carries_every_point_in_lat_lon_order(self):
        url = mc.elevation_url([[37.5, 127.0], [37.25, 127.125]])
        self.assertEqual("https://api.open-meteo.com/v1/elevation?latitude=37.50000,37.25000&longitude=127.00000,127.12500", url)

    def test_heights_keep_one_decimal(self):
        self.assertEqual([12.3, 40.0], mc.to_elevation({"elevation": [12.34, 40]}, 2))

    def test_a_reply_with_the_wrong_count_is_rejected(self):
        with self.assertRaises(ValueError):
            mc.to_elevation({"elevation": [12.0]}, 2)


class KeepPathTest(unittest.TestCase):
    OLD = {"schemaVersion": 2, "id": "t", "toleranceMeters": 800.0, "path": LINE,
           "sections": [{"name": "앞", "startIndex": 0, "endIndex": 10, "mood": "city"}]}

    def test_only_region_and_elevation_are_added(self):
        added = mc.with_region_and_elevation(self.OLD, "수도권", [1.0, 2.0])
        self.assertEqual({"region": "수도권", "elevation": [1.0, 2.0]}, {k: v for k, v in added.items() if k not in self.OLD})
        self.assertEqual(self.OLD, {k: v for k, v in added.items() if k in self.OLD})

    def test_the_old_text_survives_byte_for_byte(self):
        old_text = mc.to_text(self.OLD)
        new_text = mc.to_text(mc.with_region_and_elevation(self.OLD, "수도권", [1.0]))
        self.assertTrue(new_text.startswith(old_text[:-1]))


class FerryTest(unittest.TestCase):
    @staticmethod
    def reply(*modes):
        steps = [{"name": f"길{i}", "mode": mode} for i, mode in enumerate(modes)]
        return {"routes": [{"legs": [{"steps": steps}], "geometry": {"coordinates": [[127.0, 37.5], [127.1, 37.6]]}}]}

    def test_a_road_route_becomes_a_path(self):
        self.assertEqual([[37.5, 127.0], [37.6, 127.1]], mc.route_path(self.reply("driving", "driving")))

    def test_a_route_over_a_ferry_is_rejected(self):
        with self.assertRaises(ValueError):
            mc.route_path(self.reply("driving", "ferry"))


class Servers:
    """경로 서버와 고도 서버 대역. 받은 요청을 남긴다. 실제 서버처럼 steps=true 일 때만 단계를 준다."""

    def __init__(self, mode="driving"):
        self.mode = mode
        self.requests = []

    def __call__(self, request, timeout):
        self.requests.append(request)
        url = request.full_url
        if "/elevation" in url:
            count = len(parse_qs(urlparse(url).query)["latitude"][0].split(","))
            reply = {"elevation": [10.0] * count}
        else:
            steps = [{"name": "길", "mode": self.mode}] if "steps=true" in url else []
            reply = {"routes": [{"legs": [{"steps": steps}], "geometry": {"coordinates": [[lon, lat] for lat, lon in LINE]}}]}
        return io.BytesIO(json.dumps(reply).encode())

    def urls(self, part):
        return [r.full_url for r in self.requests if part in r.full_url]


class ToolRunTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        (self.root / "tools/courses").mkdir(parents=True)
        (self.root / "app/src/main/assets/courses").mkdir(parents=True)
        self.servers = Servers()
        self.sleeps = []
        for patch in (mock.patch.object(mc, "ROOT", self.root),
                      mock.patch.object(mc.urllib.request, "urlopen", lambda *a, **k: self.servers(*a, **k)),
                      mock.patch.object(mc.time, "sleep", self.sleeps.append)):
            patch.start()
            self.addCleanup(patch.stop)

    def define(self, course_id, **changes):
        text = json.dumps(definition(id=course_id, fidelity=None, **changes), ensure_ascii=False)
        (self.root / f"tools/courses/{course_id}.json").write_text(text)

    def app_file(self, course_id):
        return self.root / f"app/src/main/assets/courses/{course_id}.json"

    def run_tool(self, *arguments):
        with mock.patch.object(sys, "argv", ["make-course.py", *arguments]), mock.patch("builtins.print"):
            mc.main()

    def test_a_new_course_gets_sixty_heights_from_one_request_each(self):
        self.define("t")
        self.run_tool("t")
        course = json.loads(self.app_file("t").read_text())
        self.assertEqual(60, len(course["elevation"]))
        self.assertEqual(LINE, course["path"])
        self.assertEqual(1, len(self.servers.urls("/route/")))
        self.assertEqual(1, len(self.servers.urls("/elevation")))

    def test_every_request_names_the_tool(self):
        self.define("t")
        self.run_tool("t")
        self.assertEqual({"byway-course-tool"}, {r.get_header("User-agent") for r in self.servers.requests})

    def test_a_route_over_a_ferry_writes_nothing(self):
        self.define("t")
        self.servers.mode = "ferry"
        with self.assertRaises(ValueError):
            self.run_tool("t")
        self.assertFalse(self.app_file("t").exists())

    def test_courses_are_spaced_more_than_a_second_apart(self):
        self.define("a")
        self.define("b")
        self.run_tool("a", "b")
        self.assertEqual(1, len(self.sleeps))
        self.assertGreaterEqual(self.sleeps[0], 1)

    def test_keep_path_asks_for_heights_only_and_keeps_the_old_text(self):
        self.define("t", region="수도권")
        old = '{"schemaVersion":2,"id":"t","toleranceMeters":800.0,"path":[[37.5,127.0],[37.6,127.1]]}'
        self.app_file("t").write_text(old)
        with mock.patch.object(mc, "report"):
            self.run_tool("--keep-path", "t")
        new = self.app_file("t").read_text()
        self.assertEqual([], self.servers.urls("/route/"))
        self.assertTrue(new.startswith(old[:-1]))
        self.assertEqual("수도권", json.loads(new)["region"])
        self.assertEqual(60, len(json.loads(new)["elevation"]))

    def test_keep_path_rejects_an_unknown_region(self):
        self.define("t", region="서해안")
        self.app_file("t").write_text('{"path":[[37.5,127.0],[37.6,127.1]]}')
        with self.assertRaises(ValueError):
            self.run_tool("--keep-path", "t")

    def test_a_measured_course_is_not_routed_again(self):
        text = json.dumps(definition(id="t", fidelity={"rows": []}), ensure_ascii=False)
        (self.root / "tools/courses/t.json").write_text(text)
        self.app_file("t").write_text("옛 코스")
        with self.assertRaises(ValueError):
            self.run_tool("t")
        self.assertEqual("옛 코스", self.app_file("t").read_text())
        self.assertEqual([], self.servers.requests)


class RetraceTest(unittest.TestCase):
    # 동쪽으로 약 3.5km 뻗은 직선
    LONG = [[37.5, 127.0 + i * 0.0004] for i in range(101)]

    def test_a_road_driven_once_has_no_retrace(self):
        self.assertEqual((0.0, 0.0), mc.retrace(self.LONG))

    def test_a_dead_end_round_trip_counts_nearly_everything(self):
        ratio, longest = mc.retrace(self.LONG + self.LONG[-2::-1])
        self.assertGreater(ratio, 0.9)
        self.assertGreater(longest, 3000)

    def test_a_road_crossing_itself_counts_only_a_short_stretch(self):
        cross = [[37.5, 127.0 + i * 0.0004] for i in range(51)]
        loop = [[37.5 + i * 0.0004, 127.02] for i in range(21)] + [[37.508, 127.02 - i * 0.0004] for i in range(1, 11)] \
            + [[37.508 - i * 0.0004, 127.016] for i in range(1, 41)]
        ratio, longest = mc.retrace(cross + loop)
        self.assertLess(ratio, 0.05)
        self.assertLess(longest, 200)


if __name__ == "__main__":
    unittest.main()
