#!/usr/bin/env bash
# 사용: tools/check-lane.sh <레인> [기준 브랜치]. 레인 소유 경로 밖의 변경과 계층 위반을 찾는다.
set -u
lane="${1:?레인 이름이 필요합니다}"
base="${2:-main}"
app=app/src/main/java/com/byway
core=core/src/main/kotlin/com/byway/core
coretest=core/src/test/kotlin/com/byway/core

case "$lane" in
  contract) allowed="^($core/(drive|course)/|$coretest/(drive|course)/|settings\.gradle\.kts|docs/|CLAUDE\.md)" ;;
  engine)   allowed="^($core/(engine|geo)/|$coretest/(engine|geo)/)" ;;
  platform) allowed="^($app/(platform|service)/|app/src/main/AndroidManifest\.xml|app/src/androidTest/|app/build\.gradle\.kts)" ;;
  ui)       allowed="^($app/ui/|app/src/test/java/com/byway/ui/|app/src/main/res/)" ;;
  tools)    allowed="^(tools/|app/src/main/assets/courses/|app/src/main/assets/covers/)" ;;
  server)   allowed="^(server/|settings\.gradle\.kts|docs/)" ;;
  feature-*) name="${lane#feature-}"; allowed="^(feature/$name/|$app/ui/$name/|$app/FeatureRegistry\.kt)" ;;
  *) echo "모르는 레인: $lane"; exit 2 ;;
esac

status=0
outside=$(git diff --name-only "$base"...HEAD | grep -Ev "$allowed" || true)
if [ -n "$outside" ]; then
  echo "레인 밖 변경:"; echo "$outside"; status=1
fi
leaks=$(grep -rl --include='*.kt' 'ai\.pleos\.' core feature "$app" 2>/dev/null | grep -v "^$app/platform/" || true)
if [ -n "$leaks" ]; then
  echo "platform 밖 SDK import:"; echo "$leaks"; status=1
fi
[ "$status" -eq 0 ] && echo "레인 확인 통과: $lane"
exit "$status"
