# 더 많은 코스 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 둘러보기를 "더 많은 코스"로 바꾸고, 지도 없는 더미 코스 150개와 검색·내 주변·무드·거리 걸러 보기를 넣는다. 더미 코스도 출발하면 실제 내비 안내와 주행·요약까지 간다.

**Architecture:**
- **core:** 코스에 `dummy`·`declaredMeters`·`weathers`를 더한다. 더미 파일 읽기(점 → 촘촘한 직선 path), 걸러 보기 순수 함수, 엔진의 더미 분기(지점 = 점 그대로, 이탈 판단 끔, 남은 거리 배율)를 둔다.
- **자료:** `tools/make-dummy-courses.py`가 실제 지명 표에서 150개를 만들어 `app/src/main/assets/dummy/courses.json`에 쓴다.
- **앱:** 코스 책이 더미를 랭킹·자동 추천·홈 띠·파티 고르기에서 뺀다. 지도 그리는 곳(`CoursePathView`, 한국 지도)은 더미에서 지도 대신 표시를 그린다. 둘러보기 화면을 검색 칸 + 걸러 보기 줄 + 재사용 격자(`GridView`)로 바꾼다.

**Tech Stack:** Kotlin 1.9.25, Android View(Compose 아님), kotlinx.serialization, JUnit4, Python 3(자료 생성기).

**Spec:** `docs/superpowers/specs/2026-11-11-more-courses-design.md`(75389b0)

## Global Constraints

- 빌드와 시험은 gpu-wsl `~/projects/byway`에서 `./gradlew --no-watch-fs ...`로 한다.
- 탭 화면은 정차(P) 중에만 보인다. 이 규칙(`screenFor`)은 바꾸지 않는다.
- `ai.pleos.*`, `android.car.*`는 `platform/` 안에서만 쓴다. Pleos AAR·APK를 풀거나 분석하지 않는다(라이선스 3.4).
- 새 라이브러리 의존성을 더하지 않는다(재사용 목록은 프레임워크 `GridView`).
- 주행 화면 규칙: 자동 움직임 없음, 버튼 1개 + 코스 끝내기.
- 문구(그대로 쓴다):
  - 메뉴: "더 많은 코스"
  - 검색 칸 힌트: "코스 이름, 지역, 장소로 찾기"
  - 걸러 보기 버튼: "내 주변", "무드", "거리", "지역", "풍경", 풀기: "모두 풀기"
  - 내 주변 값: "25km", "50km", "75km" (고른 뒤 버튼 글: "25km 안" 등)
  - 거리 값: "30km 이하", "30~60km", "60~100km", "100km 넘게"
  - 무드 값: "혼자", "데이트", "가족", "봄", "여름", "가을", "겨울", "비 오는 날", "일출", "낮", "노을", "밤"
  - 위치 없음: "현재 위치를 아직 모릅니다"
  - 결과 없음: "맞는 코스가 없습니다"
  - 지도 자리: "지도 없음 · 더미 데이터입니다"
  - 한국 지도에서 더미를 골랐을 때: "더미 데이터입니다 · 경로선이 없습니다"
  - 더미 표시 낱말: "더미"
  - 측정값 없음: "—"
  - 카드에 내 주변 거리: "12km 거리"
- 더미 지역 나눔: 수도권 30 · 강원 25 · 충청 20 · 경상 25 · 전라 20 · 남해안 15 · 제주 15 = 150.
- 거리 구간 경계: 30km 이하는 `<= 30_000`, 30~60은 `> 30_000 && <= 60_000`, 60~100은 `> 60_000 && <= 100_000`, 100 넘게는 `> 100_000`.

## Review Focus

