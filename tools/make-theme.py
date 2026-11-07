#!/usr/bin/env python3
"""디자인 값의 유일한 원본 design/tokens.json 에서 시안용 CSS와 앱용 Kotlin을 만든다.
값을 바꿀 때는 design/tokens.json 만 고치고 이 도구를 돌린다. 만든 파일은 직접 고치지 않는다.
사용: python3 tools/make-theme.py"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).parent.parent
SOURCE = ROOT / "design/tokens.json"
CSS_OUT = ROOT / "design/screens/tokens.css"
KOTLIN_OUT = ROOT / "app/src/main/java/com/byway/ui/Palette.kt"
FIRST_LINE = "/* tools/make-theme.py 가 design/tokens.json 에서 만든 파일이다. 직접 고치지 않는다. */"
LENGTH_FAMILIES = ("spacing", "radius", "size")


def per_theme(tokens):
    """테마별 (이름, 값) 목록. 색과 그림자만 테마마다 다르다."""
    themes = [theme["id"] for theme in tokens["color"]["themes"]]
    entries = tokens["color"]["tokens"] + tokens.get("shadow", {}).get("tokens", [])
    return {theme: [(entry["name"], entry["value"][theme]) for entry in entries] for theme in themes}


def css(tokens):
    themes = per_theme(tokens)
    first, *rest = themes
    lines = [FIRST_LINE, f':root, [data-theme="{first}"] {{']
    lines += [f"  --{name}: {value};" for name, value in themes[first]]
    for family in LENGTH_FAMILIES:
        lines += [f"  --{entry['name']}: {entry['value']};" for entry in tokens.get(family, {}).get("tokens", [])]
    lines.append("}")
    for theme in rest:
        lines.append(f'[data-theme="{theme}"] {{')
        lines += [f"  --{name}: {value};" for name, value in themes[theme]]
        lines.append("}")
    for group in tokens["type"]["groups"]:
        for style in group["styles"]:
            spacing = f" letter-spacing: {style['letterSpacing']};" if "letterSpacing" in style else ""
            lines.append(
                f".t-{style['name']} {{ font-size: {style['fontSize']}; line-height: {style['lineHeight']}; "
                f"font-weight: {style['fontWeight']};{spacing} }}"
            )
    return "\n".join(lines) + "\n"


def camel(name):
    head, *tail = name.split("-")
    return head + "".join(part.capitalize() for part in tail)


def argb(value):
    digits = value.lstrip("#").upper()
    alpha = digits[6:8] or "FF"
    literal = f"0x{alpha}{digits[:6]}"
    return f"{literal}.toInt()" if alpha >= "80" else literal


def kotlin(tokens):
    colors = tokens["color"]["tokens"]
    themes = [theme["id"] for theme in tokens["color"]["themes"]]
    lines = [
        FIRST_LINE,
        "package com.byway.ui",
        "",
        "/** 지금 테마의 색. 화면은 색 값을 직접 쓰지 않고 이것만 읽는다. */",
        "data class Palette(",
    ]
    lines += [f"    val {camel(entry['name'])}: Int," for entry in colors]
    lines.append(")")
    for theme in themes:
        lines += ["", f"val {theme.capitalize()}Palette = Palette("]
        lines += [f"    {camel(entry['name'])} = {argb(entry['value'][theme])}," for entry in colors]
        lines.append(")")
    lines += ["", "/** 글자 크기(sp)와 길이(dp). */", "object Tokens {"]
    for family in LENGTH_FAMILIES:
        for entry in tokens.get(family, {}).get("tokens", []):
            number = re.sub(r"[^0-9.]", "", entry["value"])
            kind = "" if "." not in number else "f"
            lines.append(f"    const val {entry['name'].upper().replace('-', '_')} = {number}{kind}")
    for group in tokens["type"]["groups"]:
        for style in group["styles"]:
            name = style["name"].upper().replace("-", "_")
            lines.append(f"    const val TEXT_{name} = {style['fontSize'].rstrip('px')}f")
    lines.append("}")
    return "\n".join(lines) + "\n"


def main():
    tokens = json.loads(SOURCE.read_text(encoding="utf-8"))
    CSS_OUT.parent.mkdir(parents=True, exist_ok=True)
    CSS_OUT.write_text(css(tokens), encoding="utf-8")
    KOTLIN_OUT.write_text(kotlin(tokens), encoding="utf-8")
    print(CSS_OUT.relative_to(ROOT), KOTLIN_OUT.relative_to(ROOT))


if __name__ == "__main__":
    main()
