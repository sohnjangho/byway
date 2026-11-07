"""메인 분위기 효과 스타일을 만드는 규칙을 시험한다. 실행: python3 tools/test_make_effects.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_effects", Path(__file__).parent / "make-effects.py")
fx = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fx)


class CssTest(unittest.TestCase):
    def setUp(self):
        self.css = fx.css()

    def test_every_mood_has_an_effect(self):
        for mood in ("rain", "snow", "fog", "cloudy", "sunrise", "sunset", "night", "spring", "summer", "autumn", "winter"):
            self.assertIn(f'[data-mood="{mood}"]', self.css)

    def test_the_map_and_route_are_never_touched(self):
        for name in ("scene-img", "scene-line", "route", "filter:"):
            self.assertNotIn(name, self.css)

    def test_each_theme_gets_its_own_colours(self):
        self.assertIn('[data-theme="day"][data-mood="snow"]', self.css)

    def test_people_who_turn_motion_off_get_a_still_effect(self):
        self.assertIn("prefers-reduced-motion: reduce", self.css)


class TileTest(unittest.TestCase):
    def test_the_same_seed_makes_the_same_tile(self):
        self.assertEqual(fx.tile("snow", "#fff", 1), fx.tile("snow", "#fff", 1))

    def test_mist_fades_out_at_its_edges(self):
        self.assertIn("radialGradient", fx.tile("fog", "#fff", 1))

    def test_mist_wraps_so_the_tile_seam_stays_hidden(self):
        self.assertGreater(fx.tile("fog", "#fff", 1).count("<ellipse"), fx.SHAPES["fog"][1])

    def test_a_tile_is_an_svg_data_address(self):
        self.assertTrue(fx.data_address(fx.tile("snow", "#fff", 1)).startswith("data:image/svg+xml,"))


if __name__ == "__main__":
    unittest.main()
