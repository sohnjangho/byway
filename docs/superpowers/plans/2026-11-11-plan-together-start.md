# 함께 달리기 다듬기 · 출발 화면 고르기 구현 계획

**Spec:** `docs/superpowers/specs/2026-11-11-together-start-design.md`

## Global Constraints
- 빌드·시험은 gpu-wsl `./gradlew --no-watch-fs`. Pleos AAR 분석 금지. `ai.pleos.*`는 platform/ 에만.
- 문구: "함께 달리기", "함께 달릴 친구", 출발 고르기 칸 "3분할 · 차량 정보 + 내비 + 에움길", "3분할 · 차량 정보 + 에움길 + 내비", "2분할 · 차량 정보 + 내비", 출발 버튼 "이 화면으로 출발", 안내 "오른쪽 끝 ⋮을 왼쪽으로 끌어 셋으로 나누면 출발합니다", "⋮을 오른쪽 끝까지 끌어 2분할로 돌아가면 출발합니다".

### Task 1: 코스 비우기와 다시 출발 없애기
- core `PartyRules.leaderEndsRun(view: PartyView?): Boolean` (리더이고 코스가 있을 때 true), 시험 `PartyRulesTest`.
- server `DELETE /v1/parties/{code}/course` + `Store.clearCourse(code)`, 시험 `leaderClearsCourse`, `onlyLeaderClearsCourse`.
- app `ShareClient.clearCourse(code)`, `DriveService.stopParty()`에서 `leaderEndsRun`이면 보냄. `partyActions`의 restart 와 `RESTART_BUTTON`, `canRestart` 제거, 시험 갱신.

### Task 2: 말 바꾸기
- 시험 먼저: PartyCopyTest·ShareCopyTest·server PartyRoutesTest 의 문구 기대값을 새 말로. `withParticle(name)` 같은 받침 처리 시험(서버 기본 이름).
- app·server 문구 31곳.

### Task 3: 구획
- `TogetherScreen` 밖·안 화면을 `section(title, body)` 칸으로 묶는다. 에뮬레이터 화면으로 확인.

### Task 4: 출발 화면 고르기
- `DriveLayout` 세 값: `SPLIT`("split", 차량+내비+에움길), `SPLIT_BYWAY`("split_byway", 차량+에움길+내비), `NAVI`("navi", 2분할). `needsThird`.
- `Nav.splitGuide`는 그대로 두고 `Nav.startLayout: DriveLayout?`(고른 뒤 기다리는 배치)를 더한다.
- `takeGuide(nav, window, parked, stop)`: startLayout 이 정해져 있고 창이 맞으면(needsThird == (window==THIRD)) 꺼낸다.
- 시험: `DriveLayoutTest` — 세 값 문구·저장값, 옛 저장값 "split"/"navi" 그대로 읽힘, takeGuide 세 경우(3분할 고르고 2/3 → 기다림, 1/3 되면 출발, 2분할 고르고 1/3 → 기다림, 넓어지면 출발), 고르기 전에는 출발 안 함.
- `MainActivity.begin`은 늘 고르기 화면, `launch(request, layout)`: SPLIT_BYWAY 면 내비 띄운 뒤 1.5초 뒤 에움길 앞으로(REORDER_TO_FRONT).
- `SplitGuideScreen` → 고르기 화면(그림 세 개). 설정 칩 세 개.
- 에뮬레이터: 세 배치 모두 실제로 출발해 칸 순서 확인.

### Task 5: 확인과 촬영
- 시연 모임 새로 만들기, 보기 페이지 전체 다시 찍기(PM 요청), PR 보고서.
