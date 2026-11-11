# 함께달리기 메뉴 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 파티를 왼쪽 메뉴 "함께달리기" 탭으로 옮기고, 순서를 "사람 먼저 모으고 리더가 나중에 코스를 고른다"로 바꾼다. 파티원은 리더가 고른 코스를 승낙(출발)하거나 거부(나감)한다.

**Architecture:**
- **서버(Ktor + SQLite):** 파티에 리더, 코스 제안 번호, 멤버별 승낙·들어와 있음·막힘을 더한다. 열은 그 자리에서 늘린다.
- **core:** 앱과 서버가 함께 쓰는 자료형과 "질문을 띄울지" 순수 함수를 둔다.
- **앱:** 기존 `partyScreen`을 탭 화면 `togetherScreen`으로 바꾼다. 정차 중 파티 상태를 가끔 받아 승낙·거부 질문을 정차 화면으로 띄운다.

**Tech Stack:** Kotlin 1.9.25, Android View(Compose 아님), Ktor server + testApplication, SQLite(xerial jdbc), kotlinx.serialization, OkHttp, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-11-together-drive-design.md`(54dcabb). 바탕은 `docs/superpowers/specs/2026-11-10-share-party-design.md` 6-5다.

## Global Constraints

- 조작은 정차(P) 중에만 한다. 코스가 달리는 동안(정차 포함)은 `screenFor`가 주행 화면만 보인다. 이 규칙은 바꾸지 않는다(spec 6-10.1).
- 주행 화면은 바꾸지 않는다(버튼 1개 + 코스 끝내기, 자동 움직임 없음).
- 위치는 "동의하고 출발", "동의하고 함께 달리기", "동의하고 다시 출발" 버튼을 누른 뒤에만 나간다. 동의 문구는 `LOCATION_CONSENT`를 그대로 쓴다.
- 닉네임과 아바타만 쓴다. 실명·연락처는 쓰지 않는다(블라인드 심사).
- `ai.pleos.*`와 `android.car.*`는 `platform/` 안에서만 쓴다. Pleos AAR·APK를 풀거나 분석하지 않는다(라이선스 3.4).
- 메뉴 이름은 "함께달리기"(붙여 씀)이고 자리는 내 코스 다음이다. 화면 안 말은 "파티", "파티원", "리더"다.
- 문구(그대로 쓴다):
  - "파티원 초대"
  - "다른 차에서 함께달리기 → 코드로 참여에 이 코드를 넣으면 들어옵니다"
  - "코스 고르기"
  - "동의하고 출발"
  - "동의하고 함께 달리기"
  - "거부하고 파티 나가기"
  - "동의하고 다시 출발"
  - "리더 넘기기"
  - "내보내기"
  - "응답 기다림"
  - "함께 달림"
  - "리더"
  - "<닉네임>님이 코스를 골랐습니다"
  - "이 파티에 들어갈 수 없습니다"
  - "열려 있음 · 들어가기"
  - "다시 모이기"
- 빌드는 gpu-wsl `~/projects/byway`에서 `./gradlew --no-watch-fs ...`로 한다.
- 커밋:
  - 작성자는 `byway-dev <dev@byway.invalid>`다.
  - 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`와 `Claude-Session: https://claude.ai/code/session_01CHFCWCoxEmwdrDSYGzruEU`를 붙인다.
  - 푸시와 PR은 하지 않는다.
- 에뮬레이터:
  - `tools/emulator-lock.sh`로 잠그고 쓴다.
  - 비행기 모드를 켜지 않는다.
  - `unregisterGearSelection`을 부르지 않는다.
  - AVD를 지우지 않는다.

## Review Focus

1. 리더가 다시 고른 뒤, 옛 제안 번호로 온 승낙은 409로 막는다. 앱은 새 제안을 다시 묻는다. → Task 3 `staleAnswerIsRejected`
2. 리더가 나가면 들어온 순서로 다음 사람이 리더가 된다. 그 사람이 아직 답하지 않았으면 질문을 받는다. → Task 3 `leaderLeavingPassesToEarliestPresent`, Task 1 `aNewLeaderWhoHasNotAnsweredIsStillAsked`
3. 위치 공유를 켜고 꺼도 멤버 순서(들어온 순서)가 바뀌지 않는다. 지금 `INSERT OR REPLACE`는 rowid를 바꾼다. → Task 2 `memberOrderSurvivesUpdates`
4. 내보낸 사람은 다시 참여할 때 403을 받는다. 그 사람의 앱은 404를 받아 파티를 비운다. → Task 3 `kickedMemberCannotRejoin`
5. 코스 없는 파티를 시연 서버(가상 멤버 2명)로 열어도 보기가 깨지지 않는다. 가상 멤버는 목록에 있고 핀은 없다. → Task 3 `demoMembersWaitForACourse`

