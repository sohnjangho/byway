"""풍경 사진을 코스에 넣는 규칙을 시험한다. 실행: python3 tools/test_add_scenery.py"""
import importlib.util
import json
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


class DetailTest(unittest.TestCase):
    entry = {
        "artist": "홍길동", "license": "CC BY 2.0", "file": "Namhan River 01.jpg",
        "page": "https://commons.wikimedia.org/wiki/File:Namhan_River_01.jpg",
    }

    def detail(self, **changes):
        return scenery.detail_of({**self.entry, **changes})

    def test_it_names_the_title_author_licence_and_source(self):
        detail = self.detail()
        self.assertIn("홍길동", detail)
        self.assertIn("「Namhan River 01」", detail)
        self.assertIn("CC BY 2.0 https://creativecommons.org/licenses/by/2.0/", detail)
        self.assertIn("https://commons.wikimedia.org/wiki/File:Namhan_River_01.jpg", detail)

    def test_it_says_the_photo_was_changed(self):
        self.assertIn("흐리게 변형", self.detail())
        self.assertIn("자르고", self.detail())

    def test_a_share_alike_photo_states_the_same_licence_for_the_changed_copy(self):
        self.assertIn("같은 라이선스", self.detail(license="CC BY-SA 3.0"))
        self.assertNotIn("같은 라이선스", self.detail())

    def test_the_address_is_readable(self):
        detail = self.detail(page="https://commons.wikimedia.org/wiki/File:%EA%B0%90_blue.jpg")
        self.assertIn("File:감_blue.jpg", detail)

    def test_a_public_domain_photo_has_no_licence_address(self):
        detail = self.detail(license="Public domain")
        self.assertIn("Public domain", detail)
        self.assertNotIn("creativecommons.org", detail)

    def test_an_unknown_licence_is_refused(self):
        with self.assertRaises(KeyError):
            self.detail(license="모르는 라이선스")

    def test_every_listed_photo_has_a_clean_author_and_a_known_licence(self):
        plan = json.loads(scenery.PLAN.read_text(encoding="utf-8"))
        for course_id, entry in plan.items():
            self.assertNotRegex(entry["artist"], r"\[\d+\]", course_id)
            self.assertIn(entry["license"], scenery.LICENCE_LINKS, course_id)


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

    def test_the_full_credit_also_goes_next_to_the_summary_once(self):
        once = scenery.with_detail('{"id":"c","summary":"길"}', "옛 전문")
        self.assertEqual(scenery.with_detail(once, "새 전문"), '{"id":"c","sceneryDetail":"새 전문","summary":"길"}')


if __name__ == "__main__":
    unittest.main()
