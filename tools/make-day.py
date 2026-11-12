#!/usr/bin/env python3
"""야간 시안 *.dc.html 에서 주간판 *Day.dc.html 과 메뉴 화면의 창 크기별 판을 만든다.
시안은 야간 원본만 고치고 이 도구를 돌린다. *Day.dc.html, *Full, *Third 는 직접 고치지 않는다.
사용: python3 tools/make-day.py"""
import re
from pathlib import Path

SCREENS = Path(__file__).parent.parent / "design/screens"
# 메인 화면의 창 크기별 판. 원본 Main 은 2/3 창이다. 값은 에뮬레이터 실측이다.
# 메인 화면의 분위기별 판. 앱은 날씨와 시각으로 고르고 시안은 판을 나눠 보인다.
MOODS = {
    "MainRain": "rain", "MainSnow": "snow", "MainFog": "fog", "MainCloudy": "cloudy",
    "MainSunrise": "sunrise", "MainSunset": "sunset", "MainNight": "night",
    "MainSpring": "spring", "MainSummer": "summer", "MainAutumn": "autumn", "MainWinter": "winter",
}
# 밤에는 시스템이 야간 테마이므로 주간판을 만들지 않는다.
NIGHT_ONLY = {"MainNight"}
# 메뉴 화면마다 전체와 1/3 창 판을 만든다. 원본은 2/3 창이다.
MENU_SCREENS = ("Main", "Browse", "Ranking", "Me")
WINDOWS = {"Full": ("full", 2560, 1210), "Third": ("third", 841, 1210)}


def window_screens():
    return {f"{screen}{suffix}": (screen, *size) for screen in MENU_SCREENS for suffix, size in WINDOWS.items()}


def day_version(text, names):
    if text.count('data-theme="night"') != 1:
        raise SystemExit('화면마다 data-theme="night" 가 한 곳이어야 합니다')
    text = text.replace('data-theme="night"', 'data-theme="day"')
    text = re.sub(r"images/([\w/-]+)-night\.jpg", r"images/\1.jpg", text)
    text = text.replace('"coverNight"', '"coverDay"')
    return re.sub(rf"\b({'|'.join(names)})\.dc\.html", r"\1Day.dc.html", text)


def window_version(text, window, width, height):
    text = text.replace('data-window="two"', f'data-window="{window}"').replace('const WINDOW = "two";', f'const WINDOW = "{window}";')
    suffix = next(s for s, (w, *_) in WINDOWS.items() if w == window)
    text = re.sub(rf'href="({"|".join(MENU_SCREENS)})\.dc\.html', rf'href="\1{suffix}.dc.html', text)
    if window == "third":
        text = text.replace("const LIMIT = { view: 2, sight: 2, food: 2 };", "const LIMIT = { view: 1, sight: 1, food: 1 };")
    return re.sub(r'"\$preview":\{"width":\d+,"height":\d+\}', f'"$preview":{{"width":{width},"height":{height}}}', text)


def mood_version(text, mood):
    return text.replace('data-mood="plain"', f'data-mood="{mood}"').replace('const MOOD = "plain";', f'const MOOD = "{mood}";')


def main():
    main_screen = SCREENS / "Main.dc.html"
    for name, mood in MOODS.items():
        (SCREENS / f"{name}.dc.html").write_text(mood_version(main_screen.read_text(encoding="utf-8"), mood), encoding="utf-8")
    for name, (screen, window, width, height) in window_screens().items():
        text = window_version((SCREENS / f"{screen}.dc.html").read_text(encoding="utf-8"), window, width, height)
        (SCREENS / f"{name}.dc.html").write_text(text, encoding="utf-8")
    sources = sorted(p for p in SCREENS.glob("*.dc.html") if not p.name.endswith("Day.dc.html"))
    names = [p.name.removesuffix(".dc.html") for p in sources]
    for source in sources:
        if source.name.removesuffix(".dc.html") in NIGHT_ONLY:
            continue
        target = source.with_name(source.name.replace(".dc.html", "Day.dc.html"))
        target.write_text(day_version(source.read_text(encoding="utf-8"), names), encoding="utf-8")
        print(target.name)


if __name__ == "__main__":
    main()