---

### Task 1: core 자료형과 질문 규칙

**Files:**
- Modify: `core/src/main/kotlin/com/byway/core/share/ShareApi.kt`
- Create: `core/src/main/kotlin/com/byway/core/share/PartyRules.kt`
- Test: `core/src/test/kotlin/com/byway/core/share/PartyRulesTest.kt`
- Modify(컴파일만 맞춤):
  - `server/src/main/kotlin/com/byway/server/PartyRoutes.kt`
  - `app/src/main/java/com/byway/ui/party/PartyScreen.kt`
  - nullable `courseId`를 쓰는 곳 전부

**Interfaces:**
- Produces(ShareApi.kt):

  ```kotlin
  data class NewParty(val courseId: String? = null, val name: String, val profile: Profile)   // courseId 가 있으면 만들자마자 제안 1번
  data class PartyMember(id, nickname, avatar, demo, me, sharing, starred, val leader: Boolean = false, val accepted: Boolean = false)
  data class PartyView(code, name, val courseId: String?, open, owner /* 내가 리더 */, members, pins,
                       val proposal: Int = 0, val leaderName: String = "", val joined: Boolean = true)
  data class Reopen(val courseId: String? = null)
  data class Propose(val courseId: String)
  data class Answer(val proposal: Int, val accept: Boolean)
  data class MemberRef(val memberId: String)
  ```

- Produces(PartyRules.kt):
  - `fun needsAnswer(view: PartyView): Boolean`: 들어와 있고(`joined`), 제안이 있고(`proposal > 0 && courseId != null`), 내 줄의 `accepted`가 false일 때만 true다.
  - `fun canRestart(view: PartyView): Boolean`: 내 줄이 `accepted`이고 코스가 있을 때 true다.

- [ ] **Step 1: 실패하는 시험 쓰기.** `PartyRulesTest`에 다음을 쓴다.
  - `noProposalNoQuestion`: proposal 0이면 `needsAnswer` false
  - `pendingMemberIsAsked`: proposal 1, courseId "c", me.accepted false → true
  - `acceptedMemberIsNotAsked`
  - `aNewLeaderWhoHasNotAnsweredIsStillAsked`: me.leader true, accepted false → true
  - `notJoinedIsNotAsked`: `joined=false` → false
  - `restartOnlyAfterAccepting`: `canRestart`는 accepted && courseId != null일 때만 true
