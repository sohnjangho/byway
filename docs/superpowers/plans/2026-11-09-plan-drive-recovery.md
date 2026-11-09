# 주행 보완 과제(P1~P9) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 주행 중 실패 사례 P1~P9를 고친다. 운전자 경로를 덮어쓰지 않고, 이탈·복귀가 한 번에 끝나고, 꺼졌다 켜도 코스가 이어진다.

**Architecture:** 코스 엔진의 `Run`을 상태(접근 중, 코스 위, 묻는 중, 들르는 중, 복귀 중, 멈춤)로 다시 짠다. 시간은 엔진이 모르므로 위치 사건 횟수로 센다. 내비·차량 호출은 `platform/`에만 두고, 판단은 SDK 타입이 없는 순수 Kotlin으로 빼서 JVM 시험으로 고정한다.

**Tech Stack:** Kotlin 1.9.25, JUnit 4, Pleos Connect SDK(NaviHelper, Vehicle, FusedLocation, TextToSpeech).

**Spec:** `docs/보완과제_실패사례_v01.md`

## Global Constraints

- `ai.pleos.*`, `android.car.*` import는 `app/.../platform/`에만. SDK 이름은 공개 문서와 컴파일 결과로만 확인한다(AAR 분석 금지).
- 새 사건·명령은 contract 레인(`core/.../drive/`)을 먼저 커밋한다. 한 브랜치(`feature/drive-recovery`)에서 레인 순서대로 커밋한다: contract → engine → feature-ambience → platform·service → ui. **[판단]** 개발 세션이 하나라 레인별 브랜치 대신 커밋 순서로 지킨다.
- `DriveGate.allows`의 `when`에 `else`를 두지 않는다.
- 주행 화면에는 자동 애니메이션을 넣지 않는다. 새 버튼은 터치 76dp 이상.
- `unregisterGearSelection`을 부르지 않는다. 에뮬레이터는 `tools/emulator-lock.sh`로 잠근다.
- 시험: `./gradlew test`. 엔진은 `CourseEngineTest`에 실패 시험을 먼저 쓴다.

## 정한 것 **[판단]** — 문서에 없거나 문서와 다르게 정한 것

1. **구간 묶음은 미리 자르지 않는다.** 엔진이 "앞에 남은 지점" 열에서 경유지 2개와 목적지 1개를 그때그때 뽑는다. P3의 "묶음 사이 지점 공유", P4의 "복귀 지점부터 한 번에", P7의 "지점 추가"가 같은 함수 하나로 풀린다. `planLegs`는 화면이 쓰므로 남긴다.
2. **음성 수락(STT)은 넣지 않는다.** 이 브랜치에 STT가 없다(문서의 "기존 STT"는 없는 코드다). 주행 화면에 버튼 하나를 띄워 답을 받는다. 답이 없으면 "아니요"다.
3. **"정해진 시간"은 위치 사건 횟수다.** 엔진에 시계가 없다. 위치는 1초마다 읽고 움직일 때만 오므로 15회는 달리는 중 약 15초다.
4. **출발 때 "접근 중" 상태를 둔다(핵심 ④의 일부).** P5가 들어가면 집에서 출발하는 운전자에게 "잠깐 들르시나요?"를 묻게 되어, 접근 상태 없이는 P5를 켤 수 없다. 첫 위치가 코스 출발점 근처면 그대로, 코스 중간이면 그 자리부터, 멀면 출발점을 첫 경유지로 다시 올린다.
5. **연출 복원은 platform이 맡는다.** 문서는 기능이 시작 값을 받아 `SetCabin(원래 값)`을 내게 했지만, 시작 값은 비동기로 읽혀 첫 연출보다 늦게 올 수 있다. `PleosCabin`이 항목마다 처음 쓰기 직전에 읽어 두고, 기능은 `RestoreCabin` 명령만 낸다. 되돌릴 때 지금 값이 우리가 마지막에 쓴 값과 다르면(운전자가 바꿨으면) 그 항목은 건드리지 않는다.
6. **P7의 추가 지점은 그 구간에서 직선과 가장 멀리 떨어진 점 하나다.** 허용 오차 절반 재계산의 첫 분할점과 같다. 50m보다 가까우면 넣지 않는다.
7. **P8 완주는 종점이 탐색 범위(3km) 안에 들어온 뒤에만 본다.** 출발점과 종점이 같은 순환 코스에서 출발하자마자 완주로 판정되지 않게 한다.
8. **P9 "위치 확인 중"은 위치 시각(`timestampMillis`)이 10초 넘게 멈췄을 때만 띄운다.** 좌표가 같은 것으로 판단하면 신호 대기마다 뜬다. 끊김 뒤 복귀는 연속한 두 위치가 500m 넘게 떨어졌을 때 탐색 범위를 그 거리의 2배만큼 넓혀 다시 맞춘다.
9. **수동 종료는 서비스가 `CourseStopped` 사건을 낸 뒤 2초 있다가 멈춘다.** 연출을 되돌리는 호출이 나갈 시간이다. 그 사이 새 코스를 시작하면 예약한 종료를 취소한다(완주 뒤 6초 종료도 같이 고친다).
10. **스냅숏은 코스 id와 마지막 확인 지점만 담는다.** 구간 교체·이탈·복귀·멈춤 때만 바뀌어 저장 횟수가 적다. 이어갈 때 운전자가 수락했으므로 멈춤 상태는 되살리지 않는다.