1. 검색 칸에 글을 넣을 때마다 화면 전체를 다시 만들면 키보드와 초점이 사라진다 → 검색은 화면을 다시 만들지 않고 목록·수·지도만 갈아 끼운다(Task 7 수동 확인 + `searchKeepsScreen` 성격의 에뮬레이터 확인).
2. 내 주변을 켠 채 위치가 1초마다 오면 화면이 1초마다 다시 만들어져 깜박인다 → 처음 받았을 때와 1km 넘게 움직였을 때만 알린다(Task 7 `BrowseLocationTest`).
3. 더미로 출발했는데 차가 출발 지점에서 멀리 있다 → 엔진이 "출발 지점으로" 안내 대신 바로 코스 위로 보고 첫 지점들을 내비에 올린다(Task 3 시험).
4. 더미 코스의 구간 이름과 내비 지점 이름이 비면 주행 화면에 빈 줄이 나온다 → 읽기 시험이 모든 구간 이름이 비지 않음을 본다(Task 1).
5. 165장을 한 번에 만들어 둘러보기가 늦게 뜬다 → `GridView` 재사용 + 에뮬레이터에서 칩 누름 → 화면 바뀜 시간을 잰다(Task 8).

---

### Task 1: core 코스 모델과 더미 읽기

**Files:**
- Modify: `core/src/main/kotlin/com/byway/core/course/Course.kt` (Course, CourseTags)
- Modify: `core/src/main/kotlin/com/byway/core/course/CourseFile.kt` (TagsFile에 weathers, 읽기·쓰기)
- Create: `core/src/main/kotlin/com/byway/core/course/DummyCourses.kt`
- Modify: `core/src/main/kotlin/com/byway/core/course/CourseFacts.kt` (`factsOf` 거리)
- Test: `core/src/test/kotlin/com/byway/core/course/DummyCoursesTest.kt`, `CourseFileTest.kt`

**Interfaces:**
- Produces:
  - `Course.dummy: Boolean = false`, `Course.declaredMeters: Double? = null`
  - `CourseTags.weathers: List<String> = emptyList()` (값 `"rain"`)
  - `fun parseDummyCourses(text: String): List<Course>`
  - `fun Course.lengthScale(): Double` — `declaredMeters / 직선 path 길이`, 실제 코스는 1.0
  - `const val DUMMY_STEP_METERS = 200.0`
- 더미 파일 형식(JSON 배열): `{"id","name","region","summary","km", "points":[{"name","lat","lon"}...], "tags":{themes,moods,times,seasons,weathers}}`

- [ ] **Step 1: 실패하는 시험** `DummyCoursesTest`:
  - `pointsBecomeDenseStraightPath`: 점 3개(서울시청 37.5663,126.9779 → 37.5663,127.0279 → 37.6163,127.0279) 입력 → `path.first()`=첫 점, `path.last()`=끝 점, 이웃 간격 모두 `<= 200.0 + 1e-6`, `dummy == true`, `declaredMeters == km*1000`.
  - `oneSectionPerLegNamedByItsEnd`: 위 입력 → `sections.map { it.name } == listOf(둘째 점 이름, 셋째 점 이름)`, 구간이 path 전체를 빈틈없이 덮음, 이름 비지 않음.
  - `lengthScaleStretchesToDeclared`: `km` 을 직선 길이의 1.5배로 → `lengthScale()` ≈ 1.5(±0.01). 실제 코스(`dummy=false`)는 1.0.
  - `factsUseDeclaredMeters`: `factsOf(dummy).meters == declaredMeters`.
  - `CourseFileTest.weathersRoundTrip`: `weathers=["rain"]` 코스를 `writeCourse` → `parseCourse` → 같은 값.