- [ ] **Step 2:** `./gradlew --no-watch-fs :core:test --tests '*PartyRulesTest*'`. 기대: FAIL(`needsAnswer` 없음).
- [ ] **Step 3:** 자료형을 바꾸고 `PartyRules.kt`를 만든다. `courseId`가 nullable이 되어 깨지는 곳은 동작을 바꾸지 않고 컴파일만 맞춘다.
  - 서버 `view()`: `courseOf(party.courseId ?: "")`
  - 앱 `localCourse(party.courseId ?: return null)` 등
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test :server:test :app:testDebugUnitTest`. 기대: 전부 PASS.
- [ ] **Step 5:** 커밋 `feat: 함께달리기 자료형과 질문 규칙(core)`

### Task 2: 서버 저장소 열 늘리기와 멤버 순서

**Files:**
- Modify: `server/src/main/kotlin/com/byway/server/Store.kt`
- Test: `server/src/test/kotlin/com/byway/server/StoreTest.kt`(새로)

**Interfaces:**
- Produces:

  ```kotlin
  data class PartyRow(code, name, val courseId: String? /* DB 는 '' */, owner /* 리더 기기 */, open, openedAt, val proposal: Int = 0)
  data class MemberRow(partyCode, device, nickname, avatar, sharing, starred, val accepted: Int = 0, val present: Boolean = true, val blocked: Boolean = false)
  fun Store.propose(code: String, courseId: String, now: Long): Int        // proposal+1, course_id, created_at=now, 새 번호를 돌려줌
  fun Store.reopenParty(code: String, courseId: String?, now: Long)       // proposal 0, course_id ''(null 이면)
  fun Store.members(code: String): List<MemberRow>                        // 들어온 순서(rowid). present/blocked 모두 돌려줌
  ```

- 열 늘리기:
  - `Store.open`에서 `PRAGMA table_info`로 없는 열만 `ALTER TABLE ... ADD COLUMN`으로 더한다.
    - `party.proposal INTEGER NOT NULL DEFAULT 0`
    - `member.accepted INTEGER NOT NULL DEFAULT 0`
    - `member.present INTEGER NOT NULL DEFAULT 1`
    - `member.blocked INTEGER NOT NULL DEFAULT 0`
  - `party.course_id`는 NOT NULL 그대로 두고, 코스 없음은 빈 문자열로 쓴다(표를 다시 만들지 않는다).
  - `party.proposal`을 처음 더할 때 한 번만, 코스가 있던 옛 파티를 "제안 1번에 모두 승낙"으로 맞춘다. 옛 방식은 코스를 보고 들어온 것이기 때문이다.
    - `UPDATE party SET proposal = 1 WHERE course_id != ''`
    - `UPDATE member SET accepted = 1 WHERE party_code IN (SELECT code FROM party WHERE proposal = 1)`
- `putMember`는 `INSERT ... ON CONFLICT(party_code, device) DO UPDATE SET ...`로 바꾼다. rowid(들어온 순서)를 지키기 위해서다.

- [ ] **Step 1: 실패하는 시험.**
  - `oldDatabaseGainsColumns`
    - 준비: 지금 스키마의 `party`·`member` 표를 직접 만들고 행 하나씩 넣는다.
    - `Store.open` 뒤에 `party(code).proposal == 1`, `members(code).single().present == true`, `.accepted == 1`, `courseId == "river"`
    - 한 번 더 `Store.open`해도 `proposal`은 1 그대로다.
  - `memberOrderSurvivesUpdates`: a, b, c를 넣고, a의 `sharing`을 바꿔 다시 `putMember`한다. 그래도 `members().map { it.device } == [a, b, c]`
  - `partyWithoutCourseReadsNull`: `addParty(courseId=null)` 뒤 `party().courseId == null`
  - `proposeRaisesTheNumber`: `propose` 두 번이면 2, `courseId` 갱신
- [ ] **Step 2:** `./gradlew --no-watch-fs :server:test --tests '*StoreTest*'`. 기대: FAIL.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** `./gradlew --no-watch-fs :server:test`. 기대: PASS(기존 `PartyRoutesTest` 포함).
- [ ] **Step 5:** 커밋 `feat: 파티 저장소에 제안 번호와 멤버 상태 열, 들어온 순서 지키기`

### Task 3: 서버 파티 규칙과 엔드포인트

**Files:**
- Modify: `server/src/main/kotlin/com/byway/server/PartyRoutes.kt`, `DemoMembers.kt`(필요하면)
- Test: `server/src/test/kotlin/com/byway/server/PartyRoutesTest.kt`

**Interfaces:**
- Consumes: Task 1 자료형, Task 2 `Store`
- Produces(엔드포인트, 모두 기기 토큰):

| 메서드·경로 | 동작 |
|---|---|
| `POST /v1/parties` (`NewParty`) | `courseId`가 null이면 코스 없이 연다. 있으면 열고 제안 1번 |
| `POST /v1/parties/{code}/course` (`Propose`) | 리더만(아니면 403). 검사 뒤 `propose`, 리더 `accepted`=새 번호. `PartyView` 반환 |
| `POST /v1/parties/{code}/answer` (`Answer`) | 번호가 지금 제안과 다르면 409. 승낙은 `accepted`=번호, 204. 거부는 `leaveParty`, 204 |
| `POST /v1/parties/{code}/leader` (`MemberRef`) | 리더만. 대상은 들어와 있는 실제 멤버(가상 멤버·나·없는 사람은 400). `setOwner` |
| `POST /v1/parties/{code}/kick` (`MemberRef`) | 리더만, 나 자신은 400. 대상 위치를 지우고 `nickname=''`, `avatar=''`, `present=false`, `blocked=true`, `sharing=false`로 남긴다 |
| `POST /v1/parties/{code}/join` | `blocked`면 403 "이 파티에 들어갈 수 없습니다". 들어오면 `present=true`(별표 멤버가 다시 들어올 때 포함) |
| `POST /v1/parties/{code}/reopen` (`Reopen`) | 코스 없이 다시 열 수 있다. 다시 연 사람만 `present=true`, 나머지 별표 멤버는 `present=false` |

- 규칙:
  - `GET /v1/parties/{code}`: 멤버 행이 있고 막히지 않았으면 보인다(들어와 있지 않아도 `joined=false`로 보인다).
  - `view()`:
    - 멤버 목록은 `present && !blocked`인 실제 멤버(들어온 순서) + 가상 멤버다.
    - `leader`는 리더 기기 줄에 붙는다.
    - `accepted`는 `proposal > 0 && row.accepted == proposal`일 때 true다.
    - `leaderName`은 리더 닉네임이다.
    - 가상 멤버: `open && demoMembers > 0`이면 코스와 상관없이 목록에 넣고, `accepted = proposal > 0`이다. 핀은 코스가 있을 때만이다.
  - `leaveParty`: 나간 뒤 `present && !blocked` 실제 멤버가 없으면 `endParty`다. 리더가 나갔으면 그중 첫째(들어온 순서)가 리더가 된다.
  - `endParty`: 별표 멤버는 `present=false`, `sharing=false`로 남긴다. 막힌 행과 별표 없는 행은 지운다.
  - `courseProblem(server, partyCode, id)`: 파티 범위 코스는 `row.partyCode == partyCode`일 때만 된다(spec 6-10.3). 숨김·없음은 지금처럼 막는다.

- [ ] **Step 1: 실패하는 시험.** 기존 도우미(`create`, `join`, `seen`)를 따른다.
  - `partyStartsWithoutACourse`: `NewParty(null, ...)` → 201, `courseId == null`, `proposal == 0`, `members[0].leader`
  - `onlyLeaderProposes`: 비리더 → 403. 리더 → 200, `proposal == 1`, 리더 줄 `accepted`, 다른 줄 not accepted
  - `acceptAndDecline`: b 승낙 → b `accepted`. c 거부 → c가 목록에서 빠지고 c의 `GET`은 404
  - `staleAnswerIsRejected`: 제안 1 뒤 리더가 다시 제안(2). b가 `Answer(1, true)` → 409
  - `leaderTransfer`: a가 b에게 넘김 → b 보기 `owner` true, a 보기 `owner` false. 가상 멤버 id로 넘기면 400
  - `leaderLeavingPassesToEarliestPresent`: a(리더), b, c 순서로 들어오고 b가 sharing을 껐다 켬. a가 나가면 b가 리더(c 아님)
  - `kickedMemberCannotRejoin`: a가 b 내보냄 → b `GET` 404, b `join` 403, a 목록에 b 없음
  - `samePartyCourseIsChoosableByNewLeader`: a가 이 파티 범위로 올린 코스를 b가 리더가 된 뒤 제안 → 200. 다른 파티 범위 코스 → 400
  - `demoMembersWaitForACourse`(`demoMembers = 2`): 코스 없이 열면 가상 멤버 2명이 목록에 있고 `pins` 비어 있음. 제안 뒤에는 `accepted`이고 핀 2개
  - `reopenGathersOnlyTheReopener`: a, b 별표 → 끝냄 → b가 `Reopen(null)` → b 보기 멤버 [b], a `GET`은 `joined=false`. a가 join → 둘 다
  - 기존 `starredPartySurvivesEndAndReopens` 등은 `Reopen(river.id)`도 그대로 되게 둔다(코스 있는 다시 열기는 제안 1번).
- [ ] **Step 2:** `./gradlew --no-watch-fs :server:test --tests '*PartyRoutesTest*'`. 기대: 새 시험 FAIL.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** `./gradlew --no-watch-fs :server:test`. 기대: 전부 PASS.
- [ ] **Step 5:** 커밋 `feat: 함께달리기 서버 - 코스 없는 파티, 제안·승낙·거부, 리더 넘기기, 내보내기, 다시 모이기`

### Task 4: 앱 탭·화면 고르기·클라이언트

**Files:**
- Modify: `app/src/main/java/com/byway/ui/Screen.kt`, `app/src/main/java/com/byway/service/ShareClient.kt`
- Test:
  - `app/src/test/java/com/byway/ui/ScreenForTest.kt`
  - `app/src/test/java/com/byway/ui/NavTest.kt`
  - `app/src/test/java/com/byway/service/ShareClientTest.kt`

**Interfaces:**
- Produces:

  ```kotlin
  enum class Tab { HOME("홈"), BROWSE("둘러보기"), RANKING("랭킹"), MINE("내 코스"), TOGETHER("함께달리기") }
  data class Nav(..., val picking: Boolean = false)          // party 필드는 지운다. toTab/whileDriving 은 picking=false
  sealed interface Screen { ...; data object PartyAsk : Screen }  // Screen.Party 는 지운다
  fun screenFor(state: DriveState, nav: Nav, resumable: Boolean = false, asking: Boolean = false): Screen
  // 우선순위: ... splitGuide → Resume → PartyAsk(asking) → recordConsent → ...
  ShareClient.propose(code: String, courseId: String): Result<PartyView>   // POST /course
  ShareClient.answer(code: String, proposal: Int, accept: Boolean): Result<Unit>
  ShareClient.passLeader(code: String, memberId: String): Result<Unit>
  ShareClient.kick(code: String, memberId: String): Result<Unit>
  ShareClient.reopen(code: String, courseId: String?): Result<PartyView>
  ShareClient.createParty(party: NewParty)   // 그대로, courseId=null 로 부른다
  ```

- [ ] **Step 1: 실패하는 시험.**
  - `ScreenForTest`
    - `menuHasFiveEntries`: `Tab.entries.map { it.title } == ["홈","둘러보기","랭킹","내 코스","함께달리기"]`(기존 `menuHasFourEntries`를 바꾼다)
    - `aPartyQuestionComesAfterTheResumeOfferWhenParked`
      - `screenFor(parked, Nav(tab=TOGETHER), asking=true) == PartyAsk`
      - `screenFor(parked, Nav(), resumable=true, asking=true) == Resume`
      - `screenFor(moving, Nav(), asking=true) == Waiting`
      - `screenFor(parked.copy(running=true, course=course), Nav(), asking=true) == Drive`
  - `NavTest`: `pickingClosesWhenTheMenuChanges`, `pickingClosesWhenDriving`
  - `ShareClientTest`: `partyCallsUseTheirPaths`. 작은 `ServerSocket` 서버로 받은 요청 줄을 모아 아래를 확인한다.
    - `POST /v1/parties/ABC234/course`
    - `/answer`
    - `/leader`
    - `/kick`
- [ ] **Step 2:** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*ScreenForTest*' --tests '*NavTest*' --tests '*ShareClientTest*'`. 기대: FAIL.
- [ ] **Step 3:** 구현한다.
  - `MainActivity`에서 `Screen.Party` 분기와 `onParty`를 지운다. 컴파일을 위해 `Tab.TOGETHER`는 일단 기존 `partyScreen(..., onClose = { openTab(Tab.HOME) })`로 잇는다. Task 6에서 바꾼다.
  - 1/3 칸 위 탭 줄(`Rail.kt` `onTop`)에서 5개가 841dp 안에 들어가는지 Task 8에서 눈으로 본다. 넘치면 `railItem` 가로 여백을 `SPACE_4` → `SPACE_3`으로 줄인다.
