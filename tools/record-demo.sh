#!/usr/bin/env bash
# 시연 영상을 녹화한다. 사용: tools/record-demo.sh <출력.mp4>
# 앱 설치와 위치 권한이 먼저 되어 있어야 한다. 소리는 녹화되지 않아 음성 문구는 앱 화면에 표시된다.
set -u
out="${1:?출력 파일이 필요합니다}"
cd "$(dirname "$0")/.."
VC=http://127.0.0.1:8088
APP_ICON="1520 1370"; START_BUTTON="1285 918"

adb forward tcp:8088 tcp:8088 >/dev/null
( while :; do curl -s -m 2 -o /dev/null -X POST "$VC/browser/session?client=byway-demo"; sleep 1; done ) &
heartbeat=$!
trap 'kill $heartbeat 2>/dev/null' EXIT

adb emu geo fix 127.1112 37.3937 >/dev/null
adb shell am force-stop com.byway
adb shell am start -n com.byway/.ui.MainActivity >/dev/null
sleep 3

adb shell screenrecord --size 1280x720 --bit-rate 8000000 --time-limit 170 /sdcard/byway-demo.mp4 &
recorder=$!
sleep 6
adb shell input tap $START_BUTTON      # 코스를 내비에 입힌다
sleep 14                               # 내장 내비가 안내를 시작하는 장면
adb shell input tap $APP_ICON
sleep 3
tools/demo-drive.py 60 1.2 --stray &
drive=$!
# 구간을 바꿀 때마다 내비가 앞으로 나온다. 몇 초 보여 준 뒤 앱으로 돌아온다.
while kill -0 $drive 2>/dev/null; do
  sleep 8
  adb shell input tap $APP_ICON
done
sleep 8
adb shell pkill -INT screenrecord
wait $recorder
sleep 2
adb pull /sdcard/byway-demo.mp4 "$out"
adb shell rm /sdcard/byway-demo.mp4