- [ ] **Step 2:** `./gradlew --no-watch-fs :core:test --tests '*DummyCoursesTest*' --tests '*CourseFileTest*'` → FAIL(심볼 없음).
- [ ] **Step 3:** 구현. 더미의 `attribution`은 `""`, `toleranceMeters`는 null, `elevation` 빈 목록. 구간 `startIndex`는 이전 구간 `endIndex+1`, 각 구간 끝은 그 다리의 도착 점 인덱스.
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test` → 전부 PASS.
- [ ] **Step 5:** 커밋 `feat: 더미 코스 모델과 읽기 - 점을 직선 path로, 구간은 다리마다, 표기 거리`.

### Task 2: core 걸러 보기

**Files:**
- Create: `core/src/main/kotlin/com/byway/core/course/CourseFilter.kt`
- Test: `core/src/test/kotlin/com/byway/core/course/CourseFilterTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  enum class LengthBand { UP_TO_30, UP_TO_60, UP_TO_100, OVER_100 }
  fun bandOf(meters: Double): LengthBand
  enum class NearRange(val km: Int) { KM25(25), KM50(50), KM75(75) }
  /** 무드 키. "kind:value" 형식: moods:alone, seasons:spring, weathers:rain, times:sunset 등 */
  val MOOD_KEYS: List<String>  // 12개, 화면 순서: alone,date,family / spring,summer,autumn,winter / rain / sunrise,day,sunset,night
  data class CourseFilter(val query: String = "", val near: NearRange? = null, val mood: String? = null,
                          val length: LengthBand? = null, val region: String? = null, val theme: String? = null)
  data class Found(val course: Course, val awayMeters: Double?)
  fun filterCourses(courses: List<Course>, filter: CourseFilter, here: GeoPoint?, metersOf: (Course) -> Double,
                    wordsOf: (Course) -> List<String>): List<Found>
  fun CourseFilter.isEmpty(): Boolean
  ```
- 규칙: 모든 조건 AND. `near`가 있고 `here == null`이면 near 조건은 무시(화면이 칩을 흐리게 둔다). `near`가 있고 `here`가 있으면 `path.first()`까지 직선거리 `<= km*1000`만, 가까운 순 정렬, `awayMeters` 채움. 그 밖에는 입력 순서 유지, `awayMeters = here?.let { 거리 }`. 검색: `query.trim()`을 공백으로 나눈 낱말 모두가 (이름, 지역, summary, spot 이름, section 이름, `wordsOf(course)`) 이어 붙인 글에 대소문자 무시로 들어 있어야 맞음.

- [ ] **Step 1: 실패하는 시험** `CourseFilterTest`:
  - `bandEdges`: `bandOf(30_000.0)==UP_TO_30`, `bandOf(30_000.1)==UP_TO_60`, `bandOf(60_000.0)==UP_TO_60`, `bandOf(100_000.0)==UP_TO_100`, `bandOf(100_000.1)==OVER_100`.
  - `nearKeepsWithinAndSortsByDistance`: 서울(37.5663,126.9779) 기준 출발점 10km·40km·70km 코스 → `KM25` 1개, `KM50` 2개(10km 먼저), `KM75` 3개, `awayMeters` 채움.
  - `nearIgnoredWithoutPosition`: `here=null`, `near=KM25` → 전부.
  - `moodMatchesEachKind`: `"seasons:winter"`, `"weathers:rain"`, `"times:sunset"`, `"moods:family"` 각각 해당 태그 코스만.
  - `queryNeedsEveryWord`: 이름 "가평 북한강 노을길", 지역 "수도권" → `"가평 노을"` 맞음, `"가평 바다"` 안 맞음, `"  "` 전부 맞음, 대소문자 무시(`"ABC"` vs 이름 `"abc 길"`).
  - `queryMatchesWordsOf`: `wordsOf = { listOf("비 오는 날") }` → `"비 오는"` 맞음.
  - `filtersCombineWithAnd`: region + length + mood 동시에.
  - `moodKeysAreTwelve`: `MOOD_KEYS.size == 12`, 첫 값 `"moods:alone"`, `"weathers:rain"` 포함.
- [ ] **Step 2:** `./gradlew --no-watch-fs :core:test --tests '*CourseFilterTest*'` → FAIL.
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test` → PASS.
- [ ] **Step 5:** 커밋 `feat: 코스 걸러 보기 - 검색, 내 주변, 무드, 거리, 지역, 풍경`.

### Task 3: core 엔진의 더미 분기

**Files:**
- Modify: `core/src/main/kotlin/com/byway/core/engine/LegPlanner.kt` (`planStops`)
- Modify: `core/src/main/kotlin/com/byway/core/engine/CourseEngine.kt` (`strayLimit`, `report()`)
- Test: `core/src/test/kotlin/com/byway/core/engine/DummyDriveTest.kt`

**Interfaces:**
- Consumes: Task 1 `parseDummyCourses`, `lengthScale()`
- Produces: 더미에서 `planStops(course, tol)` = 각 구간 `endIndex`의 `Stop(section.name, path[endIndex], endIndex)`(단순화 없음). 더미에서 `strayLimit = Double.MAX_VALUE`. `ShowProgress.remainingMeters`에 `course.lengthScale()`을 곱한다.