- [ ] **Step 4:** `./gradlew --no-watch-fs :app:testDebugUnitTest`. 기대: PASS.
- [ ] **Step 5:** 커밋 `feat: 함께달리기 탭, 질문 화면 순서, 파티 클라이언트 호출`

### Task 5: 함께달리기 문구와 코스 묶음(순수 함수)

**Files:**
- Modify: `app/src/main/java/com/byway/ui/party/PartyCopy.kt`
- Test: `app/src/test/java/com/byway/ui/PartyCopyTest.kt`

**Interfaces:**
- Produces:

  ```kotlin
  internal fun memberTags(member: PartyMember, proposal: Int): String
  // 순서: "리더", "나", "시연용 가상 멤버", 그리고 proposal>0 이면 "함께 달림"(accepted) 또는 "응답 기다림"
  internal data class CourseChoice(val id: String, val name: String, val upload: Boolean)  // upload=true 면 고를 때 파티 범위로 올린다(id 는 로컬 m- id)
  internal data class CourseGroups(val mine: List<CourseChoice>, val listed: List<CourseChoice>, val received: List<CourseChoice>)
  internal fun partyCourseGroups(courses: List<Course>, shared: List<SharedCourse>, uploads: Map<String, String>, partyCode: String): CourseGroups
  ```

