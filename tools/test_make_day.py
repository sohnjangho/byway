"""야간 시안에서 주간판을 만드는 규칙을 시험한다. 실행: python3 tools/test_make_day.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_day", Path(__file__).parent / "make-day.py")
day = importlib.util.module_from_spec(spec)
spec.loader.exec_module(day)

SOURCE = '<div class="stage" data-theme="night"><a href="Main.dc.html"><img src="images/covers/paldang-sunset-night.jpg"></a></div>'


class DayVersionTest(unittest.TestCase):
    def setUp(self):
        self.page = day.day_version(SOURCE, ["Main", "Preview"])

    def test_the_theme_becomes_day(self):
        self.assertIn('data-theme="day"', self.page)

    def test_covers_lose_the_night_suffix(self):
        self.assertIn("images/covers/paldang-sunset.jpg", self.page)

    def test_links_stay_inside_the_day_screens(self):
        self.assertIn('href="MainDay.dc.html"', self.page)

    def test_screens_read_the_day_cover_field(self):
        page = day.day_version(SOURCE + "", ["Main"]) + day.day_version('<i data-theme="night"></i>const COVER = "coverNight";', ["Main"])
        self.assertIn('const COVER = "coverDay";', page)

    def test_a_screen_must_name_its_theme_once(self):
        with self.assertRaises(SystemExit):
            day.day_version(SOURCE + SOURCE, ["Main"])


if __name__ == "__main__":
    unittest.main()
