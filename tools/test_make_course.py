#!/usr/bin/env python3
"""make-course.py 시험. 사용: python3 tools/test_make_course.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_course", Path(__file__).parent / "make-course.py")
mc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mc)

# 동쪽으로 약 88m 간격인 직선 도로
LINE = [[37.5, 127.0 + i * 0.001] for i in range(11)]


def definition(**changes):
    base = {
        "id": "t", "name": "시험", "summary": "짧은 설명", "toleranceMeters": 800.0,
        "tags": {"themes": ["river"], "moods": ["alone"], "times": ["day"]},
        "waypoints": [[37.5, 127.0], [37.5002, 127.0041], [37.5, 127.01]],
        "sections": [{"name": "앞", "mood": "city"}, {"name": "뒤", "mood": "river"}],
        "spots": [{"name": "쉼터", "kind": "rest", "lat": 37.5005, "lon": 127.0072},
                  {"name": "전망", "kind": "view", "lat": 37.5, "lon": 127.0}],
    }
    return {**base, **changes}


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