- 묶음 규칙:
  - **mine:** `m-` 코스다. 올린 적 있으면 서버 id, `upload=false`다. 없으면 로컬 id, `upload=true`다.
  - **listed:** 앱 내장 코스와 `visibility=public`인 남의 공유 코스다.
  - **received:** 링크 코스, 그리고 이 파티(`partyCode`)에 묶인 파티 범위 코스다.
  - 다른 파티에 묶인 파티 범위 코스는 어디에도 넣지 않는다.
  - 기존 `partyCourseChoices`와 `CREATE_BUTTON`은 지운다.
- 파티 범위 코스가 어느 파티에 묶였는지 앱이 알아야 한다. `SharedCourse`에 `partyCode: String? = null`을 더하고(core), 서버 `CourseRoutes`의 목록 응답에 채운다. 이 한 줄 변경은 이 Task에 넣고, `CourseRoutesTest`에 `partyCourseCarriesItsParty`로 확인한다.

- [ ] **Step 1: 실패하는 시험.**
  - `tagsShowLeaderMeDemoAndAnswer`
    - 리더·나·제안1·accepted → "리더 · 나 · 함께 달림"
    - 가상 멤버·제안1 → "시연용 가상 멤버 · 함께 달림"
    - 제안0 → 상태 없음
  - `coursesSplitIntoThreeGroups`
    - 내장 c1 → listed
    - 공개 공유 s1 → listed
    - 링크 s2 → received
    - 이 파티 범위 s3 → received
    - 다른 파티 범위 s4 → 없음
    - 올린 m-1 → mine(서버 id, upload false)
    - 안 올린 m-2 → mine(m-2, upload true)
  - `CourseRoutesTest.partyCourseCarriesItsParty`
