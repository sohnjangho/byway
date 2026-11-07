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


class ScriptTest(unittest.TestCase):
    def test_the_file_sets_one_global_list(self):
        self.assertTrue(data.script([{"id": "c"}]).startswith("window.COURSES = ["))


if __name__ == "__main__":
    unittest.main()
