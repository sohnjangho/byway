# Gleo 음성 연동 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Gleo가 부를 수 있는 함수 3개(찾기·출발·에움길 조작)를 에움길에 붙이고, 시연용 Gleo 흉내 앱으로 그 흐름을 보인다.

**Architecture:**
- 판단은 모두 core의 순수 함수다. 인자 읽기, 코스 찾기와 이름 맞추기, 함수별 판단, Callback JSON 만들기가 여기에 들어간다.
- app은 방송 수신기 하나(`GleoReceiver`)로 받아 판단 결과를 `DriveService`, `MyDrives`, `ShareClient`에 잇는다.
- Callback 방송은 `com.byway.platform.PleosGleo`만 보낸다.
- 시연용 흉내 앱은 별도 모듈 `gleosim`이다. 같은 규격의 방송을 보내고 받는다.

**Tech Stack:** Kotlin 1.9.25, Android(minSdk 28, target 34), kotlinx-serialization-json 1.6.3, JUnit 4, Pleos TextToSpeech 3.3.1(흉내 앱 음성)

**Spec:** `docs/superpowers/specs/2026-11-13-gleo-voice-design.md`

## Global Constraints

- **AAR:** AAR, APK, 시스템 앱을 풀거나 분석하지 않는다(Pleos 라이선스 3.4). 서브에이전트 지시서에도 이 조항을 넣는다.
- **`ai.pleos.*` 위치:** app 모듈에서는 `com.byway.platform` 안에만 둔다. 흉내 앱(`gleosim`)은 제출물이 아닌 시연 도구라 예외로, TTS 감싸개 한 파일에만 둔다.
- **함수 이름(고정):** `FIND_DRIVING_COURSES`, `START_DRIVING_COURSE`, `CONTROL_BYWAY_DRIVE`
- **Callback 방송:**
  - action `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`
  - extra `packageName` = `"com.byway"`, `response` = JSON 문자열
  - `setPackage`는 달지 않는다.
- **Gleo가 보내는 extra:** `ACTION_PARAM`. 맨 JSON과 따옴표로 한 번 더 감싼 JSON 둘 다 받는다.
- **글의 언어**
  - 스키마 설명과 `message`는 영어 한두 문장으로 쓰고, 예시 문구는 넣지 않는다. 코스 이름, 지역, 구간 이름은 한국어 그대로 둔다.
  - 화면 문구와 흉내 앱 문구는 한국어다.
- **시간:** 수신기는 `goAsync()`로 받고 **6초** 안에 끝낸다. 6초를 넘기면 `fail`로 "A timeout occurred while processing the request."를 보낸다. Gleo의 제한은 7초다.
- **Callback은 요청 하나당 정확히 한 번** 보낸다. 예외가 나도 보낸다.
- **찾기 결과:** `data`에는 최대 3개만 넣는다. 직전 결과는 10분 동안 기억한다.
- **출발**
  - 주행 중에도 출발한다(PM 11-13).
  - 달리는 코스가 있으면 `fail`이다.
  - 성공하면 `mode: "silent"`로 보낸다(에움길 출발 안내를 쓴다).
  - 화면 배치는 그대로 둔다.
- **커밋과 빌드**
  - 작성자는 `byway-dev <dev@byway.invalid>`이고, 끝에 Co-Authored-By와 Claude-Session 줄을 단다.
  - gradle은 `--no-watch-fs`로 돌린다.
  - 저장소와 `apk/`에 테일넷 주소를 넣지 않는다.

## Review Focus

1. **`ACTION_PARAM`이 없거나, 빈 문자열이거나, 깨진 JSON일 때:** 앱이 죽지 않는다. `asking`(필요한 인자 안내)이나 `fail`로 한 번 답한다. → Task 1 `paramsTolerateGarbage`
2. **Gleo가 같은 `START`를 연달아 두 번 보낼 때(재시도):** 코스는 한 번만 시작되고, 두 번째는 "already running" `fail`이다. → Task 3 `secondStartWhileRunningFails`와 Task 4 실측
3. **"노을"처럼 여러 코스에 맞는 이름:** 출발하지 않는다. `asking`으로 후보를 최대 3개 보낸다. 이름의 띄어쓰기와 대소문자가 달라도 정확히 같은 이름은 하나로 정해진다. → Task 2 `ambiguousNameAsks`, `exactNameIgnoresSpacesAndCase`
4. **앱이 꺼진 상태에서 처음 받을 때:** 코스 목록 읽기가 6초를 넘으면 timeout `fail`을 정확히 한 번 보내고, 늦게 끝난 결과는 버린다. → Task 4 `replyOnce`
5. **화면 뒤(2분할), 코스 없음, 포그라운드 서비스 시작이 막힐 때:** `fail`로 "Open 에움길 on the screen and try again."을 보낸다. 앱이 죽지 않는다. → Task 4 수신기 처리와 Task 5 실측