- [ ] **Step 2:** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*PartyCopyTest*' :server:test --tests '*CourseRoutesTest*'`. 기대: FAIL.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test :server:test :app:testDebugUnitTest`. 기대: PASS.
- [ ] **Step 5:** 커밋 `feat: 함께달리기 멤버 표시와 코스 고르기 세 묶음`

### Task 6: 함께달리기 탭 화면

**Files:**
- Create: `app/src/main/java/com/byway/ui/party/TogetherScreen.kt`. `PartyScreen.kt`의 화면 함수를 옮기고 고친다. `profileBlock`, `AvatarDot`, `showPins`는 같이 옮긴다.
- Delete: `app/src/main/java/com/byway/ui/party/PartyScreen.kt`(옮긴 뒤)
- Modify:
  - `MainActivity.kt`: `Tab.TOGETHER` 연결
  - `MineScreen.kt`: "파티" 버튼과 `onParty` 지움

**Interfaces:**
- Consumes: Task 4 `Nav.picking`, `ShareClient` 호출. Task 5 `memberTags`, `partyCourseGroups`, `CourseChoice`. Task 1 `canRestart`.
- Produces: `internal fun togetherScreen(context: Context, kit: BrowseKit, nav: Nav, go: (Nav) -> Unit, onStart: (String) -> Unit): View`

