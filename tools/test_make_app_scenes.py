"""앱용 장면 자료를 만드는 규칙을 시험한다. 실행: python3 tools/test_make_app_scenes.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_app_scenes", Path(__file__).parent / "make-app-scenes.py")
scenes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scenes)

ATLAS = {
    "x": 1.0, "y": 2.0, "box": {"x": 1, "y": 2, "w": 3, "h": 4}, "frame": {"x": 5, "y": 6, "w": 7, "h": 8},
    "lines": [{"mood": "river", "d": "M1 2 L3 4"}],
}
PLACE = {
    "name": "전망대", "kind": "view", "label": "경치", "note": "", "km": 1.5, "x": 10.0, "y": 20.0, "at": 3, "no": 1,
    "evidence": "근거 문장", "sourceUrl": "https://example.invalid",
}
ENTRIES = [{
    "id": "c1", "scene": {"sections": [{"mood": "city", "name": "구간", "d": "M10 20 L30.5 40"}]},
    "places": [PLACE], "atlas": ATLAS,
}]
KOREA = {"size": [2020, 2347], "labels": [{"name": "수도권", "kind": "region", "x": 1.0, "y": 2.0}]}


class PointsTest(unittest.TestCase):
    def test_path_becomes_points(self):
        self.assertEqual(scenes.points("M1 2 L3.5 4 L5 6"), [[1.0, 2.0], [3.5, 4.0], [5.0, 6.0]])

    def test_negative_coordinates_survive(self):
        self.assertEqual(scenes.points("M-1.5 2 L3 -4"), [[-1.5, 2.0], [3.0, -4.0]])


class IndexTest(unittest.TestCase):
    def setUp(self):
        self.index = scenes.build_index(ENTRIES, KOREA)

    def test_every_course_is_indexed(self):
        self.assertEqual(set(self.index["courses"]), {"c1"})

    def test_route_lines_are_points(self):
        line = self.index["courses"]["c1"]["lines"][0]
        self.assertEqual(line, {"mood": "city", "name": "구간", "points": [[10.0, 20.0], [30.5, 40.0]]})

    def test_source_notes_do_not_reach_the_app(self):
        place = self.index["courses"]["c1"]["places"][0]
        self.assertNotIn("evidence", place)
        self.assertNotIn("sourceUrl", place)

    def test_atlas_keeps_box_and_frame(self):
        atlas = self.index["courses"]["c1"]["atlas"]
        self.assertEqual(atlas["frame"], ATLAS["frame"])
        self.assertEqual(atlas["lines"], [{"mood": "river", "points": [[1.0, 2.0], [3.0, 4.0]]}])

    def test_sizes_and_labels_are_kept(self):
        self.assertEqual(self.index["sceneSize"], [2404, 1210])
        self.assertEqual(self.index["koreaSize"], [2020, 2347])
        self.assertEqual(self.index["labels"], KOREA["labels"])

    def test_moods_come_from_the_effect_tool(self):
        rain = self.index["moods"]["rain"]
        self.assertEqual((rain["shape"], rain["count"]), ("streak", 46))
        self.assertEqual((rain["night"], rain["dx"], rain["dy"], rain["near"], rain["far"]), ("#b9d2ec", -140, 640, 0.8, 1.3))


if __name__ == "__main__":
    unittest.main()
