"""풍경 사진을 코스에 넣는 규칙을 시험한다. 실행: python3 tools/test_add_scenery.py"""
import importlib.util
import unittest
from pathlib import Path

from PIL import Image

spec = importlib.util.spec_from_file_location("add_scenery", Path(__file__).parent / "add-scenery.py")
scenery = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scenery)


class CreditTest(unittest.TestCase):
    def test_a_credit_names_the_author_and_the_licence(self):
        credit = scenery.credit_of({"artist": "홍길동", "license": "CC BY 4.0"})
        self.assertEqual(credit, "사진: 홍길동 (CC BY 4.0, Wikimedia Commons)")


class SourceTest(unittest.TestCase):
    def test_it_asks_for_a_photo_at_app_size_not_the_original(self):
        url = scenery.source_url({"file": "Namhan River 01.jpg"})
        self.assertEqual(url, "https://commons.wikimedia.org/wiki/Special:FilePath/Namhan%20River%2001.jpg?width=1600")


class FitTest(unittest.TestCase):
    def test_a_big_photo_is_cut_down_to_the_app_size(self):
        image = scenery.fitted(Image.new("RGB", (4000, 3000)))
        self.assertEqual(image.size, scenery.SIZE)

    def test_a_tall_photo_keeps_the_app_size_too(self):
        self.assertEqual(scenery.fitted(Image.new("RGB", (1000, 3000))).size, scenery.SIZE)


class CourseFileTest(unittest.TestCase):
    def test_the_credit_goes_next_to_the_summary(self):
        text = '{"schemaVersion":2,"id":"c","summary":"길","path":[]}'
        self.assertEqual(
            scenery.with_credit(text, "사진: 가 (CC0)"),
            '{"schemaVersion":2,"id":"c","sceneryCredit":"사진: 가 (CC0)","summary":"길","path":[]}',
        )

    def test_a_second_run_replaces_the_old_credit(self):
        text = '{"id":"c","sceneryCredit":"옛 출처","summary":"길"}'
        self.assertEqual(scenery.with_credit(text, "새 출처"), '{"id":"c","sceneryCredit":"새 출처","summary":"길"}')


if __name__ == "__main__":
    unittest.main()