- 화면(spec 6-2, 6-3, 6-5):
  - **파티 없음:**
    - 프로필
    - `PARTY_JOIN_CONSENT` + "파티원 초대"(`createParty(NewParty(null, "", me))`)
    - 코드로 참여
    - 즐겨찾는 파티: `parties()`에서 `!open`이면 "다시 모이기"(`reopen(code, null)`), `open && !joined`면 "열려 있음 · 들어가기"(`join`)
  - **파티 안:**
    - 이름과 코드(크게)
    - 초대 안내 문구
    - 멤버 줄: `memberTags(member, view.proposal)`. 리더이고 줄이 내가 아닌 실제 멤버면 "리더 넘기기", "내보내기" 작은 버튼
    - 위치 공유 끄기(켜져 있을 때), 즐겨찾기
    - 아래 버튼 칸:
      - 리더면 "코스 고르기"(→ `go(nav.copy(picking = true))`)와 "파티 끝내기"
      - 아니면 "나가기"
      - `canRestart`면 "동의하고 다시 출발"(`sharing(true)` → `onStart(courseId)`)
    - 지금의 "동의하고 위치 공유" 블록과 "내비에 코스 입히기"는 없앤다.
    - 그림 칸: 코스가 있으면 코스 + 핀, 없으면 분위기 바탕만
  - **코스 고르기(`nav.picking`):**
    - 세 묶음 제목은 "내가 저장한 코스", "목록 코스", "공유받은 코스"이고, 칩으로 하나를 고른다.
    - 고르면 오른쪽 그림에 그 코스를 보인다.
    - 아래에 코스 이름·거리와 `LOCATION_CONSENT`를 둔다. `upload`면 공유 동의 문구도 둔다(`share` 화면의 파티 범위 문구 재사용).
    - 버튼은 "동의하고 출발"과 "취소"다.
    - "동의하고 출발"은 다음 순서다.
      1. `upload`면 `upload(UploadCourse(PARTY, code, body))` → `CourseStore.setUpload`
      2. `propose(code, id)`
      3. `sharing(code, true)`
      4. `go(nav.copy(picking=false))`
      5. `onStart(id)`

      실패하면 아래 알림 줄에 적고 멈춘다.
  - 2초 폴링, `PartyTick.Ended`면 `MyParty.set(null)` 후 "파티에서 나왔습니다"를 알림 줄에 보인다. 지금 코드와 같다.
  - 탭이므로 "닫기"는 없다.

- [ ] **Step 1:** 화면 함수는 단위 시험이 없다. 로직은 Task 1·5 함수에 있다. `./gradlew --no-watch-fs :app:compileDebugKotlin`으로 Task 4의 임시 연결을 지운 뒤 컴파일이 깨지는 것을 본다. 기대: FAIL(`togetherScreen` 없음).
- [ ] **Step 2:** 구현한다.
- [ ] **Step 3:** `./gradlew --no-watch-fs :app:testDebugUnitTest :app:assembleDebug`. 기대: PASS. `grep -rn "partyScreen\|onParty\|Screen.Party\b\|nav.party" app/src/main`이 0건.
- [ ] **Step 4:** 커밋 `feat: 함께달리기 탭 화면 - 초대·목록·리더 넘기기·내보내기·코스 고르기`

### Task 7: 승낙·거부 질문

**Files:**
- Create: `app/src/main/java/com/byway/ui/party/PartyAskScreen.kt`, `app/src/main/java/com/byway/service/PartyWatch.kt`
- Modify: `MainActivity.kt`
- Test: `app/src/test/java/com/byway/service/PartyWatchTest.kt`

**Interfaces:**
- Consumes: Task 1 `needsAnswer`, Task 4 `screenFor(asking)`, `ShareClient.answer`, `sharing`
- Produces:

  ```kotlin
  /** 정차 중이고 코스가 달리지 않고 파티에 들어가 있을 때만 받는다. */
  fun shouldWatch(parked: Boolean, running: Boolean, partyCode: String?, online: Boolean): Boolean
  object PartyWatch {
      val view: PartyView?
      fun start(context, onChanged: () -> Unit)
      fun stop()
  }   // 5초마다 party(code). Ended → MyParty null.
  internal fun partyAskScreen(context: Context, kit: BrowseKit, view: PartyView, course: Course?, onAccept: () -> Unit, onDecline: () -> Unit): View
  ```

- `MainActivity.render`에서 `asking = PartyWatch.view?.let(::needsAnswer) == true`를 `screenFor`에 넘긴다. `onResume`과 상태 변화 때 `shouldWatch`로 시작하거나 멈춘다.
- 질문 화면:
  - 제목 "<leaderName>님이 코스를 골랐습니다"
  - 코스 이름·거리(코스를 아직 못 받았으면 "코스를 받는 중입니다")
  - `LOCATION_CONSENT`
  - 버튼 "동의하고 함께 달리기", "거부하고 파티 나가기"
  - 그림 칸에 코스
