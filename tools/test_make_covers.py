#!/usr/bin/env python3
"""make-covers.py 시험. 사용: python3 tools/test_make_covers.py"""
import importlib.util
import unittest
from pathlib import Path
from unittest import mock

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

    def picture_with_a_cloud(self, cloud=(250, 250, 250)):
        picture = Image.new("RGB", (400, 400), self.LAND)
        picture.paste(cloud, (150, 150, 250, 250))
        return picture

    def assert_land_colour(self, pixel):
        for channel, land in zip(pixel, self.LAND):
            self.assertAlmostEqual(land, channel, delta=8)

    def test_a_white_cloud_takes_the_colour_of_the_land_around_it(self):
        self.assert_land_colour(mc.without_clouds(self.picture_with_a_cloud()).getpixel((200, 200)))

    def test_a_thin_grey_cloud_is_covered_too(self):
        self.assert_land_colour(mc.without_clouds(self.picture_with_a_cloud((200, 200, 200))).getpixel((200, 200)))

    def test_the_haze_at_the_edge_of_a_cloud_fades_more_than_halfway_to_the_land(self):
        picture = self.picture_with_a_cloud()
        picture.paste((120, 130, 125), (146, 150, 150, 250))
        self.assertLess(mc.without_clouds(picture).getpixel((148, 200))[0], 80)

    def test_land_away_from_a_cloud_stays_as_it_was(self):
        self.assertEqual(self.LAND, mc.without_clouds(self.picture_with_a_cloud()).getpixel((10, 10)))

    def test_a_bright_but_coloured_field_is_not_a_cloud(self):
        picture = self.picture_with_a_cloud((210, 180, 140))
        self.assertEqual((210, 180, 140), mc.without_clouds(picture).getpixel((200, 200)))

    def test_each_side_of_a_cloud_takes_the_colour_of_the_land_next_to_it(self):
        picture = self.picture_with_a_cloud()
        picture.paste((120, 110, 60), (200, 0, 400, 150))
        picture.paste((120, 110, 60), (250, 150, 400, 400))
        picture.paste((120, 110, 60), (200, 250, 400, 400))
        filled = mc.without_clouds(picture)
        self.assertLess(filled.getpixel((160, 200))[0], filled.getpixel((240, 200))[0])

    def test_a_picture_that_is_all_cloud_stays_as_it_was(self):
        picture = Image.new("RGB", (100, 100), (250, 250, 250))
        self.assertEqual((250, 250, 250), mc.without_clouds(picture).getpixel((50, 50)))


class CoverTest(unittest.TestCase):
    def test_a_cover_shows_no_cloud(self):
        # 타일마다 가운데에 구름이 있는 땅. 숲 색 코스 선은 구름보다 어둡다
        land = Image.new("RGB", (256, 256), (40, 70, 50))
        land.paste((250, 250, 250), (96, 96, 160, 160))
        course = {"path": TALL, "sections": [{"startIndex": 0, "endIndex": len(TALL) - 1, "mood": "forest"}]}
        with mock.patch.object(mc, "tile", lambda *_: land):
            cover = mc.cover(course)
        self.assertLess(max(min(pixel) for pixel in cover.getdata()), 200)


class NightTest(unittest.TestCase):
    course = {"path": TALL, "sections": [{"startIndex": 0, "endIndex": 90, "mood": "river"}]}

    def covers(self):
        green = Image.new("RGB", (256, 256), (60, 110, 70))
        with mock.patch.object(mc, "tile", return_value=green):
            return mc.cover(self.course), mc.cover(self.course, night=True)

    def test_the_night_photo_is_darker(self):
        day, night = self.covers()
        self.assertLess(sum(night.getpixel((20, 20))), sum(day.getpixel((20, 20))) * 0.8)

    def test_the_course_line_stays_as_bright_at_night(self):
        day, night = self.covers()
        middle = mc.SIZE // 2
        self.assertEqual(day.getpixel((middle, middle)), night.getpixel((middle, middle)))

    def test_section_colours_come_from_the_design_tokens(self):
        self.assertEqual(mc.section_colours()["river"], (0x4F, 0xD1, 0xE8))


class LayerTest(unittest.TestCase):
    def test_each_layer_has_its_own_tiles_and_cache(self):
        self.assertIn("s2cloudless", mc.tile_url("satellite", 9, 1, 2))
        self.assertIn("terrain-light", mc.tile_url("map", 9, 1, 2))
        self.assertNotEqual(mc.cache_file("satellite", 9, 1, 2).parent, mc.cache_file("map", 9, 1, 2).parent)

    def test_a_tile_url_puts_the_row_before_the_column(self):
        self.assertTrue(mc.tile_url("map", 9, 1, 2).endswith("/9/2/1.jpg"))

    def test_map_files_carry_the_layer_name(self):
        self.assertEqual(mc.file_name("c", "satellite"), "c.jpg")
        self.assertEqual(mc.file_name("c", "satellite", night=True), "c-night.jpg")
        self.assertEqual(mc.file_name("c", "map"), "c-map.jpg")
        self.assertEqual(mc.file_name("c", "map", night=True), "c-map-night.jpg")

    def test_white_land_on_the_map_is_not_treated_as_cloud(self):
        paper = Image.new("RGB", (256, 256), (240, 240, 240))
        paper.paste((40, 70, 50), (0, 0, 256, 60))
        with mock.patch.object(mc, "tile", lambda *_: paper):
            image = mc.ground(TALL, "map")[0]
        self.assertIn((240, 240, 240), set(image.getdata()))


class NightMapTest(unittest.TestCase):
    WATER, LAND, ROAD = (146, 164, 176), (232, 232, 232), (200, 200, 200)
    course = {"path": TALL, "sections": [{"startIndex": 0, "endIndex": 90, "mood": "river"}]}

    def night(self):
        picture = Image.new("RGB", (90, 30), self.LAND)
        picture.paste(self.WATER, (0, 0, 30, 30))
        picture.paste(self.ROAD, (60, 0, 90, 30))
        return mc.night_map(picture)

    def test_the_background_comes_from_the_design_tokens(self):
        self.assertEqual(mc.night_background(), (0x13, 0x14, 0x17))

    def test_water_becomes_the_app_background(self):
        self.assertEqual(self.night().getpixel((15, 15)), mc.night_background())

    def test_land_is_a_little_brighter_than_the_background(self):
        land = self.night().getpixel((45, 15))
        self.assertGreater(sum(land), sum(mc.night_background()) + 30)
        self.assertLess(max(land), 90)

    def test_roads_are_brighter_than_land(self):
        night = self.night()
        self.assertGreater(sum(night.getpixel((75, 15))), sum(night.getpixel((45, 15))))

    def test_a_map_cover_at_night_uses_the_map_tones(self):
        water = Image.new("RGB", (256, 256), self.WATER)
        with mock.patch.object(mc, "tile", lambda *_: water):
            cover = mc.cover(self.course, night=True, layer="map")
        self.assertEqual(cover.getpixel((5, 5)), mc.night_background())


if __name__ == "__main__":
    unittest.main()