---

### Task 1: Gleo 요청과 답의 모양 (core)

**Files:**
- Create: `core/src/main/kotlin/com/byway/core/gleo/GleoProtocol.kt`
- Test: `core/src/test/kotlin/com/byway/core/gleo/GleoProtocolTest.kt`

**Interfaces:**
- Produces:
  - `object GleoNames { const val FIND = "FIND_DRIVING_COURSES"; const val START = "START_DRIVING_COURSE"; const val CONTROL = "CONTROL_BYWAY_DRIVE"; const val PARAM_EXTRA = "ACTION_PARAM"; val ALL: List<String> }`
  - `fun parseGleoParams(raw: String?): Map<String, JsonPrimitive>`
    - 맨 JSON과 따옴표로 감싼 JSON 둘 다 읽는다.
    - null, 빈 문자열, 깨진 JSON이면 빈 맵을 돌려준다.
  - `enum class GleoStatus { SUCCESS, ASKING, FAIL }`, `enum class GleoMode { VOICE, BEEP, SILENT }`
  - `data class GleoReply(val function: String, val status: GleoStatus, val message: String, val data: List<Map<String, Any>> = emptyList(), val mode: GleoMode = GleoMode.VOICE)`
  - `fun GleoReply.toJson(): String`
    - 키는 `functionName`, `status`, `message`, `data`, `mode`다. 값은 소문자다.
    - `data`는 JSON 배열을 **문자열로** 넣는다. 비었으면 키를 뺀다.
    - `mode`가 VOICE면 키를 뺀다.
  - `val MOOD_ENUM: Map<String, String>`: 영어 enum → `MOOD_KEYS`
    - 예: `"sunset" to "times:sunset"`, `"date" to "moods:date"`, `"rain" to "weathers:rain"`. 12개다.
  - `val LENGTH_ENUM: Map<String, LengthBand>`: `up_to_30km`, `up_to_60km`, `up_to_100km`, `over_100km`
  - `val RANK_ENUM: Map<String, RankBy>`: `curves`→CURVES, `driving_fun`→FUN, `scenery`→SCENERY, `length`→LENGTH
  - `val COMMANDS: List<String>`: `stop_by`, `back_to_course`, `resume`, `end_course`, `status`, `next_spot`, `voice_guide_on`, `voice_guide_off`, `cabin_effects_on`, `cabin_effects_off`, `stop_recording`, `stop_location_sharing`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test fun paramsReadPlainAndQuotedJson() {
    assertEquals("노을", parseGleoParams("""{"searchTerm":"노을"}""")["searchTerm"]!!.content)
    assertEquals("미팅", parseGleoParams("\"{\\\"title\\\":\\\"미팅\\\"}\"")["title"]!!.content)
    assertEquals(25.0, parseGleoParams("""{"nearbyKm":25}""")["nearbyKm"]!!.double, 0.0)
}
@Test fun paramsTolerateGarbage() {
    listOf(null, "", "   ", "{", "[1,2]", "\"plain\"").forEach { assertTrue(parseGleoParams(it).isEmpty()) }
}
@Test fun replyJsonMatchesCallbackSchema() {
    val json = Json.parseToJsonElement(GleoReply(GleoNames.START, GleoStatus.SUCCESS, "Started.", listOf(mapOf("name" to "팔당 노을", "km" to 50.3)), GleoMode.SILENT).toJson()).jsonObject
    assertEquals("START_DRIVING_COURSE", json["functionName"]!!.jsonPrimitive.content)
    assertEquals("success", json["status"]!!.jsonPrimitive.content)
    assertEquals("silent", json["mode"]!!.jsonPrimitive.content)
    assertTrue(json["data"]!!.jsonPrimitive.isString)
    assertEquals("팔당 노을", Json.parseToJsonElement(json["data"]!!.jsonPrimitive.content).jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
    val bare = Json.parseToJsonElement(GleoReply(GleoNames.FIND, GleoStatus.FAIL, "No.").toJson()).jsonObject
    assertFalse("data" in bare); assertFalse("mode" in bare)
}
@Test fun enumsCoverEveryKey() {
    assertEquals(MOOD_KEYS.toSet(), MOOD_ENUM.values.toSet())
    assertEquals(LengthBand.entries.toSet(), LENGTH_ENUM.values.toSet())
    assertEquals(RankBy.entries.toSet(), RANK_ENUM.values.toSet())
    assertEquals(12, COMMANDS.size)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew --no-watch-fs -q :core:test --tests 'com.byway.core.gleo.GleoProtocolTest'`
Expected: FAIL. 컴파일 오류(`parseGleoParams` 없음).

- [ ] **Step 3: Implement the Produces block in `GleoProtocol.kt`**
  - kotlinx `Json.parseToJsonElement`를 쓴다.
  - 결과가 문자열 primitive면 그 내용을 한 번 더 파싱한다.
  - JSON 객체가 아니면 빈 맵을 돌려준다.

- [ ] **Step 4: Run test to verify it passes**
  - 같은 명령을 돌린다. Expected: PASS(4개)

- [ ] **Step 5: Commit**: `feat: Gleo 요청 인자 읽기와 Callback JSON 모양(core)`

### Task 2: 음성용 코스 찾기와 이름 맞추기 (core)

**Files:**
- Create: `core/src/main/kotlin/com/byway/core/gleo/VoiceSearch.kt`
- Test: `core/src/test/kotlin/com/byway/core/gleo/VoiceSearchTest.kt`

**Interfaces:**
- Consumes:
  - `filterCourses`, `CourseFilter`, `NearRange`, `LengthBand`, `rank(courses, by)`, `Found`(기존 core)
  - `MOOD_ENUM`, `LENGTH_ENUM`, `RANK_ENUM`(Task 1)
- Produces:
  - `data class VoiceQuery(val searchTerm: String = "", val mood: String? = null, val nearbyKm: Double? = null, val length: String? = null, val region: String? = null, val rankBy: String? = null, val myCoursesOnly: Boolean = false)`
    - mood, length, rankBy는 영어 enum 값이다.
  - `fun VoiceQuery.isEmpty(): Boolean`
  - `fun nearRangeFor(km: Double): NearRange`: 25 이하는 KM25, 50 이하는 KM50, 그 밖은 KM75
  - `data class VoiceHits(val total: Int, val top: List<Found>, val positionUnknown: Boolean)`
  - `fun findForVoice(courses: List<Course>, query: VoiceQuery, here: GeoPoint?, mine: Set<String>, metersOf: (Course) -> Double, wordsOf: (Course) -> List<String>): VoiceHits`
    - `myCoursesOnly`면 `mine`(코스 id)만 남긴다.
    - `rankBy`가 있으면 더미를 빼고 `rank` 순서로 정렬한다.
    - 없으면 실제 코스가 먼저, 더미가 나중이다. 각각 안에서는 위치를 알 때 가까운 순, 모르면 원래 순서다.
    - `top`은 최대 3개다.
  - `sealed interface NameMatch { data class One(val course: Course) : NameMatch; data class Many(val candidates: List<Course>) : NameMatch; data object None : NameMatch }`
  - `fun matchCourse(courses: List<Course>, name: String, wordsOf: (Course) -> List<String>): NameMatch`
    - 단계 1~3을 차례로 시도한다. 1) 공백과 대소문자를 무시하고 이름이 같다, 2) 이름에 그 말이 들어 있다, 3) 모든 낱말이 이름, 지역, `wordsOf`에 들어 있다.
    - 처음 결과가 나온 단계에서 멈춘다.
    - Many 후보는 최대 3개다.
    - 순위로 고르기(설계 3-2의 4단계)는 Task 3이 한다.

- [ ] **Step 1: Write the failing tests**
  - 픽스처는 `CourseFilterTest`의 `course(...)` 도우미를 그대로 옮긴다.
  - 시험 이름과 확인할 것:
    - `realBeforeDummyThenNearest`: 실제 2개(40km, 10km)와 더미 1개(1km). `top` id가 `[real10, real40, dummy1]`이다.
    - `topIsAtMostThree`: 5개가 맞으면 `total == 5`, `top.size == 3`이다.
    - `rankByDropsDummiesAndUsesRankOrder`: `rankBy = "length"`면 더미가 없고 `rank(real, LENGTH)` 순서와 같다.
    - `nearbyRoundsUpToChip`: `nearRangeFor(10.0) == KM25`, `nearRangeFor(30.0) == KM50`, `nearRangeFor(200.0) == KM75`
    - `nearbyWithoutPositionIsSkippedAndFlagged`: here가 null이면 `positionUnknown == true`이고 결과가 걸러지지 않는다.
    - `myCoursesOnlyKeepsMine`: `mine = setOf("b")`면 `["b"]`만 남는다.
    - `moodEnumFilters`: `mood = "sunset"`이면 `times:sunset` 태그 코스만 남는다.
    - `exactNameIgnoresSpacesAndCase`: "팔당노을"이 "팔당 노을" 하나로 정해진다. "팔당 노을"과 "팔당 노을 긴 길"이 함께 있어도 One이다.
    - `ambiguousNameAsks`: "노을"이 이름에 든 코스가 4개면 Many이고 후보는 3개다.
    - `unknownNameIsNone`: "없는길"은 None이다.
    - `wordsMatchRegionAndTags`: "제주 바다"가 region 제주와 wordsOf "바다"인 코스 하나에 맞는다.

- [ ] **Step 2: Run tests to verify they fail**
  - `./gradlew --no-watch-fs -q :core:test --tests 'com.byway.core.gleo.VoiceSearchTest'` → FAIL(컴파일 오류)

- [ ] **Step 3: Implement the Produces block in `VoiceSearch.kt`**
  - 거르기는 `filterCourses`에 `CourseFilter(query, near, mood, length, region)`를 넘겨 재사용한다.
  - mood, length, rankBy 값이 enum에 없으면 그 조건만 무시한다.

- [ ] **Step 4: Run tests to verify they pass**
  - 같은 명령 → PASS(11개)

- [ ] **Step 5: Commit**: `feat: 음성용 코스 찾기와 이름 맞추기(core)`

### Task 3: 함수별 판단 (core)

**Files:**
- Create: `core/src/main/kotlin/com/byway/core/gleo/GleoDecide.kt`
- Test: `core/src/test/kotlin/com/byway/core/gleo/GleoDecideTest.kt`

**Interfaces:**
- Consumes: Task 1의 `GleoReply`, `GleoNames`, `COMMANDS`, Task 2의 `VoiceQuery`, `findForVoice`, `matchCourse`, 기존 `Prompt`
- Produces:
  - `data class GleoWorld(val courses: List<Course>, val here: GeoPoint?, val mine: Set<String>, val running: Course?, val sectionIndex: Int, val remainingMeters: Double?, val prompt: Prompt?, val recording: Boolean, val partyCode: String?, val savedRun: Pair<String, Int>?, val lastResults: List<String>, val metersOf: (Course) -> Double, val wordsOf: (Course) -> List<String>, val moodNames: (Course) -> List<String>)`
    - `savedRun`은 courseId와 progress다.
    - `lastResults`는 직전 찾기 top의 id다. 10분이 지났으면 앱이 빈 목록을 넘긴다.
  - `sealed interface GleoEffect`:
    - `None`
    - `Remember(val ids: List<String>)`
    - `Start(val courseId: String, val resumeFrom: Int)`
    - `Answer(val yes: Boolean)`
    - `EndCourse`
    - `SetVoice(val on: Boolean)`
    - `SetCabin(val on: Boolean)`
    - `StopRecording`
    - `StopSharing(val code: String)`
  - `data class GleoOutcome(val reply: GleoReply, val effect: GleoEffect)`
  - `fun decideGleo(function: String, params: Map<String, JsonPrimitive>, world: GleoWorld): GleoOutcome`
    - 모르는 함수는 `fail`이다.
    - 영어 `message` 문구는 구현자가 정한다. 문장 하나에 예시 없이 쓰고, 고유명은 한국어로 둔다.
  - `fun nextSpot(course: Course, here: GeoPoint?, sectionIndex: Int): Spot?`
    - 기준점은 here에 가장 가까운 path 번호다. here가 없으면 `sections[sectionIndex].startIndex`다.
    - 기준점보다 `pathIndex`가 큰 spot 중 첫 번째를 돌려준다.
- **`data` 키(고정)**
  - FIND 항목: `rank`, `name`, `region`, `km`(소수 1자리), `awayKm`(알 때만), `moods`(`moodNames` 결과), `dummy`
  - START 성공: `name`, `km`, `sections`, `firstSection`
  - status: `course`, `remainingKm`, `section`(예: "2/3"), `sectionName`, `nextSectionName`(있을 때)
  - next_spot: `name`, `kind`, `km`(남은 거리)

- [ ] **Step 1: Write the failing tests**
  - 시험 이름과 확인할 것:
    - `findEmptyAsks`: 인자가 없으면 ASKING이다.
    - `findRemembersTopIds`: effect가 `Remember(top ids)`이고 data는 3개 이하다.
    - `findNothingIsSuccess`: 0개면 SUCCESS이고 data가 비어 있다.
    - `startByNameWhileMovingStarts`: running이 null이면 `Start(id, 0)`이고 SUCCESS, SILENT다.
    - `secondStartWhileRunningFails`: running이 있으면 FAIL이고 effect는 None이다.
    - `startAmbiguousAsks`: ASKING이고 data 후보는 3개 이하다.
    - `startByRankUsesLastResults`: `resultRank = 2`, `lastResults = [a, b]`면 `Start(b, 0)`이다.
    - `startRankWithoutResultsAsks`: `resultRank`만 있고 `lastResults`가 비었으면 ASKING이다.
    - `resumeLastRunStartsFromProgress`: `savedRun = ("c", 40)`이면 `Start("c", 40)`이다.
    - `resumeWithoutSavedRunFails`
    - `nameNotFoundFallsBackToLastResults`: 이름이 None인데 `lastResults`가 있으면 ASKING이고 data는 직전 결과다(설계: 영어 이름이 와도 되묻기).
    - `stopByAnswersDetour`: prompt가 DETOUR면 `Answer(true)`, `back_to_course`면 `Answer(false)`다.
    - `stopByWithoutPromptFails`
    - `resumeCommandNeedsResumePrompt`
    - `controlNeedsRunningCourse`: running이 null이면 `status`, `next_spot`, `end_course`, `stop_by`가 모두 FAIL이다.
    - `statusReportsSection`: data에 remainingKm, section "2/3"이 있다.
    - `nextSpotIsFirstAhead`: spots의 pathIndex가 [5, 20, 40]이고 here가 path[25]에 가장 가까우면 pathIndex 40인 spot이다.
    - `settingsWorkWithoutCourse`: `voice_guide_off`는 `SetVoice(false)`, `cabin_effects_off`는 `SetCabin(false)`다. cabin 응답 message에 "next course"가 들어간다.
    - `stopRecordingNeedsRecording`
    - `stopSharingNeedsParty`: partyCode가 null이면 FAIL, 있으면 `StopSharing(code)`다.
    - `unknownCommandFails`

- [ ] **Step 2: Run tests to verify they fail**
  - `./gradlew --no-watch-fs -q :core:test --tests 'com.byway.core.gleo.GleoDecideTest'` → FAIL(컴파일 오류)

- [ ] **Step 3: Implement `decideGleo` and `nextSpot` in `GleoDecide.kt`**
  - 출발 판단은 `courseName` → `matchCourse` 순서다.
  - One이 아니고 `resultRank`가 있으면 `lastResults`에서 그 순위를 고른다(1부터 센다).
  - 그래도 없으면 설계 3-2대로 ASKING이나 FAIL이다.

- [ ] **Step 4: Run tests and the whole core suite**
  - `./gradlew --no-watch-fs -q :core:test` → PASS(기존 297 + 새 것)

- [ ] **Step 5: Commit**: `feat: Gleo 함수별 판단 - 찾기·출발(주행 중 허용, 달리는 중 거절)·에움길 조작(core)`

### Task 4: 스키마, 수신기, Callback (app)

**Files:**
- Create:
  - `app/src/main/assets/schema.json`
  - `app/src/main/java/com/byway/platform/PleosGleo.kt`
  - `app/src/main/java/com/byway/service/GleoReceiver.kt`
  - `app/src/main/java/com/byway/service/GleoMemory.kt`
- Modify:
  - `app/src/main/AndroidManifest.xml`: `<receiver android:name=".service.GleoReceiver" android:exported="true">`와 intent-filter action 3개를 단다.
  - `app/src/main/java/com/byway/ui/BrowseCopy.kt`: `fun searchWords(course: Course): List<String>`를 만든다. 지금 `BrowseScreen.kt:37`에 있는 람다 내용을 그대로 옮기고, BrowseScreen도 이것을 쓰게 바꾼다.
- Test: `app/src/test/java/com/byway/service/GleoSchemaTest.kt`, `app/src/test/java/com/byway/service/GleoReceiverTest.kt`

**Interfaces:**
- Consumes: Task 1~3 전부, `DriveService.start/answer/stop/recordStop`, `DriveBoard.state`, `CourseCatalog.all`, `CourseStore.files(ctx).mine()`, `MyDrives.voiceOn/ambienceOn/savedRun`, `MyParty.code`, `Share.client(ctx).sharing(code, false)`, `moodLabel`, `searchWords`
- Produces:
  - `object PleosGleo { fun respond(context: Context, reply: GleoReply) }`
    - 디버그 빌드에서는 `Log.i("BywayGleo", json)` 한 줄을 남긴다. 실측과 흉내 앱 확인에 쓴다.
  - `object GleoMemory { fun remember(ids: List<String>, nowMs: Long); fun recall(nowMs: Long): List<String> }`: 10분이 지나면 빈 목록이다.
  - `internal class ReplyOnce(private val send: (GleoReply) -> Unit) { fun reply(r: GleoReply): Boolean }`: 처음 한 번만 보내고, 그 뒤 호출은 false를 돌려준다.
  - `GleoReceiver.onReceive` 처리 순서:
    1. `goAsync()`
    2. 배경 스레드에서 `GleoWorld`를 만든다.
       - 거리는 `CourseBook`이 쓰는 것과 같은 값이다(`CourseBook.kt`의 `measured`를 만드는 식을 따른다).
       - `wordsOf = ::searchWords`
       - `moodNames`는 `moodWords(tags)`다.
    3. `decideGleo`
    4. effect를 실행한다.
       - `Start`이면 `DriveService.start`를 부른다. `ForegroundServiceStartNotAllowedException`(API 31+)이 나면 reply를 FAIL "Open 에움길 on the screen and try again."으로 바꾼다.
       - `StopSharing`은 배경에서 `sharing(code, false)`를 부르고, 결과에 따라 SUCCESS나 FAIL로 답한다. 서버 주소가 없는 빌드(`Share.available == false`)라면 FAIL이다.
    5. `ReplyOnce`로 답한다.
    6. 6초 타이머가 먼저 끝나면 timeout FAIL로 답한다. 늦게 끝난 결과는 effect를 실행하지 않고 버린다.
    7. `finish()`

- [ ] **Step 1: Write the failing tests**
  - `GleoSchemaTest`
    - `schemaFunctionsMatchCodeAndManifest`
      - `File("src/main/assets/schema.json")`을 파싱한다.
      - `actionCalls` 함수 이름 집합이 `GleoNames.ALL`과 같다.
      - `actionCalls`는 3개 이하다.
      - 모든 `component`가 `"service"`다.
      - `packageName`이 `"com.byway"`다.
      - `File("src/main/AndroidManifest.xml")` 문자열에 각 이름의 `<action android:name="..."/>`가 있다.
    - `schemaEnumsMatchCore`
      - mood enum이 `MOOD_ENUM.keys`와 같다.
      - lengthBand enum이 `LENGTH_ENUM.keys`, rankBy enum이 `RANK_ENUM.keys`, command enum이 `COMMANDS`와 같다.
      - region enum이 `["강원","경상","남해안","수도권","전라","제주","충청"]`이다.
    - `schemaDescriptionsAreEnglish`: 모든 description에 한글 음절(가-힣)이 없다.
  - `GleoReceiverTest`
    - `replyOnce`: 두 번 부르면 send는 1회이고, 두 번째 반환값은 false다.
    - `memoryForgetsAfterTenMinutes`: `remember([a], 0)`이면 `recall(599_999)`는 [a], `recall(600_001)`은 빈 목록이다.

- [ ] **Step 2: Run tests to verify they fail**
  - `./gradlew --no-watch-fs -q :app:testDebugUnitTest --tests 'com.byway.service.Gleo*'` → FAIL

- [ ] **Step 3: Write `schema.json`**
  - 설계 3절의 인자와 enum을 그대로 옮긴다.
  - `invokeWord`는 `"에움길"`, `version`은 `"0.1.0"`이다.
  - `appDescription`과 함수, 인자 설명은 영어 한두 문장이다.
  - `searchTerm`과 `courseName` 설명에는 "as spoken by the user, in the original language"라는 뜻을 넣는다.
  - `FIND`와 `START`는 `required: []`, `CONTROL`은 `required: ["command"]`다.

- [ ] **Step 4: Implement PleosGleo, GleoMemory, ReplyOnce, GleoReceiver, the manifest receiver and `searchWords`**

- [ ] **Step 5: Run tests**
  - `./gradlew --no-watch-fs -q :app:testDebugUnitTest :app:testReleaseUnitTest :core:test` → PASS(기존 app 366 포함)

- [ ] **Step 6: Commit**: `feat: Gleo 스키마·수신기·Callback - 찾기·출발·에움길 조작을 방송으로 받는다`

### Task 5: 에뮬레이터 실측과 설계 문서 기록

**Files:**
- Modify: `docs/superpowers/specs/2026-11-13-gleo-voice-design.md`. 3-2절 "화면 뒤에서 출발할 때"와 8절에 실측 결과를 적는다.

**Interfaces:**
- Consumes: Task 4의 디버그 APK와 logcat 태그 `BywayGleo`

- [ ] **Step 1:** 에뮬레이터 잠금을 잡고(`${TMPDIR}/byway-emulator.lock`) 디버그 APK를 설치한다.
  - 시연 빌드 데이터는 같은 서명이면 유지된다.
  - 아니면 실측이 끝난 뒤 `byway_20261111_8377896.apk`로 되돌린다.
- [ ] **Step 2: 정차 + 앱이 보일 때**
  - `adb shell am broadcast -a FIND_DRIVING_COURSES -p com.byway --es ACTION_PARAM '{"searchTerm":"노을"}'`
  - Expected: logcat `BywayGleo`에 `"status":"success"`가 나오고 data는 3개 이하다.
  - 이어서 `START_DRIVING_COURSE '{"resultRank":1}'`을 보낸다.
  - Expected: success/silent이고, 주행 화면이 나오며, 에움길 출발 안내 음성이 나온다.
- [ ] **Step 3: 같은 START를 바로 한 번 더 보낸다**
  - Expected: `"status":"fail"`이고, 달리던 코스가 그대로다.
- [ ] **Step 4: 기어 D + 이탈 장면에서 답하기**
  - `detour.py`로 이탈을 만들고, "잠깐 들르시나요?"가 나오면 `CONTROL_BYWAY_DRIVE '{"command":"stop_by"}'`를 보낸다.
  - Expected: success이고 복귀 안내가 나오지 않는다.
  - 다시 이탈해 `back_to_course`를 보낸다. Expected: "코스로 돌아가는 길을 안내합니다"가 나온다.
  - `status`와 `next_spot`의 data를 확인한다.
- [ ] **Step 5: 2분할(에움길 가려짐) + 코스 없음에서 START**
  - 결과(출발 또는 FAIL "Open 에움길…")를 설계 3-2에 적는다.
  - 앱을 강제 종료(`am force-stop com.byway`)한 뒤 FIND를 보내 응답 시각을 재고, 8절에 적는다.
- [ ] **Step 6: Commit**: `docs: Gleo 에뮬레이터 실측 - 화면 뒤 출발·첫 응답 시간`

### Task 6: 시연용 Gleo 흉내 앱 (`gleosim`)

**Files:**
- Modify: `settings.gradle.kts`에 `include(":gleosim")`를 더한다.
- Create:
  - `gleosim/build.gradle.kts`: android application, `com.byway.gleosim`, minSdk 28 / target 34, `:core`에 의존하지 않는다, Pleos TextToSpeech 3.3.1, junit
  - `gleosim/src/main/AndroidManifest.xml`: `SYSTEM_ALERT_WINDOW`, Pleos TTS 권한, 런처 Activity 하나(띄우기와 닫기)
  - `gleosim/src/main/java/com/byway/gleosim/Scenarios.kt`
  - `gleosim/src/main/java/com/byway/gleosim/SimReplies.kt`
  - `gleosim/src/main/java/com/byway/gleosim/OverlayService.kt`
  - `gleosim/src/main/java/com/byway/gleosim/SimVoice.kt`
- Test: `gleosim/src/test/java/com/byway/gleosim/ScenariosTest.kt`

**Interfaces:**
- Produces:
  - `data class Scenario(val utterance: String, val function: String, val paramJson: String)`
  - `val SCENARIOS: List<Scenario>`(순서 고정). 각 문장 → 보내는 함수와 인자:

| 문장 | 함수 | ACTION_PARAM |
|---|---|---|
| 노을 보기 좋은 코스 찾아줘 | FIND | `{"mood":"sunset"}` |
| 1번 코스로 출발해줘 | START | `{"resultRank":1}` |
| 잠깐 들를게 | CONTROL | `{"command":"stop_by"}` |
| 코스로 돌아갈래 | CONTROL | `{"command":"back_to_course"}` |
| 얼마나 남았어? | CONTROL | `{"command":"status"}` |
| 다음 볼거리 뭐야? | CONTROL | `{"command":"next_spot"}` |
| 음성 안내 꺼줘 | CONTROL | `{"command":"voice_guide_off"}` |
| 코스 그만할래 | CONTROL | `{"command":"end_course"}` |

  - `fun koreanReply(responseJson: String): String`
    - `status`와 `functionName`, `data`로 한국어 답을 만든다. 예: "노을 코스 3곳을 찾았어요: 팔당 노을, 변산 노을, 애월 노을", "남은 거리는 12.4km, 2/3 구간이에요".
    - FAIL이면 "할 수 없어요: " 뒤에 영어 message를 그대로 붙인다.
- **화면**
  - 오른쪽 아래에 떠 있는 창이다(TYPE_APPLICATION_OVERLAY).
  - 맨 위 줄에 "시연용 Gleo 흉내 · 실제 Gleo 아님"을 늘 보인다.
  - 문장 버튼 목록 아래에 마지막 사용자 말풍선과 답 말풍선이 있다.
  - Gleo 로고, 크롬 애니메이션, 브랜드 색은 쓰지 않는다. 앱 기본 회색과 흰색이다.
- **방송**
  - 버튼을 누르면 `Intent(function).setPackage("com.byway").putExtra("ACTION_PARAM", paramJson)`를 `sendBroadcast`로 보낸다.
  - `APP_ACTION_RESPONSE`는 동적 수신기로 받는다.
    - `packageName == "com.byway"`일 때만 처리한다.
    - mode가 voice일 때만 `SimVoice.say`로 읽는다.
  - 7초 안에 답이 없으면 "응답이 없어요"를 보인다.

- [ ] **Step 1: Write the failing test**
  - `scenariosUseRealFunctionNames`: 모든 `function`이 세 이름 중 하나다. 이름은 문자열로 고정해 확인한다.
  - `scenarioParamsAreJsonObjects`
  - `koreanReplyForFind`: FIND success, data 3개 → "3곳" 문장에 세 이름이 다 들어 있다.
  - `koreanReplyForFail`: "할 수 없어요"로 시작한다.

- [ ] **Step 2: Run the test to verify it fails**
  - `./gradlew --no-watch-fs -q :gleosim:testDebugUnitTest` → FAIL

- [ ] **Step 3: Implement Scenarios, SimReplies, OverlayService, SimVoice and the launcher Activity**

- [ ] **Step 4: Run tests and build**
  - `./gradlew --no-watch-fs -q :gleosim:testDebugUnitTest :gleosim:assembleDebug` → PASS
  - APK `gleosim/build/outputs/apk/debug/gleosim-debug.apk`

- [ ] **Step 5: 에뮬레이터 확인**
  - 설치하고 `adb shell appops set com.byway.gleosim SYSTEM_ALERT_WINDOW allow`를 준다.
  - 3분할 주행 중에 창이 내비 위에 보이는지 확인한다.
  - "노을 보기 좋은 코스 찾아줘" → "1번 코스로 출발해줘" 순서로 누르면, 답 말풍선이 나오고 에움길이 출발해야 한다.

- [ ] **Step 6: Commit**: `feat: 시연용 Gleo 흉내 앱(gleosim) - 같은 규격 방송, 한국어 답 말풍선, 실제 Gleo 아님 표시`

### Task 7: 마무리

- [ ] **Step 1:** 전체 시험을 돌린다.
  - `./gradlew --no-watch-fs -q :core:test :server:test :app:testDebugUnitTest :app:testReleaseUnitTest :gleosim:testDebugUnitTest` → exit 0. 건수를 기록한다.
- [ ] **Step 2:** 시연 APK를 만든다.
  - `assembleRelease -Pbyway.serverUrl=<테일넷>` → `~/serve/byway/downloads/byway_<날짜>_<커밋>.apk`. 저장소 밖이다.
  - 제출용 `apk/` 교체는 PR #14 병합 뒤 따로 한다.
- [ ] **Step 3:** 전체 브랜치 독립 검토를 받는다.
  - 가장 능력 있는 모델로 돌리고, 이 계획의 Review Focus 다섯 줄을 그대로 넘긴다. AAR 금지 조항도 넣는다.
  - Critical과 Important는 각각 RED→GREEN으로 고친다.
- [ ] **Step 4:** PR 보고서를 쓴다. `~/reports/pr_YYYYMMDD_HHMM.md`에 5항목을 담는다.
  - 요약
  - diff
  - gpu-wsl grep 영향 범위
  - 시험 기록. Docker 기록이 없다는 것을 명시한다.
  - 리뷰 요청. 실제 Gleo 미확인 3가지(한국어 인자, 한국어 답, Callback 도착)를 포함한다.
- [ ] **Step 5:** 기획서 세션에 알린다. 내용은 새 클립 장면 두 개(설계 9절)와 흉내 앱 설치 방법이다.
  - 푸시와 PR은 PM이 지시할 때만 한다.
