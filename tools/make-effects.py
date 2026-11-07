#!/usr/bin/env python3
"""메인 화면의 분위기 효과를 design/screens/effects.css 로 만든다. 만든 파일은 직접 고치지 않는다.
효과는 지도와 경로 밖 정보 영역에서만 움직인다. 지도와 경로의 색은 건드리지 않는다.
사용: python3 tools/make-effects.py"""
import random
import urllib.parse
from pathlib import Path

OUT = Path(__file__).parent.parent / "design/screens/effects.css"
WIDTH, HEIGHT = 480, 640
FIRST_LINE = "/* tools/make-effects.py 가 만든 파일이다. 직접 고치지 않는다. */"


def flake(r, colour):
    return f'<circle cx="{r.randint(8, WIDTH - 8)}" cy="{r.randint(8, HEIGHT - 8)}" r="{r.uniform(1.5, 4.5):.1f}" fill="{colour}" opacity="{r.uniform(0.5, 0.95):.2f}"/>'


def streak(r, colour):
    x, y, length = r.randint(30, WIDTH - 10), r.randint(10, HEIGHT - 70), r.randint(26, 58)
    return f'<line x1="{x}" y1="{y}" x2="{x - length * 0.22:.1f}" y2="{y + length}" stroke="{colour}" stroke-width="{r.uniform(1.2, 2.2):.1f}" stroke-linecap="round" opacity="{r.uniform(0.35, 0.8):.2f}"/>'


def petal(r, colour):
    x, y = r.randint(14, WIDTH - 14), r.randint(14, HEIGHT - 14)
    return f'<ellipse cx="{x}" cy="{y}" rx="{r.uniform(5, 9):.1f}" ry="{r.uniform(3, 5):.1f}" fill="{colour}" opacity="{r.uniform(0.6, 0.95):.2f}" transform="rotate({r.randint(0, 180)} {x} {y})"/>'


def leaf(r, colour):
    x, y, size = r.randint(24, WIDTH - 24), r.randint(24, HEIGHT - 24), r.uniform(0.8, 1.5)
    shape = "M0-12C7-8 9 0 0 12C-9 0-7-8 0-12Z"
    return f'<path d="{shape}" fill="{colour}" opacity="{r.uniform(0.7, 1):.2f}" transform="translate({x} {y}) rotate({r.randint(0, 359)}) scale({size:.2f})"/>'


def star(r, colour):
    return f'<circle cx="{r.randint(6, WIDTH - 6)}" cy="{r.randint(6, HEIGHT - 6)}" r="{r.uniform(0.8, 2.2):.1f}" fill="{colour}" opacity="{r.uniform(0.4, 1):.2f}"/>'


def crystal(r, colour):
    x, y, size = r.randint(16, WIDTH - 16), r.randint(16, HEIGHT - 16), r.uniform(5, 10)
    arms = "".join(f'<line x1="0" y1="0" x2="0" y2="{-size:.1f}" transform="rotate({angle})"/>' for angle in range(0, 360, 60))
    return f'<g transform="translate({x} {y})" stroke="{colour}" stroke-width="1.4" stroke-linecap="round" opacity="{r.uniform(0.5, 0.9):.2f}">{arms}</g>'


def mist(r, colour):
    """가장자리가 사라지는 안개 덩어리. 가로로 흐르므로 양옆에 한 벌씩 더 그려 타일 경계를 감춘다."""
    x, ry = r.randint(0, WIDTH), r.randint(70, 130)
    y, rx, opacity = r.randint(ry, HEIGHT - ry), r.randint(200, 320), r.uniform(0.5, 0.95)
    return "".join(f'<ellipse cx="{x + shift}" cy="{y}" rx="{rx}" ry="{ry}" fill="url(#m)" opacity="{opacity:.2f}"/>' for shift in (-WIDTH, 0, WIDTH))


def mote(r, colour):
    return f'<circle cx="{r.randint(8, WIDTH - 8)}" cy="{r.randint(8, HEIGHT - 8)}" r="{r.uniform(1.5, 3.5):.1f}" fill="{colour}" opacity="{r.uniform(0.3, 0.8):.2f}"/>'


SHAPES = {"rain": (streak, 46), "snow": (flake, 42), "spring": (petal, 22), "autumn": (leaf, 16), "night": (star, 60),
          "winter": (crystal, 12), "fog": (mist, 7), "cloudy": (mist, 6), "sunrise": (mote, 26), "sunset": (mote, 26), "summer": (mote, 22)}
