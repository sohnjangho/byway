# 코스 공유와 드라이빙 파티 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 달린 길을 코스로 기록·공유(전체/파티/링크)하고, 파티 멤버 위치를 주행 화면에 아바타 물방울 핀으로 보인다.

**Architecture:** 순수 로직(기록 자르기·구간·코드·API 자료형)은 `core`에 둔다. `server/`는 Ktor 2.3 + SQLite로 `core`를 그대로 가져다 쓴다. 앱은 `service/`에 서버 연결·코스 저장소·파티 폴링을 두고, `DriveState`를 거쳐 화면이 그린다. 주행 규제는 `screenFor` 한 곳에서 한다.

**Tech Stack:** Kotlin 1.9.25, AGP 8.7.3, Android View, kotlinx-serialization 1.6.3, OkHttp 4.12, Ktor 2.3.12(netty, content-negotiation), sqlite-jdbc 3.46, JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-10-share-party-design.md`

## Global Constraints

- 공유 범위 문자열: `public`(전체) · `party`(파티) · `link`(링크).
- 코드: 6자. 문자 집합은 `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`(0·1·O·I 없음).
- 기록: 처음과 끝 500m를 자르고, 남은 길이가 5,000m보다 짧으면 저장하지 않는다. 구간은 약 5,000m마다 나누고 "1구간"부터 번호를 붙인다. 분위기는 `forest`·`city`·`river` 중 하나다. `attribution`은 "에움길 운전자 기록"이다.
- 서버 코스 id는 `s-` 뒤에 소문자·숫자 10자다. 내장 코스 id와 겹치지 않는다.
- 위치: 서버 메모리에만 두고 60초가 지나면 지운다. 앱은 2초마다 보내고 받는다. 30초 넘게 소식이 없는 핀은 그리지 않는다.
- 가상 멤버(서버 환경 변수 `BYWAY_DEMO_MEMBERS=2`일 때만): 이름은 "시연용 1", "시연용 2"다. 실제 차 위치가 없으면 출발점에서 시속 60km로 움직인다. 위치가 있으면 실제 차의 코스 위 자리 +1,000m(시연용 1), −1,000m(시연용 2)에 놓는다.
- 동의 전에는 위치·궤적·닉네임·아바타가 담긴 요청을 보내지 않는다. 동의는 버튼으로만 한다. 뒤로 가기나 바깥 터치는 거부로 본다.
- 정차 화면에만 둔다: 파티 만들기·참여·동의·위치 공유 끄기, 공유 범위 고르기, 코드 입력·표시, 프로필, 신고, 데이터 지우기. 주행 중에는 핀, 기록 중 한 줄, "기록 끝내기" 1개만 둔다. 핀은 애니메이션 없이 다시 그린다.
- 서버 주소는 Gradle 속성 `byway.serverUrl`(`local.properties` 또는 `-P`)로만 넣는다. 저장소와 `apk/`에는 테일넷 주소가 0건이어야 한다. 주소가 있을 때만 그 호스트 하나에 평문을 허용하는 네트워크 보안 설정을 빌드할 때 만든다.
- 블라인드 심사: 실명·연락처·이메일·소속 금지. 닉네임은 최대 10자다.
- AAR 분석 금지(Pleos 라이선스 3.4). `ai.pleos.*`는 `platform/`에만 둔다. `unregisterGearSelection` 호출 금지.
- 최소 12sp, 터치 76dp. 색·치수는 `Palette`·`Dimens` 토큰만 쓴다.

## Review Focus

1. 서버가 꺼져 있거나 5초 넘게 응답이 없을 때: 정차 화면은 "공유 서버에 연결할 수 없습니다"를 보이고 멈추지 않는다. 주행 화면은 핀만 사라진다 → Task 5 `ShareClient` 시간 초과 시험, Task 8 폴링 실패 시험.
2. 기록 중 위치가 끊겼다 튀는 경우(터널 등): 거리를 부풀리지 않는다. 500m가 넘게 튀면 거리에 넣지 않는다 → Task 1 `jumpIsNotCounted`.
3. 서버에서 지워졌거나 숨겨진 공유 코스: 다음 갱신 때 목록에서 빠지고, 달리는 중인 코스는 영향을 받지 않는다 → Task 5 `mergeDropsRemovedShared`.
4. 파티가 끝났거나 내가 빠진 뒤의 폴링: 404를 받으면 핀을 지우고 파티 상태를 비운다 → Task 8 `endedPartyClearsPins`.
5. 기록 중 앱이 죽은 경우: 기록은 사라져도 다음 실행은 정상 화면으로 돌아오고, 기록 화면에 갇히지 않는다 → Task 6 `ScreenForTest.recordingNeedsLiveState`.

---

### Task 0: 가이드와 레인

**Files:** Modify `docs/제작가이드.md`(3장 구조에 `core/record`·`core/share`·`server/`, 5장 연결표의 부가 1을 "기록은 DriveFeature가 아니다, `core/record` + `DriveService` + `ui/record`"로, 12장 레인 표에 `server`), `tools/check-lane.sh`(`server)` 레인: `^(server/|settings\.gradle\.kts|docs/)`).

- [ ] **Step 1:** 고치고 `bash tools/check-lane.sh server feature/drive-screen` 실행 → Expected: `레인 확인 통과: server`(아직 server 변경 없음, docs만).
- [ ] **Step 2:** 커밋 `docs: 기록과 서버 레인을 가이드에 적는다`.

### Task 1: 기록 로직 (core)

**Files:** Create `core/src/main/kotlin/com/byway/core/record/Recording.kt`, `core/src/main/kotlin/com/byway/core/share/ShareCode.kt`; Modify `core/src/main/kotlin/com/byway/core/course/CourseFile.kt`(쓰기 추가); Test `core/src/test/kotlin/com/byway/core/record/RecordingTest.kt`, `core/src/test/kotlin/com/byway/core/course/CourseFileTest.kt`(추가).

**Interfaces — Produces:**
- `fun writeCourse(course: Course): String` — 코스 형식 v2 JSON. `parseCourse(writeCourse(c)) == c`.
- `data class RecordRules(val trimMeters: Double = 500.0, val minMeters: Double = 5_000.0, val sectionMeters: Double = 5_000.0, val jumpMeters: Double = 500.0)`
- `fun trimEnds(path: List<GeoPoint>, meters: Double): List<GeoPoint>`
- `fun drivenMeters(path: List<GeoPoint>, jumpMeters: Double): Double` — 이웃 점 사이가 `jumpMeters`보다 멀면 그 구간을 더하지 않는다.
- `sealed interface Recorded { data class Made(val course: Course) : Recorded; data class TooShort(val meters: Double) : Recorded; data class BadName(val reason: String) : Recorded }`
- `fun makeCourse(id: String, name: String, mood: String, fixes: List<GeoPoint>, rules: RecordRules = RecordRules()): Recorded`
- `fun nameProblem(name: String): String?` — 빈 이름이면 "이름을 넣어 주세요", 20자 초과면 "20자 안으로 줄여 주세요", 금칙어면 "쓸 수 없는 말이 들어 있습니다", 문제가 없으면 null.
- `val RECORD_MOODS = listOf("forest", "city", "river")`
- `const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"`, `fun newCode(random: kotlin.random.Random): String`, `fun isCode(text: String): Boolean`

