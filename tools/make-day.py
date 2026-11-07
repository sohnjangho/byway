#!/usr/bin/env python3
"""야간 시안 *.dc.html 에서 주간판 *Day.dc.html 을 만든다.
시안은 야간 원본만 고치고 이 도구를 돌린다. *Day.dc.html 은 직접 고치지 않는다.
사용: python3 tools/make-day.py"""
import re
from pathlib import Path

SCREENS = Path(__file__).parent.parent / "design/screens"


def day_version(text, names):
    if text.count('data-theme="night"') != 1:
        raise SystemExit('화면마다 data-theme="night" 가 한 곳이어야 합니다')
    text = text.replace('data-theme="night"', 'data-theme="day"')
    text = re.sub(r"images/([\w/-]+)-night\.jpg", r"images/\1.jpg", text)
    return re.sub(rf"\b({'|'.join(names)})\.dc\.html", r"\1Day.dc.html", text)


def main():
    sources = sorted(p for p in SCREENS.glob("*.dc.html") if not p.name.endswith("Day.dc.html"))
    names = [p.name.removesuffix(".dc.html") for p in sources]
    for source in sources:
        target = source.with_name(source.name.replace(".dc.html", "Day.dc.html"))
        target.write_text(day_version(source.read_text(encoding="utf-8"), names), encoding="utf-8")
        print(target.name)


if __name__ == "__main__":
    main()
