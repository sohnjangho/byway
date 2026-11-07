"""시안 자료를 코스 파일에서 만드는 규칙을 시험한다. 실행: python3 tools/test_make_screen_data.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_screen_data", Path(__file__).parent / "make-screen-data.py")
data = importlib.util.module_from_spec(spec)
spec.loader.exec_module(data)

COURSE = {
    "id": "c", "name": "시험 길", "region": "제주", "summary": "짧은 설명", "sceneryCredit": "사진: 가 (CC0)",
    "tags": {"themes": ["coast"], "moods": ["date", "alone"], "times": ["sunset"], "seasons": ["spring"], "ages": ["20s"]},
}
METRICS = {"km": 28.0, "eum": 1.3, "curves": 26, "gain": 135, "mix": {"river": 100}}


class EntryTest(unittest.TestCase):
    def test_picks_use_the_first_value_of_each_kind(self):
        self.assertEqual(data.entry(COURSE, METRICS, True)["picks"], ["봄에 추천", "20대 추천", "데이트"])

    def test_a_course_with_a_photo_points_at_it(self):
        self.assertEqual(data.entry(COURSE, METRICS, True)["photo"], "images/scenery/c.jpg")

    def test_a_course_without_a_photo_has_none(self):
        self.assertIsNone(data.entry(COURSE, METRICS, False)["photo"])

    def test_numbers_come_from_the_metrics(self):
        self.assertEqual(data.entry(COURSE, METRICS, True)["curves"], 26)


class PlacesTest(unittest.TestCase):
    view = {"zoom": 12, "scale": 1.0, "left": 0.0, "top": 0.0}
    path = [[37.0, 127.0 + i * 0.01] for i in range(11)]

    def places(self):
        spots = [{"name": "전망대", "kind": "view", "lat": 37.0, "lon": 127.05, "pathIndex": 5},
                 {"name": "쉼터", "kind": "rest", "lat": 37.0, "lon": 127.02, "pathIndex": 2}]
        nearby = [{"name": "국수집", "kind": "food", "lat": 37.0, "lon": 127.08, "note": "막국수"}]
        return data.places(self.path, spots, nearby, self.view)

    def test_rest_stops_are_left_out(self):
        self.assertEqual([p["name"] for p in self.places()], ["전망대", "국수집"])

    def test_places_are_numbered_in_driving_order(self):
        self.assertEqual([p["no"] for p in self.places()], [1, 2])

    def test_each_place_says_how_far_along_the_course_it_is(self):
        self.assertAlmostEqual(self.places()[0]["km"], 4.4, delta=0.1)

    def test_kinds_get_a_word(self):
        self.assertEqual([p["label"] for p in self.places()], ["경치", "음식"])


class ScriptTest(unittest.TestCase):
    def test_the_file_sets_one_global_list(self):
        self.assertTrue(data.script([{"id": "c"}]).startswith("window.COURSES = ["))


if __name__ == "__main__":
    unittest.main()
