"""파티 아바타 그림 규칙을 시험한다. 실행: python3 tools/test_make_avatars.py"""
import importlib.util
import tempfile
import unittest
from pathlib import Path

from PIL import Image

spec = importlib.util.spec_from_file_location("make_avatars", Path(__file__).parent / "make-avatars.py")
avatars = importlib.util.module_from_spec(spec)
spec.loader.exec_module(avatars)


class AvatarTest(unittest.TestCase):
    def test_ten_distinct_square_pictures(self):
        pictures = [avatars.draw(i) for i in range(10)]
        self.assertTrue(all(p.size == (256, 256) for p in pictures))
        self.assertEqual(len({p.tobytes() for p in pictures}), 10)

    def test_names_match_the_app_list(self):
        # core/share/ShareApi.kt 의 AVATARS 와 같아야 한다.
        self.assertEqual(avatars.names(), [f"avatar-{i:02d}" for i in range(1, 11)])

    def test_writes_one_png_per_name(self):
        with tempfile.TemporaryDirectory() as out:
            avatars.main(Path(out))
            files = sorted(p.name for p in Path(out).glob("*.png"))
            self.assertEqual(files, [f"{n}.png" for n in avatars.names()])
            self.assertEqual(Image.open(Path(out) / files[0]).size, (256, 256))


if __name__ == "__main__":
    unittest.main()
