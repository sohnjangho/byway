#!/usr/bin/env python3
"""코스를 따라 에뮬레이터 위치를 흘려 보낸다. 사용: tools/demo-drive.py [구간 수] [간격초] [--stray]"""
import json
import subprocess
import sys
import time
from pathlib import Path

args = [a for a in sys.argv[1:] if not a.startswith("--")]
steps = int(args[0]) if args else 120
delay = float(args[1]) if len(args) > 1 else 1.2
stray = "--stray" in sys.argv

course = json.loads((Path(__file__).parent.parent / "app/src/main/assets/courses/paldang-sunset.json").read_text())
path = course["path"]


def fix(lat, lon):
    subprocess.run(["adb", "emu", "geo", "fix", str(lon), str(lat)], capture_output=True)


stray_at = int(steps * 0.3)
for step in range(steps + 1):
    lat, lon = path[min(step * (len(path) - 1) // steps, len(path) - 1)]
    if stray and step == stray_at:
        print("코스 이탈 시작", flush=True)
        for away in range(5):
            fix(lat + 0.02 + away * 0.002, lon + 0.02)
            time.sleep(delay + 0.3)
        print("코스 복귀", flush=True)
    fix(lat, lon)
    time.sleep(delay)
fix(*path[-1])
print("끝", flush=True)