- [ ] **Step 1: 실패하는 시험** `DummyDriveTest` (기존 엔진 시험의 사건 넣는 도우미를 그대로 쓴다):
  - `stopsAreThePointsThemselves`: 점 4개 더미 → `planStops`가 점 2·3·4와 같은 좌표, 이름은 구간 이름.
  - `noStrayTalkFarFromStraightLine`: 출발 뒤 직선에서 5km 떨어진 위치를 30번 넣어도 `Say` 중 이탈·복귀 문구(`Messages.ASK_*`, `Messages.REJOIN*`, `Messages.FREE`)가 없다.
  - `farFromStartStillStartsOnCourse`: 출발 지점에서 40km 떨어진 위치로 시작 → 첫 명령에 `Messages.TO_START`가 없고 `FollowRoute`의 목적지가 더미 점 중 하나.
  - `remainingIsScaledToDeclared`: 첫 `ShowProgress.remainingMeters` ≈ `declaredMeters`(±1%).
  - `finishesNearLastPoint`: 끝 점 근처 위치 + 정차 → 끝 사건(기존 끝 판단과 같은 명령).
- [ ] **Step 2:** `./gradlew --no-watch-fs :core:test --tests '*DummyDriveTest*'` → FAIL.
- [ ] **Step 3:** 구현. 실제 코스 동작은 그대로(기존 엔진 시험이 지킨다).
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test` → PASS.
- [ ] **Step 5:** 커밋 `feat: 더미 코스 주행 - 점을 그대로 내비 지점으로, 이탈 안내 끔, 남은 거리 표기 거리 배율`.

### Task 4: 더미 자료 150개와 비 오는 날 태그

**Files:**
- Create: `tools/make-dummy-courses.py` (실제 지명·좌표 표 → 150개, 고정 시드)
- Create: `app/src/main/assets/dummy/courses.json`
- Modify: 실제 코스 JSON 몇 개 `tags.weathers = ["rain"]` (숲길·호수·고갯길: `gonjiam-forest`, `halla-forest`, `cheongpung-lake`, `baegun-lake`, `manhang-mountain`)
- Test: `core/src/test/kotlin/com/byway/core/course/DummyAssetTest.kt`

**Interfaces:**
- Consumes: Task 1 형식, Task 2 `MOOD_KEYS`, `bandOf`
- 생성 규칙: 지역마다 지명 표(이름, 위도, 경도, 풍경 키). 코스 = 출발 + 가까운 지명 1~2곳 + 도착, 이웃 점 사이 직선 5~40km. `km` = 직선 합 × 1.3 반올림. 출발 지점이 지역 안에서 고르게 퍼지게 같은 출발 지명은 두 번 넘게 쓰지 않는다. 이름은 "출발지명–도착지명 + 풍경 낱말"류로 짓고 실제 코스 이름과 겹치지 않게. 태그는 시드 고정 난수로 고르게.

- [ ] **Step 1: 실패하는 시험** `DummyAssetTest` (`File("../app/src/main/assets/dummy/courses.json")`):
  - `hundredFiftyByRegion`: 총 150, 지역별 30/25/20/25/20/15/15.
  - `idsUniqueAndPrefixed`: 모두 `dummy-`로 시작, 겹침 없음, 실제 코스 id와도 안 겹침.
  - `pointsInKorea`: 모든 점 위도 33.0~38.7, 경도 124.5~131.0, 코스마다 점 3개 이상.
  - `tagsKnown`: themes ⊂ {river,forest,mountain,lake,city,coast}, moods ⊂ {alone,date,family}, times ⊂ {sunrise,day,sunset,night}, seasons ⊂ {spring,summer,autumn,winter}, weathers ⊂ {rain}.
  - `everyMoodAndBandHasCourses`: `MOOD_KEYS` 각각 더미 5개 이상, `LengthBand` 각각 10개 이상.
  - `rainInEveryRegion`: 지역마다 rain 1개 이상.
  - `namesNotEmpty`: 코스 이름·summary·점 이름 비지 않음.
  - `realCoursesHaveSomeRain`: 실제 코스 중 rain 태그 3개 이상.
- [ ] **Step 2:** `./gradlew --no-watch-fs :core:test --tests '*DummyAssetTest*'` → FAIL(파일 없음).
- [ ] **Step 3:** 생성기를 쓰고 `python3 tools/make-dummy-courses.py > app/src/main/assets/dummy/courses.json`, 실제 코스 5개에 rain 태그.
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test` → PASS.
- [ ] **Step 5:** 커밋 `feat: 지역별 더미 코스 150개와 비 오는 날 태그`.

