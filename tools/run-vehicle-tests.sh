#!/usr/bin/env bash
# Virtual Console은 브라우저 세션 신호가 없으면 시뮬레이터를 끈다.
# 페이지가 보내는 것과 같은 신호를 유지하며 차량 제어 계측 테스트를 실행한다.
# 사용: tools/run-vehicle-tests.sh [Park|Drive] [속도 km/h]
set -u
GEAR="${1:-Park}"; SPEED="${2:-0}"; VC=http://127.0.0.1:8088
cd "$(dirname "$0")/.."
adb forward tcp:8088 tcp:8088 >/dev/null
( while :; do curl -s -m 2 -o /dev/null -X POST "$VC/browser/session?client=roadcraft-test"; sleep 1; done ) &
HB=$!
# 구간 알림 시험용: 반경(37.5000, 127.0000) 안팎을 4초 간격으로 오간다.
( while :; do adb emu geo fix 127.0200 37.5200 >/dev/null 2>&1; sleep 4; adb emu geo fix 127.0000 37.5000 >/dev/null 2>&1; sleep 4; done ) &
GEO=$!
trap 'curl -s -m 5 -o /dev/null -X PUT "$VC/vehicle/controls?speed_kph=0"; curl -s -m 5 -o /dev/null -X PUT "$VC/vehicle/gear?gear=Park"; kill $HB $GEO' EXIT
sleep 3
curl -s -m 5 -o /dev/null -X PUT "$VC/vehicle/gear?gear=$GEAR"
curl -s -m 5 -o /dev/null -X PUT "$VC/vehicle/controls?speed_kph=$SPEED"
sleep 2
curl -s -m 5 "$VC/vehicle/state" | python3 -I -c "import json,sys;d=json.load(sys.stdin);print('state gear',d['gear']['actual'],'speed',d['controls']['speed_kph'])"
./gradlew :app:connectedDebugAndroidTest --console=plain