# 야간색, 주간색, 바탕 빛, 움직임(가로, 세로, 앞 층 초, 뒤 층 초)
LOOKS = {
    "rain": ("#b9d2ec", "#5c7f9f", "rgba(80, 110, 145, 0.26)", (-140, 640, 0.8, 1.3)),
    "snow": ("#ffffff", "#9fb7cf", "rgba(190, 210, 235, 0.16)", (40, 640, 11, 18)),
    "fog": ("#8a949e", "#9aa7b2", "rgba(150, 160, 170, 0.12)", (480, 0, 46, 74)),
    "cloudy": ("#7c8691", "#8c98a4", "rgba(120, 130, 145, 0.12)", (480, 0, 80, 130)),
    "sunrise": ("#ffd9a8", "#d98a4a", "rgba(255, 190, 130, 0.16)", (40, -640, 30, 46)),
    "sunset": ("#ffb36b", "#d4702a", "rgba(245, 130, 70, 0.34)", (-40, -640, 30, 46)),
    "night": ("#ffffff", "#6a7fb0", "rgba(40, 55, 110, 0.36)", (0, 0, 0, 0)),
    "spring": ("#ffc2d8", "#e58aae", "rgba(255, 170, 200, 0.18)", (120, 640, 16, 25)),
    "summer": ("#fff3b0", "#d7a93a", "rgba(120, 220, 215, 0.1)", (60, -640, 24, 38)),
    "autumn": ("#e8863a", "#c9601c", "rgba(214, 120, 50, 0.2)", (160, 640, 18, 28)),
    "winter": ("#d6e8fa", "#7f9dbd", "rgba(170, 200, 235, 0.2)", (30, 640, 30, 48)),
}
THEMES = {"night": 0, "day": 1}


def tile(mood, colour, seed):
    draw, count = SHAPES[mood]
    r = random.Random(f"{mood}{seed}")
    shapes = "".join(draw(r, colour) for _ in range(count))
    defs = ""
    if draw is mist:
        defs = f'<defs><radialGradient id="m"><stop offset="0" stop-color="{colour}" stop-opacity="0.34"/><stop offset="1" stop-color="{colour}" stop-opacity="0"/></radialGradient></defs>'
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{WIDTH}" height="{HEIGHT}">{defs}{shapes}</svg>'


def data_address(svg):
    return "data:image/svg+xml," + urllib.parse.quote(svg, safe="=:/ '")


def rules(mood, theme):
    look = LOOKS[mood]
    colour = look[THEMES[theme]]
    dx, dy, near, far = look[3]
    selector = f'.stage[data-theme="{theme}"][data-mood="{mood}"] .scene-mood'
    lines = [f"{selector} {{ background: linear-gradient(to bottom, {look[2]}, transparent 72%); }}"]
    for layer, seed, seconds in (("before", 1, near), ("after", 2, far)):
        name = "fx-twinkle" if mood == "night" else "fx-drift"
        timing = f"{seconds}s linear infinite" if seconds else f"{3 + seed}s ease-in-out infinite alternate"
        address = data_address(tile(mood, colour, seed))
        lines.append(f"""{selector}::{layer} {{ background-image: url("{address}"); --dx: {dx}px; --dy: {dy}px; animation: {name} {timing}; }}""")
    return lines


def css():
    lines = [
        FIRST_LINE,
        ".scene-mood { position: absolute; top: 0; left: 0; bottom: 0; width: 920px; overflow: hidden; pointer-events: none; -webkit-mask-image: linear-gradient(to right, #000 62%, transparent); mask-image: linear-gradient(to right, #000 62%, transparent); }",
        '.stage[data-window="full"] .scene-mood { width: 1340px; }',
        '.stage[data-window="third"] .scene-mood { top: auto; width: 100%; height: 480px; -webkit-mask-image: linear-gradient(to bottom, transparent, #000 22%); mask-image: linear-gradient(to bottom, transparent, #000 22%); }',
        '.scene-mood::before, .scene-mood::after { content: ""; position: absolute; inset: 0; background-repeat: repeat; }',
        ".scene-mood::after { background-position: 160px 220px; opacity: 0.7; }",
        "@keyframes fx-drift { to { background-position: var(--dx) var(--dy); } }",
        "@keyframes fx-twinkle { from { opacity: 0.35; } to { opacity: 1; } }",
    ]
    for mood in LOOKS:
        for theme in THEMES:
            lines += rules(mood, theme)
    lines.append("@media (prefers-reduced-motion: reduce) { .scene-mood::before, .scene-mood::after { animation: none !important; } }")
    return "\n".join(lines) + "\n"


def main():
    OUT.write_text(css(), encoding="utf-8")
    print(OUT.name, OUT.stat().st_size)


if __name__ == "__main__":
    main()
