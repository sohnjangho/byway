# 에움길

작업 전에 `docs/제작가이드.md`의 2·5·7·12장을 읽는다. 구조를 바꾸려면 가이드부터 고친다.

- 자기 레인의 소유 경로만 고친다. 병합 전 `tools/check-lane.sh <레인>`을 돌린다.
- 새 사건·명령은 `contract` 레인이 먼저 병합한다.
- `ai.pleos.*` import는 `app/.../platform/`에만 둔다.
- 에뮬레이터는 `tools/emulator-lock.sh`로 잠그고 쓴다.
