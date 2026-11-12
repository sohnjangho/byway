#!/usr/bin/env bash
# 사용: tools/run-server.sh [demo]
# gpu-wsl 의 테일넷 주소로 공유·파티 서버를 띄운다(공유 설계 6-1). 떠 있으면 내리고 다시 띄운다.
# demo 를 주면 새 파티에 시연용 가상 멤버 2명이 들어온다. WSL 창이 닫히면 서버도 꺼진다.
set -eu
cd "$(dirname "$0")/.."
./gradlew -q :server:installDist
pkill -f "com.byway.server.MainKt" || true
# DB 와 로그는 저장소 밖에 둔다. 저장소 동기화(rsync --delete)나 git clean 이 지우지 못하게 한다.
DATA="${BYWAY_DATA:-$HOME/.byway/server}"
mkdir -p "$DATA"
export BYWAY_DB="$DATA/byway.db"
export BYWAY_HOST="${BYWAY_HOST:-$(tailscale ip -4 | head -1)}"
export BYWAY_PORT="${BYWAY_PORT:-8787}"
export BYWAY_DEMO_MEMBERS="$([ "${1:-}" = demo ] && echo 2 || echo 0)"
nohup server/build/install/server/bin/server > "$DATA/server.log" 2>&1 &
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if curl -s -m 2 -o /dev/null -X POST "http://$BYWAY_HOST:$BYWAY_PORT/v1/devices"; then
    echo "서버: http://$BYWAY_HOST:$BYWAY_PORT (가상 멤버 $BYWAY_DEMO_MEMBERS명, 자료 $DATA)"
    exit 0
  fi
  sleep 1
done
echo "서버가 뜨지 않았습니다. $DATA/server.log 를 보세요"
exit 1