구현하며 더 정한 것:

11. **복귀 지점은 핵심 지점이 아니라 차 옆 자리에서 500m 앞의 코스 위 자리다.** 문서대로 "500m 넘게 앞의 가장 가까운 핵심 지점"으로 짰더니 실제 코스 15개에서 268회 중 175회가 3km 넘게(최대 15.2km) 건너뛰었다. 실제 코스는 핵심 지점이 5~15km 간격이다. 바꾼 뒤에는 3회, 최대 3.5km다. 그 자리 500m 안에 핵심 지점이 있으면 그 지점을 쓴다. 차 옆 자리는 탐색 범위(3km) 안에서만 찾는다. `rejoinStops`는 없앴다.
12. **누구의 경로인지는 시각과 좌표로 가린다.** 에뮬레이터 실측에서 `onDestinationChanged`는 오지 않았고, 우리 요청에도 `onRouteCancelled`가 먼저 왔다. 우리 요청의 시작을 기다리는 동안(30초 한도) 온 취소는 우리 것이고, 그 밖의 취소는 운전자가 내비를 만진 것이다. 시작 뒤 `getDestinationInfo()`로 받은 좌표가 우리가 보낸 것과 200m 넘게 다르면 운전자의 경로다. 운전자가 경유지를 더하면 `waypointChanged(ADD)`가 온다(우리 요청에는 오지 않는다).
13. **달리다 만 코스는 홈의 카드가 아니라 화면 하나로 묻는다(`Screen.Resume`).** 세 가지 창 크기의 홈에 카드 자리를 따로 내지 않고, 정차했을 때 메뉴보다 먼저 보인다. 주행 규제의 문(`screenFor`)을 그대로 지난다.
14. **"위치 확인 중"은 넣었지만 확인하지 못했다.** 에뮬레이터의 위치 시각은 서 있어도 매초 바뀐다. 실차 터널에서 시각이 멈추는지 봐야 한다.

## Review Focus

1. 접근 중에 코스 끝이 출발점 옆인 왕복 코스 → 끝 지점으로 건너뛰어 바로 완주하지 않는다. (Task 3 시험)
2. 지점 간격이 1km보다 촘촘한 코스 → 구간 교체가 연달아 일어나지 않는다. (Task 2 시험)
3. 운전자가 우리 경로를 받은 직후 목적지를 바꿈 → 직전에 보낸 요청의 이벤트와 헷갈리지 않는다. (Task 9 시험, 에뮬레이터 실측)
4. 멈춤 중 위치가 오지 않은 채 "이어가기" → 마지막 위치가 없어도 남은 구간을 올린다. (Task 6 시험)
5. 연출 항목을 운전자가 직접 바꾼 뒤 완주 → 그 항목은 되돌리지 않는다. (Task 8 시험)

---

### Task 1: 계약 — 새 사건과 명령

**Files:** `core/.../drive/Drive.kt`, `DriveGate.kt`, `core/src/test/.../drive/DriveHostTest.kt`