### Task 5: 앱 코스 목록에 더미 넣고 빼야 할 곳에서 빼기

**Files:**
- Modify: `app/src/main/java/com/byway/service/CourseCatalog.kt` (assets 뒤에 `dummy/courses.json`)
- Modify: `app/src/main/java/com/byway/ui/CourseBook.kt` (`measured`는 전체, `ranked`·`rivals`·`suggested`는 더미 제외, `featured(currentId)`, `sectionMeters`에 `lengthScale()`)
- Modify: `app/src/main/java/com/byway/ui/CourseStrip.kt`, `CourseDrawer.kt` (`book.featured(chosen)`)
- Modify: `app/src/main/java/com/byway/ui/party/PartyCopy.kt` (`partyCourseGroups`에서 더미 제외)
- Modify: `app/src/main/java/com/byway/ui/AboutScreen.kt` 또는 `creditLines` (빈 출처 줄 빼기)
- Test: `app/src/test/java/com/byway/ui/CourseBookDummyTest.kt`, `PartyCopyTest.kt`

**Interfaces:**
- Consumes: Task 1
- Produces: `fun CourseBook.featured(currentId: String?): List<Course>` = 실제 코스 + (currentId가 더미면 그 코스 하나 맨 앞)

- [ ] **Step 1: 실패하는 시험**:
  - `rankingLeavesDummiesOut`: 실제 2 + 더미 1 책 → `ranked(CURVES)`에 더미 없음, `facts(더미).meters == declaredMeters`.
  - `suggestionNeverDummy`: 모든 시각 0..23에서 `suggested(hour).dummy == false`.
  - `featuredAddsOnlyChosenDummy`: `featured(null)` = 실제만, `featured(더미id)` = 더미 + 실제.
  - `sectionMetersSumToDeclared`: 더미 `sectionMeters` 합 ≈ `declaredMeters`(±1%).
  - `PartyCopyTest.dummiesAreNotPartyChoices`: `partyCourseGroups`의 어느 묶음에도 더미 id 없음.
- [ ] **Step 2:** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*CourseBookDummyTest*' --tests '*PartyCopyTest*'` → FAIL.
- [ ] **Step 3:** 구현.
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test :app:testDebugUnitTest` → PASS.
- [ ] **Step 5:** 커밋 `feat: 더미 코스를 목록에 넣고 랭킹·추천·홈 띠·파티 고르기에서 뺀다`.

### Task 6: 지도 자리 표시와 더미 문구

**Files:**
- Create: `app/src/main/java/com/byway/ui/DummyCopy.kt`
- Modify: `CoursePathView.kt` (더미면 선·위치 점·핀 대신 옅은 바탕 + 가운데 `NO_MAP`), `AtlasView.kt` (더미는 선 안 그림, 핀 반지름 18→10, `setNote(String?)`), `BrowseScreen.kt` 카드 줄, `HomeCopy.kt`/`HomeScreen.kt`·`SummaryScreen.kt`·`MineScreen.kt` 측정값, `DriveScreen.kt` 장소 목록
- Test: `app/src/test/java/com/byway/ui/DummyCopyTest.kt`

**Interfaces:**
- Produces (`DummyCopy.kt`):
  ```kotlin
  const val NO_MAP = "지도 없음 · 더미 데이터입니다"
  const val ATLAS_DUMMY_NOTE = "더미 데이터입니다 · 경로선이 없습니다"
  const val DUMMY_WORD = "더미"
  fun measured(course: Course, text: String): String   // 더미면 "—"
  fun withDummyMark(course: Course, name: String): String  // 더미면 "더미 · $name"
  fun dummyPlaces(course: Course, sectionMeters: List<Double>): List<ScenePlace>  // 구간마다 하나, no=1.., name=구간 이름, kind="stop", label= 마지막은 "도착" 나머지는 "경유", note="", km=누적 km, x=0f, y=0f
  ```
- 쓰는 곳: 카드 글 줄 앞 `더미 · `, 홈·요약의 커브·운전 즐거움·도심 밖·연료량 칸 `measured`, 내 코스 줄의 "커브 N개" `measured`, 홈·요약·주행 코스 이름 `withDummyMark`. 주행 화면은 더미면 `places = dummyPlaces(course, sectionMeters)`, `aheadTitle`은 "남은 지점", 위치 점 그리지 않음(`CoursePathView`가 더미에서 `moveTo`를 무시).

- [ ] **Step 1: 실패하는 시험** `DummyCopyTest`: `measured(더미,"12")=="—"`, `measured(실제,"12")=="12"`, `withDummyMark(더미,"A")=="더미 · A"`, `dummyPlaces` 3구간(10/20/30km) → km `[10.0, 30.0, 60.0]`, label `["경유","경유","도착"]`, 이름 = 구간 이름.
- [ ] **Step 2:** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*DummyCopyTest*'` → FAIL.
- [ ] **Step 3:** 구현하고 위 쓰는 곳에 붙인다.
- [ ] **Step 4:** `./gradlew --no-watch-fs :app:testDebugUnitTest` → PASS.
- [ ] **Step 5:** 커밋 `feat: 더미 코스의 지도 자리 표시, 더미 낱말, 측정값 —, 주행 화면 남은 지점`.

