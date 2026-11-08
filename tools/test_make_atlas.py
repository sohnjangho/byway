"""한국 전체 지도와 그 위 코스 좌표를 만드는 규칙을 시험한다. 실행: python3 tools/test_make_atlas.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_atlas", Path(__file__).parent / "make-atlas.py")
atlas = importlib.util.module_from_spec(spec)
spec.loader.exec_module(atlas)

PATH = [[37.39, 127.11], [37.45, 127.20], [37.55, 127.31], [37.52, 127.40]]
COURSE = {"id": "c", "path": PATH, "sections": [
    {"name": "가", "mood": "forest", "startIndex": 0, "endIndex": 1},
    {"name": "나", "mood": "river", "startIndex": 2, "endIndex": 2},
]}


class ProjectTest(unittest.TestCase):
    def test_the_north_west_corner_is_the_origin(self):
        south, north, west, east = atlas.BOUNDS
        x, y = atlas.to_atlas([north, west])
        self.assertAlmostEqual(x, 0, places=6)
        self.assertAlmostEqual(y, 0, places=6)

    def test_east_is_right_and_north_is_up(self):
        x1, y1 = atlas.to_atlas([37.0, 127.0])
        x2, y2 = atlas.to_atlas([37.5, 127.5])
        self.assertGreater(x2, x1)
        self.assertLess(y2, y1)

    def test_the_map_size_covers_the_bounds(self):
        south, north, west, east = atlas.BOUNDS
        width, height = atlas.size()
        x, y = atlas.to_atlas([south, east])
        self.assertAlmostEqual(width, x, delta=1)
        self.assertAlmostEqual(height, y, delta=1)


class CourseTest(unittest.TestCase):
    def test_the_box_holds_every_point(self):
        box = atlas.box(PATH)
        for point in PATH:
            x, y = atlas.to_atlas(point)
            self.assertTrue(box["x"] <= x <= box["x"] + box["w"])
            self.assertTrue(box["y"] <= y <= box["y"] + box["h"])

    def test_the_scene_frame_keeps_the_scene_shape_and_holds_the_route(self):
        view = {"zoom": 11, "scale": 1.5, "left": 0, "top": 0}
        x, y = atlas.pixel_at(PATH[0], 11)
        view["left"], view["top"] = x - 1600 / 1.5, y - 500 / 1.5
        frame = atlas.frame(view)
        self.assertAlmostEqual(frame["w"] / frame["h"], 2404 / 1210, places=3)
        px, py = atlas.to_atlas(PATH[0])
        self.assertAlmostEqual(px, frame["x"] + 1600 * frame["w"] / 2404, delta=0.5)
        self.assertAlmostEqual(py, frame["y"] + 500 * frame["h"] / 1210, delta=0.5)

    def test_each_section_becomes_one_line_with_its_mood(self):
        lines = atlas.lines(COURSE)
        self.assertEqual([line["mood"] for line in lines], ["forest", "river"])
        self.assertTrue(all(line["d"].startswith("M") for line in lines))


class LabelTest(unittest.TestCase):
    def test_labels_sit_inside_the_map(self):
        width, height = atlas.size()
        for label in atlas.labels():
            self.assertTrue(0 < label["x"] < width and 0 < label["y"] < height, label["name"])

    def test_every_course_region_has_a_label(self):
        names = {label["name"] for label in atlas.labels() if label["kind"] == "region"}
        self.assertTrue({"수도권", "강원", "충청", "경상", "전라", "남해안", "제주"} <= names)


if __name__ == "__main__":
    unittest.main()