**Produces:**
- `DriveEvent.CourseStarted(course, resumeFrom: Int = 0)`, `RouteTakenOver`, `RouteCancelled`, `DestinationArrived`, `Answered(yes: Boolean)`, `CoursePaused`, `CourseStopped` (뒤 여섯은 `data object`, `Answered`는 `data class`)
- `enum class Prompt { DETOUR, RESUME }`
- `DriveCommand.Say(text, alert: Boolean = false)`, `Ask(prompt: Prompt?)`(null은 질문을 거둔다), `RestoreCabin`(`data object`)
- `DriveGate.allows`: `Ask`, `RestoreCabin`은 주행 중에도 허용

- [ ] 시험: 주행 중 `Ask`·`RestoreCabin`이 통과한다. `Say("a") == Say("a", alert = false)`.
- [ ] 구현 → 커밋 `feat(contract): …`

### Task 2: 엔진 — 남은 지점에서 경로를 뽑고 1km 전에 잇는다 (P3)

**Files:** `core/.../engine/CourseEngine.kt`, `DriveTuning.kt`, `Messages.kt`, `CourseEngineTest.kt`

**Produces:** `DriveTuning.legSwapAheadMeters = 1_000.0`. `Run.routeAhead(first: Stop? = null): FollowRoute`.

- [ ] 시험: 목적지 1km 전 위치에서 `FollowRoute`가 나오고 `waypoints[0] == 이전 destination`. 지점이 400m 간격이어도 한 지점을 지나기 전에는 다시 교체하지 않는다. 마지막 지점이 목적지면 교체하지 않는다. 경유지는 2개를 넘지 않는다.
- [ ] 기존 `passingTheLegDestinationPutsTheNextLeg`는 P3 기대값으로 바꾼다.

### Task 3: 엔진 — 접근, 중간 합류, 이어가기 (P4 출발, P2 엔진)

**Produces:** `CourseEngine.snapshot(): RunSnapshot?`, `data class RunSnapshot(courseId: String, progress: Int)`.

- [ ] 시험: 첫 위치가 출발점 근처 → 추가 경로 없음. 코스 중간(탐색 범위 밖) → 그 자리부터 한 번 올리고 "코스 중간에서 시작합니다." 코스에서 멀리 → 출발점이 첫 경유지인 경로 한 번, 이탈 안내 없음. 왕복 코스 출발점 → 끝으로 건너뛰지 않음. `resumeFrom = 45` → 다음 위치에서 45 뒤의 지점만 올림, "…코스를 이어갑니다." 스냅숏 → 새 엔진 → 같은 구간.

### Task 4: 엔진 — 먼저 묻는다, 서 있으면 세지 않는다 (P5)

**Produces:** `DriveTuning.askWaitFixes = 15`.

- [ ] 시험: 세 번째 이탈에서 경로 없이 질문과 `Ask(DETOUR)`. 수락 → 밖 위치가 이어져도 경로 없음, 돌아오면 "코스로 돌아왔습니다." 한 번. 거절 또는 15회 무응답 → 복귀 경로 한 번. 묻는 중 스스로 돌아옴 → `Ask(null)`만. `parked = true`에서 밖 위치 10회 → 명령 없음.

### Task 5: 엔진 — 복귀는 한 번에, 앞쪽 지점으로, 완충을 두고 (P4)

**Produces:** `DriveTuning.rejoinAheadMeters = 500.0`, `rejoinMeters = 300.0`, `rejoinFixes = 2`. (`rejoinStops`는 정한 것 11로 없앴다.)

- [ ] 시험: 복귀 경로는 1회이고 `waypoints[0].pathIndex > progress`. 목표는 다음 지점 2개를 넘지 않는다. 500m 안의 지점은 건너뛴다. 복귀 뒤 추가 경로 없음. 300m 밖에서 경계를 오가도 복귀 선언이 없다. 구간 하나를 통째로 지나 복귀 → "○○ 구간을 건너뛰었습니다." 한 번. 목표 지점을 지나쳐 허용 오차 안이면 복귀로 본다.

### Task 6: 엔진 — 운전자 경로에 손대지 않는다 (P1)

- [ ] 시험: `RouteTakenOver` 뒤 위치가 코스 밖으로 가도 `FollowRoute` 없음, `CoursePaused` 발행, `Ask(RESUME)`. `RouteCancelled`도 같다. 멈춤 중 `DestinationArrived` → "코스를 이어갈까요?". `Answered(true)` → 남은 구간 1회, 구간 다시 발행. 위치를 한 번도 받지 못한 채 이어가기 → 마지막 확인 지점부터 올린다.

