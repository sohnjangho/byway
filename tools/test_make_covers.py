#!/usr/bin/env python3
"""make-covers.py 시험. 사용: python3 tools/test_make_covers.py"""
import importlib.util
import unittest
from pathlib import Path

from PIL import Image

spec = importlib.util.spec_from_file_location("make_covers", Path(__file__).parent / "make-covers.py")
mc = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mc)

# 남북으로 약 40km 뻗은 코스와 동서로 약 40km 뻗은 코스
TALL = [[35.0 + i * 0.004, 129.0] for i in range(91)]
WIDE = [[35.0, 129.0 + i * 0.005] for i in range(91)]


class FrameTest(unittest.TestCase):
    def test_the_whole_course_stays_inside_the_frame_with_a_margin(self):
        for path in (TALL, WIDE):
            zoom, left, top, window = mc.frame(path)
            margin = window * 0.05
            for point in path:
                x, y = mc.pixel(point, zoom)
                self.assertTrue(left + margin < x < left + window - margin)
                self.assertTrue(top + margin < y < top + window - margin)

    def test_the_long_side_of_the_course_fills_most_of_the_frame(self):
        zoom, _, _, window = mc.frame(TALL)
        ys = [mc.pixel(p, zoom)[1] for p in TALL]
        self.assertGreater((max(ys) - min(ys)) / window, 0.7)


class CloudTest(unittest.TestCase):
    LAND = (40, 70, 50)

    def picture_with_a_cloud(self):
        picture = Image.new("RGB", (400, 400), self.LAND)
        picture.paste((250, 250, 250), (150, 150, 250, 250))
        return picture

    def test_a_white_cloud_takes_the_colour_of_the_land_around_it(self):
        middle = mc.without_clouds(self.picture_with_a_cloud()).getpixel((200, 200))
        self.assertLess(max(middle), 100)

    def test_land_away_from_a_cloud_stays_as_it_was(self):
        self.assertEqual(self.LAND, mc.without_clouds(self.picture_with_a_cloud()).getpixel((10, 10)))

    def test_a_bright_but_coloured_field_is_not_a_cloud(self):
        picture = Image.new("RGB", (100, 100), (200, 170, 90))
        self.assertEqual((200, 170, 90), mc.without_clouds(picture).getpixel((50, 50)))


if __name__ == "__main__":
    unittest.main()