- [ ] **Step 1: 실패하는 시험** — `RecordingTest`: `trimsHalfKilometerFromBothEnds`(직선 20km 경로 → 앞뒤 500m 안 점이 없다), `tooShortAfterTrimIsRejected`(5.8km 경로 → `TooShort`), `sectionsEveryFiveKilometersCoverThePath`(21km → 구간 4~5개, `parseCourse(writeCourse(made))` 통과, 모든 구간 mood == 입력), `jumpIsNotCounted`(1km 간격 점 사이에 3km 튐 → 튐 거리가 빠진다), `badNameIsRejected`(빈 이름, 21자), `madeCourseHasDriverAttribution`; `ShareCode`: `codeUsesOnlyClearCharacters`(1,000개 생성 → 모두 `isCode`, 0·1·O·I 없음). `CourseFileTest.writtenCourseReadsBack`(내장 코스 15개 전부 왕복).
- [ ] **Step 2:** `./gradlew :core:test --tests '*RecordingTest' --tests '*CourseFileTest'` → Expected: 컴파일 실패(없는 함수).
- [ ] **Step 3: 구현** — 단순화는 `keyPointIndexes(path, 30.0)`로 점을 줄인다. 구간 경계는 단순화한 경로의 누적 거리로 나누되 마지막 구간이 2,500m보다 짧으면 앞 구간에 붙인다. 금칙어 목록은 `Recording.kt` 안에 소수(욕설·광고 대표어)만 둔다.
- [ ] **Step 4:** 같은 명령 → Expected: PASS. 이어서 `./gradlew :core:test --rerun-tasks` 전체 통과.
- [ ] **Step 5:** 커밋 `feat(core): 달린 길을 코스로 만드는 규칙`.