- **승낙:**
  1. `answer(code, proposal, true)`
  2. `sharing(code, true)`
  3. `ShareSync.refresh(force=true)`로 코스를 받는다.
  4. `start(courseId)`

  409(옛 제안)면 `PartyWatch`를 바로 다시 받아 새 질문을 보인다.
- **거부:** `answer(..., false)` → `MyParty.set(null)` → `openTab(Tab.TOGETHER)`

- [ ] **Step 1: 실패하는 시험.** `PartyWatchTest.watchesOnlyWhenParkedIdleAndInAParty`
  - `shouldWatch(true, false, "ABC234", true)` true
  - parked false → false
  - running true → false
  - code null → false
  - online false → false
- [ ] **Step 2:** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*PartyWatchTest*'`. 기대: FAIL.
- [ ] **Step 3:** 구현한다.
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test :server:test :app:testDebugUnitTest :app:testReleaseUnitTest`. 기대: 전부 PASS.
- [ ] **Step 5:** 커밋 `feat: 리더가 고른 코스 승낙·거부 질문(정차 중에만)`

### Task 8: 에뮬레이터 확인, 캡처, 보기 페이지, 보고서

**Files:**
- Create: scratchpad `together-check.sh`(저장소 밖). 두 번째 기기는 curl로 흉내 낸다.
- Modify: 보기 페이지 `site2/index.html`의 "파티 · 길 기록 · 공유" 묶음과 캡처 스크립트

- [ ] **Step 1: 시연 서버를 다시 띄운다.** gpu-wsl에서 서버를 다시 빌드하고 `tools/run-server.sh demo`로 띄운다.
  - 열 늘리기 뒤 옛 시연 파티 A39Z8V를 `GET`할 수 있는지 본다.
  - 기대: 200, `courseId` 팔당 노을, `proposal` 1, 내 줄 `accepted`(Task 2 열 늘리기 규칙).
- [ ] **Step 2: 시연 빌드를 설치한다.** `./gradlew --no-watch-fs -q :app:assembleRelease -Pbyway.serverUrl=http://100.83.190.98:8787` → 맥에서 `adb install -r`
- [ ] **Step 3: 에뮬레이터에서 확인한다(잠금 안).**
  - **메뉴:** 1/3·2/3·전체 창에서 메뉴와 탭 줄에 "함께달리기"가 보인다. 1/3 탭 줄이 넘치지 않는다. 내 코스에 "파티" 버튼이 없다.
  - **앱이 리더일 때:**
    1. 파티원 초대 → 코드와 목록(나·리더, 가상 멤버 2) → curl 기기 b가 참여 → 목록에 b
    2. 코스 고르기 → 세 묶음 → 팔당 노을 → 동의하고 출발 → 주행 화면에 가상 멤버 핀
    3. 코스 끝내기 → 탭에 "동의하고 다시 출발"
  - **리더를 넘긴 뒤:**
    1. b에게 리더 넘기기 → 앱에서 리더 버튼이 사라진다.
    2. P에서 b가 curl로 제안 → 5초 안에 질문이 뜬다.
    3. 승낙 → 출발. 코스 끝내기 → b가 다시 제안 → 거부 → 앱이 파티 밖 화면이 된다.
    4. D에서 제안하면 질문이 뜨지 않고, P로 바꾸면 뜬다.
  - **내보내기:** 앱이 리더일 때 b를 내보내면 b의 `join`이 403이다.
  - 낮·밤 둘 다 본다. 에움길 강제 종료 0건.
- [ ] **Step 4: 보기 페이지를 고친다.** "드라이빙 파티" 그림을 함께달리기 화면으로 바꾼다.
  - 바꿀 그림: 파티 없음 / 파티 안(리더) / 코스 고르기 / 질문
  - 1/3·2/3·전체 × 낮·밤으로 찍는다.
  - `check-shots.py`가 빠진 그림 0을 보이게 한다.
  - 올리고, 끝나면 시연 파티를 다시 만든다.
- [ ] **Step 5: 최종 검토와 보고서.**
  - `review-package`로 최종 검토(독립, 가장 좋은 모델)를 받는다. 지시서에 AAR 금지를 적는다.
  - Critical·Important는 한 번에 고친다(각각 RED→GREEN).
  - PR 보고서 `~/reports/pr_YYYYMMDD_HHMM.md`(요약, diff, grep 영향, 시험 로그, 검토 요청)
  - 기억 갱신
