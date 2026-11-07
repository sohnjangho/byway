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


class WindowVersionTest(unittest.TestCase):
    source = '<div class="stage" data-theme="night" data-window="two"></div><script data-props=\'{"$preview":{"width":1700,"height":1184}}\'></script>'

    def test_the_stage_names_its_window(self):
        self.assertIn('data-window="third"', day.window_version(self.source, "third", 841, 1210))

    def test_the_preview_takes_the_window_size(self):
        self.assertIn('"$preview":{"width":841,"height":1210}', day.window_version(self.source, "third", 841, 1210))


class MoodVersionTest(unittest.TestCase):
    source = '<div data-mood="plain"></div><script>const MOOD = "plain";</script>'

    def test_the_stage_names_its_mood(self):
        self.assertIn('data-mood="rain"', day.mood_version(self.source, "rain"))

    def test_the_script_knows_its_mood(self):
        self.assertIn('const MOOD = "rain";', day.mood_version(self.source, "rain"))


if __name__ == "__main__":
    unittest.main()