### Task 7: 엔진 — 같은 구간의 반복 이탈, 종점 반경, 위치 끊김 (P7·P8·P9)

**Produces:** `DriveTuning.finishRadiusMeters = 300.0`, `gapJumpMeters = 500.0`.

- [ ] 시험: 같은 구간 두 번째 이탈 → 묻지 않고 추가 지점이 든 경로. 세 번째 → "내비 경로로 진행합니다." 한 번, 그 구간은 이탈 없음, 다음 지점을 지나면 다시 본다. 종점 200m 앞에서 `parked` → 완주 한 번. 출발 직후 순환 코스에서 `parked` → 완주 아님. 4.5km 건너뛴 위치 → 이탈이 아니라 그 자리로 맞춘다.

### Task 8: 연출을 되돌린다 (P6)

**Files:** `feature/ambience/.../AmbienceFeature.kt`, 시험, `app/.../platform/CabinMemory.kt`(순수), `app/src/test/.../platform/CabinMemoryTest.kt`, `platform/PleosCabin.kt`

**Produces:** `class CabinMemory { fun wrote(item: CabinItem, before: Int, value: Int); fun restores(item: CabinItem, now: Int): Int?; fun clear() }`, `enum class CabinItem { WINDOW, FAN, LIGHT }`, `PleosCabin.restore()`.

- [ ] 시험: `CourseStarted → SectionEntered → CourseFinished`의 마지막 명령이 `RestoreCabin`. 연출을 낸 적이 없으면 내지 않는다. `CoursePaused`·`CourseStopped`도 같다. `CabinMemory`: 처음 값만 기억, 지금 값이 마지막에 쓴 값과 다르면 `null`.

### Task 9: platform·service — 내비 이벤트, 저장과 복원, 종료, 음성

**Files:** `platform/PleosNavi.kt`, `platform/RouteLedger.kt`(순수), `platform/PleosLocation.kt`, `platform/PleosVoice.kt`, `service/DriveService.kt`, `service/DriveBoard.kt`, `service/MyDrives.kt`, `service/VoiceTiming.kt`(순수), 시험 `RouteLedgerTest.kt`, `VoiceTimingTest.kt`

**Produces:** `RouteLedger(nearMeters = 200.0, keep = 3, startWaitMs = 30_000, arriveGraceMs = 10_000)`: `requested(point, nowMs)`, `started()`, `failed()`, `arrived(nowMs)`, `cancelIsOurs(nowMs): Boolean`, `seen(point): Boolean`, `oursIsActive: Boolean`. `speechDelayMs(sinceRouteMs: Long): Long`(2,500ms 안이면 남은 시간). `isLocationStale(fixAgeMs: Long, parked: Boolean): Boolean`. `MyDrives.snapshot`, `saveSnapshot`, `clearSnapshot`. `DriveService.start(context, courseId, resumeFrom = 0)`, `answer(context, yes)`, `discard(context)`. `DriveState.prompt: Prompt?`, `stale: Boolean`.

- [ ] 에뮬레이터 실측 먼저: 우리 요청과 내비 화면 조작에서 어떤 이벤트가 오는지 로그로 확인하고 PR 보고서에 적는다.
- [ ] 시험 → 구현 → 커밋

### Task 10: ui — 주행 화면 답 버튼, 홈의 이어가기 카드

**Files:** `ui/DriveScreen.kt`, `ui/DriveReadout.kt`(문구), `ui/HomeScreen.kt`, `ui/MainActivity.kt`, 시험 `DriveReadoutTest.kt`

**Produces:** `promptButton(prompt: Prompt?): String?`("잠깐 들러요", "코스 이어가기"), `driveLine(line, stale)`, `resumeLine(courseName, sectionNo): String`, `sectionNoAt(sections, pathIndex)`, `Screen.Resume`, `screenFor(state, nav, resumable = false)`, `resumeScreen(…)`.

- [ ] 시험 → 구현 → 에뮬레이터 확인 → 커밋

### Task 11: 문서와 보고

- [ ] `docs/제작가이드.md`, `docs/보완과제_실패사례_v01.md`(상태 표기), PR 보고서, 기획 세션에 경로 알림
