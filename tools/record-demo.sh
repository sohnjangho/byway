#!/usr/bin/env bash
# 시연 영상과 화면 캡처를 만든다. 사용: tools/record-demo.sh <출력.mp4> <캡처 폴더>
# 앱 설치와 위치 권한이 먼저 되어 있어야 한다. 소리는 녹화되지 않아 음성 문구는 앱 화면에 표시된다.
set -u
out="${1:?출력 파일이 필요합니다}"
shots="${2:?캡처 폴더가 필요합니다}"
cd "$(dirname "$0")/.."
VC=http://127.0.0.1:8088
COURSE_CARD="1400 780"; START_BUTTON="1290 1184"

shot() { adb exec-out screencap -p > "$shots/$1.png"; }
gear() { curl -s -m 5 -o /dev/null -X PUT "$VC/vehicle/gear?gear=$1"; }
speed() { curl -s -m 5 -o /dev/null -X PUT "$VC/vehicle/controls?speed_kph=$1"; }
show_app() { adb shell am start -n com.byway/.ui.MainActivity >/dev/null 2>&1; }

mkdir -p "$shots"
adb forward tcp:8088 tcp:8088 >/dev/null
( while :; do curl -s -m 2 -o /dev/null -X POST "$VC/browser/session?client=byway-demo"; sleep 1; done ) &
heartbeat=$!
trap 'kill $heartbeat 2>/dev/null; speed 0; gear Park' EXIT
sleep 3

adb emu geo fix 127.1112 37.3937 >/dev/null
gear Drive
adb shell am force-stop com.byway
show_app
sleep 3

adb shell screenrecord --size 1280x720 --bit-rate 8000000 --time-limit 170 /sdcard/byway-demo.mp4 &
recorder=$!
sleep 5
shot 5_주행중대기                       # 주행 중에는 코스를 고를 수 없다
gear Park
sleep 4
shot 1_홈
adb shell input swipe 1700 1150 1700 500 400
sleep 2
adb shell input tap $COURSE_CARD
sleep 5
shot 2_코스미리보기
adb shell input tap $START_BUTTON      # 코스를 내비에 입힌다
sleep 13                               # 내장 내비가 안내를 시작하는 장면
show_app
gear Drive
speed 60
sleep 2
tools/demo-drive.py 60 1.2 --stray &
drive=$!
# 구간을 바꿀 때마다 내비가 앞으로 나온다. 몇 초 보여 준 뒤 앱으로 돌아온다.
turn=0
while kill -0 $drive 2>/dev/null; do
  sleep 8
  show_app
  turn=$((turn + 1))
  if [ "$turn" = 6 ]; then sleep 1; shot 3_주행; fi
done
sleep 7
speed 0
gear Park
sleep 7
show_app
sleep 2
shot 4_정차요약
adb shell pkill -INT screenrecord
wait $recorder
sleep 2
adb pull /sdcard/byway-demo.mp4 "$out"
adb shell rm /sdcard/byway-demo.mp4