### Task 2: API 자료형 (core) + 서버 골격·코스 (server)

**Files:** Create `core/src/main/kotlin/com/byway/core/share/ShareApi.kt`; `server/build.gradle.kts`; `server/src/main/kotlin/com/byway/server/{Main.kt,Store.kt,CourseRoutes.kt,Auth.kt}`; Test `server/src/test/kotlin/com/byway/server/CourseRoutesTest.kt`; Modify `settings.gradle.kts`(`include(":server")`), `.gitignore`(`server/data/`).

**Interfaces — Produces (`com.byway.core.share`, 모두 `@Serializable`):**
- `DeviceToken(token: String)`
- `UploadCourse(visibility: String, partyCode: String? = null, body: String)` — body는 `writeCourse` 결과.
- `Uploaded(id: String, code: String?)`
- `SharedCourse(id: String, code: String?, visibility: String, finishes: Int, mine: Boolean, body: String)`
- `Problem(message: String)` — 4xx 본문.

**API(모두 `Authorization: Bearer <token>`, `POST /v1/devices` 제외):**

| 요청 | 응답 |
|---|---|
| `POST /v1/devices` | 201 `DeviceToken`. 서버는 SHA-256 해시만 저장 |
| `GET /v1/courses` | 200 `List<SharedCourse>`: 전체 공개(숨김 제외) + 내가 올린 것 + 내가 멤버인 파티의 파티 코스 |
| `POST /v1/courses` | 201 `Uploaded`. `parseCourse` 실패·5km 미만·`nameProblem`·`planStops` 실패면 400 `Problem`. `party`는 `partyCode`의 멤버여야 하며 코드 없음, `public`·`link`는 코드 있음 |
| `GET /v1/codes/{code}` | 200 `SharedCourse`(public·link만). 없거나 파티 코스면 404 |
| `DELETE /v1/courses/{id}` | 204. 올린 기기만, 아니면 403 |
| `POST /v1/courses/{id}/runs` | 204. finishes+1, 기기 정보 저장 안 함 |
| `POST /v1/courses/{id}/reports` | 204. 숨김 표시, 서버 로그에 `report <id>` |
| `DELETE /v1/me` | 204. 내 코스, 파티 멤버 정보, 위치 삭제 |

- [ ] **Step 1: 실패하는 시험** — Ktor `testApplication` + 임시 SQLite 파일. `deviceTokenIsStoredHashed`, `uploadValidCoursePublicGetsCode`, `shortCourseIsRejected`, `badJsonIsRejected`, `publicListHidesReported`, `linkCourseOnlyByCode`(목록에 안 보이고 `/codes/{code}`로 받음), `partyCourseNotByCode`(404), `onlyOwnerDeletes`(403/204), `finishesCount`, `deleteMeRemovesMyCourses`, `noTokenIs401`.
- [ ] **Step 2:** `./gradlew :server:test` → Expected: 실패(모듈·클래스 없음).
- [ ] **Step 3: 구현** — `server/build.gradle.kts`: `kotlin("jvm")`, `kotlin("plugin.serialization")`, `application`(mainClass `com.byway.server.MainKt`), JVM 17, 의존 `project(":core")`, `io.ktor:ktor-server-netty|content-negotiation|serialization-kotlinx-json:2.3.12`, `org.xerial:sqlite-jdbc:3.46.1.3`, 시험 `ktor-server-test-host:2.3.12`. 환경 변수: `BYWAY_PORT`(기본 8787), `BYWAY_DB`(기본 `server/data/byway.db`), `BYWAY_DEMO_MEMBERS`. `fun Application.byway(store: Store, clock: () -> Long = System::currentTimeMillis, demoMembers: Int = 0)`로 시험에서 주입한다.
- [ ] **Step 4:** `./gradlew :server:test` → Expected: PASS. `bash tools/check-lane.sh server feature/drive-screen`.
- [ ] **Step 5:** 커밋 `feat(server): 기기 토큰과 코스 공유 API`.

### Task 3: 파티 서버

