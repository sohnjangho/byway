"""tokens.json 에서 만드는 파일을 시험한다. 실행: python3 tools/test_make_theme.py"""
import importlib.util
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("make_theme", Path(__file__).parent / "make-theme.py")
theme = importlib.util.module_from_spec(spec)
spec.loader.exec_module(theme)

TOKENS = {
    "color": {
        "themes": [{"id": "night"}, {"id": "day"}],
        "tokens": [
            {"name": "bg", "value": {"night": "#131417", "day": "#f7f8fa"}, "usage": "바닥"},
            {"name": "bg-raised", "value": {"night": "#1d1e21", "day": "#ffffff"}},
            {"name": "line-subtle", "value": {"night": "#ffffff14", "day": "#13141712"}},
        ],
    },
    "type": {"groups": [{"name": "주행", "styles": [{"name": "drive-number", "fontSize": "112px", "lineHeight": "134px", "fontWeight": 700}]}]},
    "spacing": {"tokens": [{"name": "space-4", "value": "16px"}]},
    "radius": {"tokens": [{"name": "radius-md", "value": "24px"}]},
    "size": {"tokens": [{"name": "button-large", "value": "100px"}]},
    "shadow": {"tokens": [{"name": "shadow-card", "value": {"night": "0 2px 2px #00000040", "day": "0 6px 24px #1314171a"}}]},
}


class CssTest(unittest.TestCase):
    def setUp(self):
        self.css = theme.css(TOKENS)

    def test_night_values_are_the_default(self):
        self.assertIn(":root, [data-theme=\"night\"] {", self.css)
        self.assertIn("--bg: #131417;", self.css.split("[data-theme=\"day\"]")[0])

    def test_day_overrides_only_inside_its_block(self):
        self.assertIn("--bg: #f7f8fa;", self.css.split("[data-theme=\"day\"]")[1])

    def test_lengths_are_variables(self):
        self.assertIn("--space-4: 16px;", self.css)
        self.assertIn("--button-large: 100px;", self.css)

    def test_shadow_changes_with_theme(self):
        self.assertIn("--shadow-card: 0 6px 24px #1314171a;", self.css.split("[data-theme=\"day\"]")[1])

    def test_type_styles_become_classes(self):
        self.assertIn(".t-drive-number { font-size: 112px; line-height: 134px; font-weight: 700;", self.css)


class KotlinTest(unittest.TestCase):
    def setUp(self):
        self.kt = theme.kotlin(TOKENS)

    def test_names_become_camel_case(self):
        self.assertIn("val bgRaised: Int", self.kt)

    def test_each_theme_gets_a_palette(self):
        self.assertIn("val NightPalette = Palette(", self.kt)
        self.assertIn("val DayPalette = Palette(", self.kt)

    def test_opaque_colors_get_full_alpha(self):
        self.assertIn("bg = 0xFF131417.toInt()", self.kt)

    def test_css_alpha_moves_to_the_front(self):
        self.assertIn("lineSubtle = 0x14FFFFFF", self.kt)

    def test_generated_file_says_so(self):
        self.assertIn("tools/make-theme.py", self.kt.splitlines()[0])

    def test_sizes_become_dimens(self):
        self.assertIn("const val BUTTON_LARGE = 100", self.kt)
        self.assertIn("const val SPACE_4 = 16", self.kt)


if __name__ == "__main__":
    unittest.main()
