"""메인 화면의 코스 장면을 만드는 규칙을 시험한다. 실행: python3 tools/test_make_scenes.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_scenes", Path(__file__).parent / "make-scenes.py")
scenes = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scenes)

WIDE = [[37.5, 127.0 + i * 0.01] for i in range(40)]
TALL = [[37.0 + i * 0.01, 127.0] for i in range(40)]


class FitTest(unittest.TestCase):
    def inside(self, path):
        view = scenes.fit(path)
        left, top, right, bottom = scenes.ROUTE_BOX
        for point in path:
            x, y = scenes.project(point, view)
            self.assertGreaterEqual(x, left - 1)
            self.assertLessEqual(x, right + 1)
            self.assertGreaterEqual(y, top - 1)
            self.assertLessEqual(y, bottom + 1)

    def test_a_wide_course_stays_inside_the_route_box(self):
        self.inside(WIDE)

    def test_a_tall_course_stays_inside_the_route_box(self):
        self.inside(TALL)

    def test_the_course_fills_the_box_on_one_side(self):
        view = scenes.fit(WIDE)
        xs = [scenes.project(p, view)[0] for p in WIDE]
        left, _, right, _ = scenes.ROUTE_BOX
        self.assertGreater(max(xs) - min(xs), (right - left) * 0.95)


class PathTextTest(unittest.TestCase):
    def test_points_become_an_svg_line(self):
        self.assertEqual(scenes.line_text([(1.0, 2.0), (3.5, 4.3)]), "M1 2 L3.5 4.3")


if __name__ == "__main__":
    unittest.main()