**Files:** Create `server/src/main/kotlin/com/byway/server/{PartyRoutes.kt,Positions.kt,DemoMembers.kt}`; Modify `core/.../share/ShareApi.kt`; Test `server/src/test/kotlin/com/byway/server/PartyRoutesTest.kt`.

**Interfaces — Produces (`core/share`):**
- `Profile(nickname: String, avatar: String)` — avatar는 `avatar-01`~`avatar-10`.
- `NewParty(courseId: String, name: String, profile: Profile)`
- `PartyMember(id: String, nickname: String, avatar: String, demo: Boolean, me: Boolean, sharing: Boolean, starred: Boolean)` — starred는 me일 때만 의미가 있다.
- `PartyPin(memberId: String, lat: Double, lon: Double)`
- `PartyView(code: String, name: String, courseId: String, open: Boolean, owner: Boolean, members: List<PartyMember>, pins: List<PartyPin>)`
- `Position(lat: Double, lon: Double)`, `Toggle(on: Boolean)`, `Reopen(courseId: String)`

| 요청 | 동작 |
|---|---|
| `POST /v1/parties` | `NewParty` → 201 `PartyView`. 코스는 내장 id, 보이는 public/link 공유 코스, 내가 올린 party 코스여야 한다. 남의 party 코스면 400 |
| `POST /v1/parties/{code}/join` | `Profile` → 200 `PartyView`. 닫힌 파티면 404 |
| `GET /v1/parties/{code}` | 멤버만 200 `PartyView`(60초 안 위치만 pins에), 아니면 404 |
| `GET /v1/parties` | 내가 멤버인 열린 파티 + 내가 별표한 닫힌 파티 `List<PartyView>` |
| `PUT /v1/parties/{code}/sharing` | `Toggle`. off면 내 위치 즉시 삭제 |
| `PUT /v1/parties/{code}/position` | `Position`. sharing이 on일 때만 저장(아니면 409) |
| `PUT /v1/parties/{code}/star` | `Toggle` |
| `POST /v1/parties/{code}/end` | 만든 사람만. 위치 전부 삭제, 별표 안 한 멤버 삭제, 별표가 없으면 파티 삭제 |
| `POST /v1/parties/{code}/leave` | 내 멤버 정보·위치 삭제. 남은 실제 멤버가 없으면 end |
| `POST /v1/parties/{code}/reopen` | 별표한 멤버만. `Reopen` → 열린 파티, 새 코스 |

- [ ] **Step 1: 실패하는 시험** — `createAndJoinShowsMembers`, `positionNeedsSharingOn`(409), `sharingOffDeletesPositionNow`, `positionsExpireAfterSixtySeconds`(clock 주입), `endDeletesPositionsAndUnstarredMembers`, `starredPartySurvivesEndAndReopens`, `unstarredPartyIsGoneAfterEnd`(404), `nonMemberCannotSeeParty`, `partyCourseVisibleToMembersOnly`(Task 2의 `GET /v1/courses`와 연결), `othersPartyCourseCannotStartParty`, `demoMembersFollowRealCar`(demoMembers=2, 실제 위치 넣기 전에는 출발점 근처이고 60초 뒤 약 1km 진행, 넣은 뒤에는 코스 위 거리 ±1,000m(오차 150m)), `leaveLastMemberEnds`.
- [ ] **Step 2:** `./gradlew :server:test` → Expected: 새 시험 실패.
- [ ] **Step 3: 구현** — 위치는 `Positions`(멤버 id → (point, at)) 메모리 맵이다. 코스 위 거리는 `core` 거리 계산으로 가장 가까운 점의 누적 거리를 쓴다. 가상 멤버는 DB 멤버로 넣지 않고 `GET` 때 계산해 `members`·`pins`에 붙인다(`demo = true`).
- [ ] **Step 4:** `./gradlew :server:test` → Expected: PASS.
- [ ] **Step 5:** 커밋 `feat(server): 파티와 위치, 시연용 가상 멤버`.

### Task 4: gpu-wsl 서버 실행과 에뮬레이터 연결 확인

**Files:** Create `tools/run-server.sh`(빌드 후 `server/build/install` 실행, nohup, 로그 `server/data/server.log`, `BYWAY_DEMO_MEMBERS` 인자).

