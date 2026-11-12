#!/usr/bin/env python3
"""파티 아바타 그림 10장을 직접 그린다(공유 설계 6-2). 사진이 아니라 도형이라 저작권·초상권 문제가 없다.

실행: python3 tools/make-avatars.py → app/src/main/assets/avatars/avatar-01.png … avatar-10.png
앱이 원형으로 잘라 물방울 핀에 넣는다. 그림 이름은 core/share/ShareApi.kt 의 AVATARS 와 같다.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/assets/avatars"
SIZE = 256
SCALE = 4  # 크게 그린 뒤 줄여 가장자리를 매끄럽게 한다.
INK = (255, 248, 236)
# 배경은 서로 잘 구별되는 색. 마지막 둘은 시연용 가상 멤버가 쓴다.
BACKGROUNDS = [
    (232, 129, 74), (70, 140, 200), (76, 160, 110), (220, 170, 60), (110, 100, 190),
    (200, 80, 110), (60, 165, 170), (150, 120, 80), (120, 130, 145), (180, 95, 170),
]


def names():
    return [f"avatar-{i:02d}" for i in range(1, len(BACKGROUNDS) + 1)]


def _mountain(d, s):
    d.polygon([(0.12 * s, 0.75 * s), (0.42 * s, 0.30 * s), (0.62 * s, 0.58 * s), (0.72 * s, 0.45 * s), (0.90 * s, 0.75 * s)], fill=INK)


def _wave(d, s):
    for row in (0.42, 0.62):
        pts = [(x, row * s + math.sin(x / s * math.pi * 3) * 0.06 * s) for x in range(int(0.12 * s), int(0.9 * s))]
        d.line(pts, fill=INK, width=int(0.07 * s))


def _tree(d, s):
    d.polygon([(0.5 * s, 0.2 * s), (0.25 * s, 0.62 * s), (0.75 * s, 0.62 * s)], fill=INK)
    d.rectangle([0.45 * s, 0.62 * s, 0.55 * s, 0.8 * s], fill=INK)


def _sun(d, s):
    c, r = 0.5 * s, 0.17 * s
    d.ellipse([c - r, c - r, c + r, c + r], fill=INK)
    for k in range(8):
        a = k * math.pi / 4
        d.line([(c + math.cos(a) * 0.25 * s, c + math.sin(a) * 0.25 * s), (c + math.cos(a) * 0.36 * s, c + math.sin(a) * 0.36 * s)], fill=INK, width=int(0.05 * s))


def _moon(d, s, background):
    d.ellipse([0.25 * s, 0.22 * s, 0.75 * s, 0.72 * s], fill=INK)
    d.ellipse([0.36 * s, 0.16 * s, 0.86 * s, 0.66 * s], fill=background)


def _star(d, s):
    c = 0.5 * s
    pts = [(c + math.cos(-math.pi / 2 + k * math.pi / 5) * (0.34 if k % 2 == 0 else 0.14) * s,
            c + math.sin(-math.pi / 2 + k * math.pi / 5) * (0.34 if k % 2 == 0 else 0.14) * s) for k in range(10)]
    d.polygon(pts, fill=INK)


def _car(d, s, background):
    d.rounded_rectangle([0.16 * s, 0.45 * s, 0.84 * s, 0.66 * s], radius=0.06 * s, fill=INK)
    d.rounded_rectangle([0.30 * s, 0.32 * s, 0.68 * s, 0.50 * s], radius=0.06 * s, fill=INK)
    for x in (0.32, 0.68):
        d.ellipse([(x - 0.09) * s, 0.58 * s, (x + 0.09) * s, 0.76 * s], fill=background, outline=INK, width=int(0.04 * s))


def _leaf(d, s, background):
    d.ellipse([0.22 * s, 0.30 * s, 0.78 * s, 0.70 * s], fill=INK)
    d.line([(0.22 * s, 0.70 * s), (0.70 * s, 0.38 * s)], fill=background, width=int(0.04 * s))


def _cloud(d, s):
    for x, y, r in ((0.38, 0.52, 0.16), (0.56, 0.44, 0.19), (0.70, 0.56, 0.13)):
        d.ellipse([(x - r) * s, (y - r) * s, (x + r) * s, (y + r) * s], fill=INK)
    d.rectangle([0.30 * s, 0.56 * s, 0.74 * s, 0.68 * s], fill=INK)


def _road(d, s):
    pts = [(0.5 * s + math.sin(t / 40 * math.pi * 1.5) * 0.22 * s, (0.15 + t / 40 * 0.7) * s) for t in range(41)]
    d.line(pts, fill=INK, width=int(0.12 * s))


MOTIFS = [_mountain, _wave, _tree, _sun, _moon, _star, _car, _leaf, _cloud, _road]


def draw(index):
    big = SIZE * SCALE
    background = BACKGROUNDS[index]
    picture = Image.new("RGB", (big, big), background)
    d = ImageDraw.Draw(picture)
    motif = MOTIFS[index]
    if motif.__code__.co_argcount == 3:
        motif(d, big, background)
    else:
        motif(d, big)
    return picture.resize((SIZE, SIZE), Image.LANCZOS)


def main(out=OUT):
    out.mkdir(parents=True, exist_ok=True)
    for i, name in enumerate(names()):
        draw(i).save(out / f"{name}.png", optimize=True)
    print(out, len(names()))


if __name__ == "__main__":
    main(Path(sys.argv[1]) if len(sys.argv) > 1 else OUT)
