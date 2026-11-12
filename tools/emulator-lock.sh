#!/usr/bin/env bash
# 사용: tools/emulator-lock.sh <명령...>. 에뮬레이터를 한 번에 한 작업만 쓰게 한다.
set -u
lock="${TMPDIR:-/tmp}/byway-emulator.lock"
until mkdir "$lock" 2>/dev/null; do
  echo "에뮬레이터 대기 중: $(cat "$lock/owner" 2>/dev/null)"
  sleep 5
done
trap 'rm -rf "$lock"' EXIT
echo "$$ $(pwd)" > "$lock/owner"
"$@"