- [ ] **Step 1:** `./gradlew :server:installDist && tools/run-server.sh demo` → `curl -s -X POST http://127.0.0.1:8787/v1/devices` Expected: `{"token":...}`.
- [ ] **Step 2:** 맥 에뮬레이터에서 `nc`로 `POST /v1/devices` → Expected: 201 응답(테일넷 IP:8787).
- [ ] **Step 3:** 커밋 `chore(server): gpu-wsl 실행 스크립트`.

### Task 5: 앱 연결·코스 저장소

**Files:** Modify `app/build.gradle.kts`(`byway.serverUrl` → `BuildConfig.SERVER_URL`; 생성 res 폴더에 `xml/network_security_config.xml`), `app/src/main/AndroidManifest.xml`(INTERNET, networkSecurityConfig), `service/CourseCatalog.kt`(합치기·`invalidate()`), `ui/MainActivity.kt`(`sharedBook` 다시 만들기); Create `service/ShareClient.kt`, `service/CourseStore.kt`, `service/Profile.kt`; Test `app/src/test/java/com/byway/service/CourseStoreTest.kt`, `ShareClientTest.kt`.

**Interfaces — Produces:**
- `object CourseStore { fun mine(context): List<Course>; fun received(context): List<Course>; fun shared(context): List<SharedCourse>; fun saveMine(context, course); fun saveReceived(context, shared: SharedCourse); fun replaceShared(context, list: List<SharedCourse>); fun deleteMine(context, id); fun clearAll(context) }` — 파일은 `filesDir/courses/{mine,received,shared}/<id>.json`.
- `fun mergeCourses(assets: List<Course>, mine: List<Course>, received: List<Course>, shared: List<Course>): List<Course>` — id가 겹치면 assets > mine > received > shared 순서로 남긴다(순수 함수, 시험 대상).
- `class ShareClient(baseUrl: String, tokenStore: TokenStore, http: OkHttpClient)` — 메서드는 모두 백그라운드 스레드용 블로킹이고 `Result<T>`를 돌려준다: `courses()`, `upload(UploadCourse)`, `byCode(code)`, `delete(id)`, `finished(id)`, `report(id)`, `deleteMe()`, `createParty(NewParty)`, `join(code, Profile)`, `party(code)`, `parties()`, `sharing(code, on)`, `position(code, Position)`, `star(code, on)`, `end(code)`, `leave(code)`, `reopen(code, courseId)`. 연결·읽기 시간 초과 5초. `baseUrl`이 비면 모두 즉시 실패(`Offline`).
- `object Profile { fun get(context): com.byway.core.share.Profile?; fun set(context, profile) }`
- `object Share { fun client(context): ShareClient; val io: Executor }` — 앱 전역 1개.

- [ ] **Step 1: 실패하는 시험** — `CourseStoreTest.mergeKeepsAssetsFirst`, `mergeDropsRemovedShared`(replaceShared에서 빠진 id는 목록에서 없어짐 — 순수 함수 수준), `ShareClientTest.emptyUrlFailsFast`, `timeoutIsFailureNotCrash`(OkHttp MockWebServer 대신 닫힌 포트·무응답 소켓으로 5초 안 실패; 의존을 늘리지 않는다).
- [ ] **Step 2:** `./gradlew :app:testReleaseUnitTest --tests '*CourseStoreTest' --tests '*ShareClientTest'` → Expected: 실패.
- [ ] **Step 3: 구현** — 네트워크 보안 설정은 `byway.serverUrl`의 호스트 하나만 `cleartextTrafficPermitted="true"`, 주소가 없으면 base-config `false`만 든 파일을 만든다. `FinishRecorder`에서 공유 코스(`s-` id) 완주를 `Share.io`로 보낸다.
- [ ] **Step 4:** 같은 명령 → PASS. `./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :core:test` 전체 통과.
- [ ] **Step 5:** 커밋 `feat(app): 서버 연결과 받은 코스 저장소`.

### Task 6: 기록 흐름 (서비스·화면)

**Files:** Modify `service/DriveBoard.kt`(`DriveState`에 `recording: Recording? = null`, `recorded: List<GeoPoint>? = null`), `service/DriveService.kt`(`ACTION_RECORD_START`/`ACTION_RECORD_STOP`, `recordStart(context)`, `recordStop(context)`), `ui/Screen.kt`(`Screen.Recording`, `Screen.SaveRecord`), `ui/MainActivity.kt`, `ui/MineScreen.kt`(진입 버튼); Create `ui/record/{RecordConsent.kt,RecordingScreen.kt,SaveRecordScreen.kt}`; Test `app/src/test/java/com/byway/ui/ScreenForTest.kt`(추가).