### Task 7: 더 많은 코스 화면

**Files:**
- Modify: `app/src/main/java/com/byway/ui/Screen.kt` (`Tab.BROWSE("더 많은 코스")`, `Nav`에 `filter: CourseFilter = CourseFilter()`·`openFilter: String? = null`, `region`·`theme`는 `filter`로 옮김)
- Create: `app/src/main/java/com/byway/ui/BrowseFilterBar.kt` (걸러 보기 줄 + 펼친 칩 줄 + 검색 칸)
- Create: `app/src/main/java/com/byway/ui/BrowseCopy.kt` (값 문구, 버튼 글)
- Create: `app/src/main/java/com/byway/service/BrowseLocation.kt`
- Modify: `app/src/main/java/com/byway/ui/BrowseScreen.kt` (GridView 재사용 격자, 검색은 그 자리 갱신, 결과 없음, 지도 갱신)
- Modify: `app/src/main/java/com/byway/ui/MainActivity.kt` (탭이 BROWSE이고 정차 화면일 때 `BrowseLocation.start`, 그 밖과 `onStop`에서 `stop`; debug 빌드 `--es query <글>`로 검색어 넣기)
- Modify: `MineScreen.kt` "코스 둘러보기" → "더 많은 코스 보기"
- Test: `app/src/test/java/com/byway/ui/ScreenForTest.kt`, `NavTest.kt`, `BrowseCopyTest.kt`, `app/src/test/java/com/byway/service/BrowseLocationTest.kt`

**Interfaces:**
- Consumes: Task 2 `CourseFilter`, `filterCourses`, `MOOD_KEYS`, `LengthBand`, `NearRange`; Task 6 `withDummyMark`
- Produces:
  ```kotlin
  // BrowseCopy.kt
  fun moodLabel(key: String): String          // MOOD_KEYS → 문구 12개
  fun lengthLabel(band: LengthBand): String
  fun nearLabel(range: NearRange): String      // "25km"
  fun filterButtonText(kind: String, filter: CourseFilter): String  // kind: near|mood|length|region|theme; 값 없으면 버튼 이름, 있으면 값(near는 "25km 안")
  fun awayText(meters: Double): String         // "12km 거리"
  // BrowseLocation.kt (service, 위치는 platform.PleosLocation 사용)
  object BrowseLocation { var last: GeoPoint?; fun start(context: Context, onMoved: () -> Unit); fun stop()
      internal fun shouldTell(before: GeoPoint?, now: GeoPoint): Boolean }  // 처음이거나 1km 넘게 움직였을 때 true
  ```