**Interfaces:** `data class Recording(val meters: Double, val fixes: Int)`. `screenFor`: `state.recording != null` → `Screen.Recording`(주행·정차 모두), `state.recorded != null && state.parked` → `Screen.SaveRecord`, `state.recorded != null && !parked` → `Screen.Waiting`.

- [ ] **Step 1: 실패하는 시험** — `recordingShowsWhileDriving`, `recordingShowsWhileParked`, `savingOnlyWhenParked`, `recordingNeedsLiveState`(새 `DriveState()` 기본값은 기록 화면이 아니다), `courseRunBeatsNothingElse`(running이면 Drive 그대로).
- [ ] **Step 2:** `./gradlew :app:testReleaseUnitTest --tests '*ScreenForTest'` → 실패.
- [ ] **Step 3: 구현** — 동의 화면 문구는 스펙 6-3. 기록 중 위치는 `fixes`에 쌓고 `drivenMeters`로 거리를 낸다. 끝내면 `recorded`에 넘기고 서비스는 연출·내비를 건드리지 않는다. 저장 화면에서 이름·분위기(숲길/도시/물가) → `makeCourse` → `CourseStore.saveMine` → `CourseCatalog.invalidate()`. id는 `m-` + `newCode` 소문자.
- [ ] **Step 4:** 시험 통과 + 전체 시험.
- [ ] **Step 5:** 커밋 `feat(app): 달린 길 기록하기`.

### Task 7: 공유 화면 (내 코스·둘러보기·홈·설정)

**Files:** Create `ui/share/{ShareSheet.kt,ConsentPanel.kt,CodeEntry.kt,SharedCards.kt}`, `ui/ProfileScreen.kt`; Modify `ui/MineScreen.kt`(내가 만든 코스·받은 코스·공유/삭제), `ui/BrowseScreen.kt`(코드로 받기, 공유 카드), `ui/HomeScreen.kt`(공유 코스: 완주 N회, 남의 코스 신고), `ui/SettingsScreen.kt`(프로필, 내 데이터 지우기), `ui/Screen.kt`(`Nav`에 `share: String?`, `profile: Boolean`); Test `app/src/test/java/com/byway/ui/ShareCopyTest.kt`.

**Interfaces:** `fun shareScopes(): List<Pair<String, String>>` = public "전체", party "파티", link "링크". `fun consentText(scope: String): String`(무엇이·누가·언제 지워지는지). `fun finishesText(n: Int): String` = "완주 ${n}회"(0이면 "아직 완주 기록 없음").

- [ ] **Step 1: 실패하는 시험** — `ShareCopyTest`: 범위 3개 순서·문구, 동의 문구에 "경로", "닉네임", "지웁니다"가 들어간다, `finishesText(0)`, `finishesText(3)`. `ScreenForTest.shareSheetOnlyWhenParked`.
- [ ] **Step 2:** 실패 확인.
- [ ] **Step 3: 구현** — 표지·장면이 없는 코스는 기존 코스 선 그림 대체(`CoursePathView`/`SceneView` scene=null)를 쓰고, 둘러보기 한국 지도에는 시작점에 핀을 둔다. 근거 없는 수치(고도·계절·시간 적합도)는 비운다. 정차 화면을 열 때 `Share.io`로 `courses()`를 받아 `CourseStore.replaceShared` 후 `invalidate()`, 실패하면 마지막 저장본과 "공유 서버에 연결할 수 없습니다. 앱에 든 코스는 그대로 쓸 수 있습니다".
- [ ] **Step 4:** 시험 + 전체 시험 + `assembleRelease`.
- [ ] **Step 5:** 커밋 `feat(ui): 코스 공유 범위와 코드로 받기`.

### Task 8: 파티 (아바타·화면·폴링·핀)

**Files:** Create `tools/make-avatars.py`(직접 그린 도형 아바타 10개 → `app/src/main/assets/avatars/avatar-01.png`~`10`, 256px), `service/PartyLink.kt`, `ui/party/{PartyScreen.kt,PartyPins.kt}`; Modify `service/DriveBoard.kt`(`party: PartyView? = null`), `service/DriveService.kt`(코스 끝내기 때 `sharing off`), `ui/SceneView.kt`(핀 그리기), `ui/Screen.kt`(`Nav.party: String?` → `Screen.Party`), `ui/MineScreen.kt`(파티 진입); Test `app/src/test/java/com/byway/service/PartyLinkTest.kt`, `app/src/test/java/com/byway/ui/PartyPinsTest.kt`, `tools/test_make_avatars.py`.

**Interfaces:**
- `class PartyLink(client: ShareClient, clock: () -> Long)` — `fun tick(code: String, me: GeoPoint?, sharing: Boolean): PartyView?`(보내고 받기 1회, 404면 null), `fun livePins(view: PartyView, now: Long, lastSeen: Map<String, Long>): List<PartyPin>`(30초 규칙).
- `fun pinGeometry(tipX: Float, tipY: Float, radius: Float): PinGeometry` — `PinGeometry(centerX, centerY, radius)`, 순수 계산(시험 대상). 그리기는 `PartyPins`가 물방울 경로를 그리고 `BitmapShader`로 원형 아바타를 넣는다.

- [ ] **Step 1: 실패하는 시험** — `PartyLinkTest.endedPartyClearsPins`(404 → null), `sendsOnlyWhenSharing`, `stalePinsDropAfterThirtySeconds`; `PartyPinsTest.pinCircleSitsAboveTip`; `test_make_avatars.py`(10개, 256×256, 서로 다름). `ScreenForTest.partyScreenOnlyWhenParked`.
- [ ] **Step 2:** 실패 확인.
- [ ] **Step 3: 구현** — 파티 만들기에서 코스 고르기: 내장·전체·링크 코스는 바로, 공유 안 한 내 코스는 Task 7 공유 동의를 거쳐 `party` 범위로 올린 뒤, 남의 파티 코스는 목록에서 뺀다. 파티 화면: 코드 크게, 멤버 목록(가상 멤버는 "시연용 가상 멤버"), 위치 공유 동의/끄기, 별표(스펙 6-5 문구), "내비에 코스 입히기", 만든 사람은 "파티 끝내기", 아니면 "나가기". 폴링은 `DriveService`가 파티가 있을 때 2초마다 `Share.io`에서 `tick`, 결과를 `DriveBoard.party`에. 핀은 `SceneView`가 코스 좌표를 화면으로 바꾸는 기존 계산을 그대로 쓴다.
- [ ] **Step 4:** 시험 + 전체 시험 + `python3 -m unittest tools/test_*.py`.
- [ ] **Step 5:** 커밋 `feat: 드라이빙 파티와 아바타 핀`.

### Task 9: 에뮬레이터 확인·캡처·보고

**Files:** Create scratchpad 스크립트(저장소 밖), 보고서 `~/reports/pr_YYYYMMDD_HHMM.*`.

- [ ] **Step 1:** 시연 빌드(`-Pbyway.serverUrl=http://<테일넷 IP>:8787`, `apk/` 밖)와 제출 빌드(주소 없음) 둘 다 `assembleRelease`. 제출 빌드 APK 안 문자열에 `100.` 테일넷 주소가 0건인지 `unzip -p … | strings | grep` 확인(우리 APK만, Pleos AAR은 열지 않는다).
- [ ] **Step 2:** 에뮬레이터(잠금): 기록(팔당 코스 일부 `geo fix`) → 저장 → 세 범위 공유 → 두 번째 토큰으로 `curl` 업로드한 링크 코스를 코드로 받기 → 받은 코스 내비 입히기(경로 올림 로그) → 파티 만들기 + 가상 멤버 핀 주행 화면 → 위치 공유 끄기 뒤 서버 `GET` pins에 내가 없음 → 서버 끄고 홈·둘러보기·주행 흐름.
- [ ] **Step 3:** 캡처(2/3 창, 주간): 기록 중, 공유 범위, 코드, 둘러보기 공유 카드, 파티 핀 주행 화면.
- [ ] **Step 4:** 전체 시험 `--rerun-tasks`(core·app debug/release·feature·server) + `tools/test_*.py` + `grep` 3종(키, 테일넷 주소, `ai.pleos` 위치).
- [ ] **Step 5:** 최종 검토(새 맥락의 검토 에이전트, AAR 금지 명시) → 고칠 것 고침 → PR 보고서 → 기획서 세션에 동작 범위·시험 수·에뮬레이터 확인·캡처 경로 보고.