- 동작: 칩·걸러 보기 버튼은 지금처럼 `go(nav)`(화면 다시 만듦). 검색 글자 변경은 `update(nav)` + 목록 어댑터·"코스 N개"·지도(`atlas.show`)만 바꿈. `toTab`은 `filter`를 지우지 않고 `openFilter`는 지운다. `BrowseLocation`의 `onMoved`는 `filter.near != null`일 때만 화면을 다시 만든다. `here` = `BrowseLocation.last ?: DriveBoard.state.position`.

- [ ] **Step 1: 실패하는 시험**:
  - `ScreenForTest.menuHasFiveEntries`를 `listOf("홈", "더 많은 코스", "랭킹", "내 코스", "함께달리기")`로.
  - `NavTest.toTabKeepsFilterButClosesOpenRow`: `Nav(filter=CourseFilter(query="노을", mood="times:sunset"), openFilter="mood").toTab(Tab.HOME)` → filter 그대로, openFilter null.
  - `BrowseCopyTest`: `moodLabel("weathers:rain")=="비 오는 날"`, `moodLabel("times:sunset")=="노을"`, `MOOD_KEYS.map(::moodLabel)`가 Global Constraints의 무드 12개와 같은 순서, `lengthLabel(UP_TO_60)=="30~60km"`, `filterButtonText("near", CourseFilter(near=KM50))=="50km 안"`, `filterButtonText("mood", CourseFilter())=="무드"`, `awayText(12_400.0)=="12km 거리"`.
  - `BrowseLocationTest.shouldTell`: `(null, p)` true, `(p, p+500m)` false, `(p, p+1.2km)` true.
  - `ShareCopyTest`의 `"둘러보기" in scopeHint("public")` → `"더 많은 코스"`(ShareCopy 문구도 바꿈).
- [ ] **Step 2:** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*ScreenForTest*' --tests '*NavTest*' --tests '*BrowseCopyTest*' --tests '*BrowseLocationTest*' --tests '*ShareCopyTest*'` → FAIL.
- [ ] **Step 3:** 구현. 1/3 창은 같은 구성을 위 지도 아래 글 칸에 둔다. `ShareScreen`의 "둘러보기의 '코드로 받기'" 문구도 "더 많은 코스의 '코드로 받기'"로.
- [ ] **Step 4:** `./gradlew --no-watch-fs :core:test :server:test :app:testDebugUnitTest :app:testReleaseUnitTest` → PASS. `./gradlew --no-watch-fs :app:assembleDebug` 성공.
- [ ] **Step 5:** 커밋 `feat: 더 많은 코스 - 검색 칸, 걸러 보기 줄, 재사용 격자, 화면이 보이는 동안만 위치`.

### Task 8: 에뮬레이터 확인과 보기 페이지

**Files:** 저장소 밖(맥 에뮬레이터, gpu-wsl 보기 페이지). 고친 것이 생기면 해당 Task 파일.

- [ ] **Step 1:** 맥 에뮬레이터 기동(`tools/emulator-lock.sh` 규칙), APK 설치, 2/3 창 낮에서: 메뉴 이름, 걸러 보기 각 줄 펼침, 무드 "비 오는 날", 거리 "100km 넘게", 지역+무드 겹침, debug `--es query 노을` 검색, 결과 없음 + 모두 풀기, 내 주변(가상 위치 서울) 25/50/75, 더미 카드 → 지도 점 + 안내 → 자세히 보기(홈의 지도 없음, 더미 표시, 측정값 —).
- [ ] **Step 2:** 더미로 출발 → 주행 화면(남은 거리 = 표기 거리, 남은 지점 목록, 지도 없음) → 가상 위치를 끝 점으로 옮기고 P → 요약. 앱 죽음 0건(`logcat` FATAL 0).
- [ ] **Step 3:** 1/3 · 전체 창, 밤에서 같은 화면 확인. 칩 누름 → 화면 바뀜이 0.25초 안인지 `gfxinfo`/로그 시각으로 잰다.
- [ ] **Step 4:** 보기 페이지에 "더 많은 코스" 묶음 촬영·배포, 새 APK 내려받기 교체.
- [ ] **Step 5:** 마지막 검토(가장 능력 있는 모델, AAR 분석 금지 명시) → Critical/Important 한 번에 고침 → PR 보고서 `~/reports/pr_YYYYMMDD_HHMM.md`.
