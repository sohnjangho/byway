# 계획 1: 앱 새 화면 구조 이전과 11.15 제출본 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 앱을 `design/screens` 시안 구조(레일 메뉴, 위성 장면, 한국 지도)로 옮기고 주행 규제를 규정대로 보강해, 11.15에 낼 release APK와 앱 시연 영상을 만든다.

**Architecture:** 시안과 같은 원본에서 앱용 장면 자료(`assets/scene`, `assets/atlas`)를 도구로 만든다. `core`의 순수 Kotlin 파서가 자료를 읽고, `ui`는 기존 Android View 코드로 `SceneView`·`AtlasView`를 그린다. 주행 엔진과 내비 연동은 바꾸지 않는다. 서버·코스 만들기·장면 생성·배포는 계획 2~4로 분리한다.

**Tech Stack:** Kotlin 1.9.25, Android View, AGP 8.7.3, compileSdk 34, kotlinx.serialization, `android.car`(CarUxRestrictions), Python 3 도구.

**Spec:** `docs/superpowers/specs/2026-11-08-app-shell-server-design.md`

**작업 위치:** `gpu-wsl:~/projects/byway`, 브랜치 `feature/app-shell-server`. 에뮬레이터 확인은 맥 AVD `Pleos_Connect`에서 한다.

## Global Constraints

- 창: 전체 2560×1210, 2/3 1700×1184, 1/3 841×1210. 메뉴는 전체·2/3 왼쪽, 1/3 위쪽 반투명.
- 주야간은 시스템 모드를 따르고 색·치수는 `Palette`/`Tokens`/`Dimens`만 쓴다. 생성 파일을 직접 고치지 않는다.
- `ai.pleos.*`와 `android.car.*` import는 `app/.../platform/`에만 둔다.
- 글자는 24sp 아래로 내리지 않고, 터치 대상은 `Dimens.TOUCH_MIN` 이상이다.
- 주행 화면에는 자동 애니메이션을 넣지 않는다. 분위기 효과는 정차 화면 정보 칸에만 넣고 지도·경로에는 넣지 않는다.
- 위성 그림이 보이는 모든 곳에 `Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)`를 표시한다.
- 코스 주변 정보는 `tools/nearby.json`의 값만 쓰고 영업 여부를 표시하지 않는다.
- 창 3종 모두 같은 기능을 제공한다(기능 패리티). 레터박스를 두지 않는다.
- 터치 반응 0.25초, 2초 넘는 로드는 진행 표시, 실행·로드 10초 이내, 복귀 시 상태 복원.
- 앱에 표본·더미 데이터를 넣지 않는다.

## Review Focus

1. 어느 화면에서든 기어가 P가 아니게 되거나 규제가 활성화되면 지도 조작·목록이 즉시 사라지고 대기 또는 주행 화면이 나온다 (Task 3, 4).
2. 창 막대를 끌거나 주야간이 바뀌어도 고른 코스, 메뉴, 둘러보기 지역, 지도 확대 상태가 남는다 (Task 4, 7).
3. 풍경 사진이 없거나 `sceneryCredit`이 비어 있는 코스는 밀어서 풍경 보기가 나타나지 않는다 (Task 6).
4. 장면 자료에 없는 코스나 빠진 그림 파일이 있어도 앱이 죽지 않고 코스 그림으로 대신한다 (Task 2, 5).
5. 코스 띠를 빠르게 연속으로 눌러도 늦게 끝난 이전 그림이 현재 코스를 덮지 않는다 (Task 5).

---

### Task 1: 앱용 장면 자료 도구와 제작가이드 갱신

**Files:**
- Modify: `docs/제작가이드.md` (3장 구조, 메뉴 4개, MapLibre 제거, 13장 도구 목록, 6장에 `CarUxRestrictions`)
- Modify: `tools/make-screen-data.py` (`main()`에서 `build_entries() -> tuple[list, dict]`를 분리)
- Create: `tools/make-app-scenes.py`
- Test: `tools/test_make_app_scenes.py`
- Create(생성물): `app/src/main/assets/scene/index.json`, `assets/scene/<id>.jpg`, `<id>-night.jpg`, `assets/atlas/korea.jpg`, `korea-night.jpg`

**Interfaces:**
- Produces: `index.json` 형식
  `{"sceneSize":[2404,1210],"koreaSize":[w,h],"labels":[{"name","kind","x","y"}],"courses":{id:{"lines":[{"mood","name","points":[[x,y],…]}],"places":[{"no","name","kind","label","note","km","x","y"}],"atlas":{"x","y","box":{x,y,w,h},"frame":{x,y,w,h},"lines":[{"mood","points"}]}}}}`

- [ ] **Step 1: 제작가이드를 먼저 고치고 커밋한다** (`docs: 새 화면 구조와 주행 규제 어댑터를 가이드에 반영`)
- [ ] **Step 2: 실패하는 시험을 쓴다**

```python
def test_path_to_points():
    assert points("M1 2 L3.5 4 L5 6") == [[1.0, 2.0], [3.5, 4.0], [5.0, 6.0]]

def test_index_has_every_course_with_points_and_no_evidence():
    index = build_index(ENTRIES, KOREA)
    assert set(index["courses"]) == {c["id"] for c in ENTRIES}
    first = index["courses"][ENTRIES[0]["id"]]
    assert isinstance(first["lines"][0]["points"][0], list)
    assert "evidence" not in first["places"][0] and "sourceUrl" not in first["places"][0]
```

`ENTRIES`, `KOREA`는 `courses.js` 항목 모양의 최소 값을 시험 안에 적는다.

- [ ] **Step 3:** `python3 -m pytest tools/test_make_app_scenes.py -v` → FAIL(모듈 없음)
- [ ] **Step 4: `points(d: str) -> list[list[float]]`, `build_index(entries, korea) -> dict`, `main()` 구현.** `make-screen-data.py`의 `build_entries()`를 불러 쓰고 그림은 `design/screens/images/{scene,atlas}`에서 복사한다.
- [ ] **Step 5:** 시험 PASS, `python3 tools/make-app-scenes.py` 실행 → `index.json`에 코스 15개. `python3 tools/make-screen-data.py` 후 `git diff --stat design/screens/courses.js`가 비어 있어야 한다.
- [ ] **Step 6: 커밋** `feat: 앱용 장면 자료를 시안과 같은 원본에서 만든다`

### Task 2: 장면 자료 파서와 로더

**Files:**
- Create: `core/src/main/kotlin/com/byway/core/course/SceneIndex.kt`
- Test: `core/src/test/kotlin/com/byway/core/course/SceneIndexTest.kt`
- Create: `app/src/main/java/com/byway/service/SceneCatalog.kt`

**Interfaces:**
- Produces:
  `data class Px(val x: Float, val y: Float)`
  `data class Box(val x: Float, val y: Float, val w: Float, val h: Float)`
  `data class SceneLine(val mood: String, val name: String, val points: List<Px>)`
  `data class ScenePlace(val no: Int, val name: String, val kind: String, val label: String, val note: String, val km: Double, val x: Float, val y: Float)`
  `data class AtlasSpot(val x: Float, val y: Float, val box: Box, val frame: Box, val lines: List<SceneLine>)`
  `data class CourseScene(val lines: List<SceneLine>, val places: List<ScenePlace>, val atlas: AtlasSpot)`
  `data class AtlasLabel(val name: String, val kind: String, val x: Float, val y: Float)`
  `data class SceneIndex(val sceneSize: Px, val koreaSize: Px, val labels: List<AtlasLabel>, val courses: Map<String, CourseScene>)`
  `fun parseSceneIndex(text: String): SceneIndex`
  `object SceneCatalog { fun index(context: Context): SceneIndex; fun of(context: Context, courseId: String): CourseScene? }`

- [ ] **Step 1: 시험을 쓴다**: `parsesLinesPlacesAndAtlas`(값 비교), `missingCourseIsAbsentNotCrash`(`index.courses["없음"] == null`), `atlasLineWithoutNameParses`(아틀라스 선의 `name` 없음 → `""`)
- [ ] **Step 2:** `./gradlew :core:test --tests '*SceneIndexTest*'` → FAIL
- [ ] **Step 3: `parseSceneIndex` 구현**(`CourseFile.kt`처럼 private `@Serializable` 파일 형식 + `ignoreUnknownKeys`). `SceneCatalog`는 assets 파일 읽기와 캐시, 파일이 없거나 읽기 실패면 빈 `SceneIndex`와 로그.
- [ ] **Step 4:** 시험 PASS
- [ ] **Step 5: 커밋** `feat: 장면 자료 파서를 더한다`

### Task 3: 주행 규제 어댑터 (`CarUxRestrictions`)

**Files:**
- Create: `app/src/main/java/com/byway/platform/CarRestrictions.kt`
- Create: `app/src/main/java/com/byway/service/ParkedState.kt`
- Modify: `app/src/main/java/com/byway/service/DriveApp.kt`, `app/build.gradle.kts`(`useLibrary("android.car")`), `app/src/main/AndroidManifest.xml`(`<uses-library android:name="android.car" android:required="false"/>`)
- Test: `app/src/test/java/com/byway/service/ParkedStateTest.kt`, `app/src/androidTest/java/com/byway/platform/CarRestrictionsTest.kt`

**Interfaces:**
- Produces:
  `fun parkedFrom(gearParked: Boolean, requiresDo: Boolean?): Boolean` (`requiresDo == null`은 규제 값을 모르는 경우)
  `class CarRestrictions(context: Context, onChange: (Boolean?) -> Unit) { fun start() }` — `Car.createCar` 실패 시 `onChange(null)`
- Consumes: 기존 `PleosGear(context, onParked)`, `DriveBoard.update`

- [ ] **Step 1: 시험을 쓴다**

```kotlin
@Test fun gearNotParkIsNeverParked() { listOf(true, false, null).forEach { assertFalse(parkedFrom(false, it)) } }
@Test fun activeRestrictionBlocksParked() = assertFalse(parkedFrom(true, true))
@Test fun parkAndNoRestrictionIsParked() = assertTrue(parkedFrom(true, false))
@Test fun unknownRestrictionFallsBackToGear() = assertTrue(parkedFrom(true, null))
```

- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*ParkedStateTest*'` → FAIL
- [ ] **Step 3: `parkedFrom`과 `CarRestrictions` 구현**: `CarUxRestrictionsManager.registerListener`의 `isRequiresDistractionOptimization()`을 넘긴다. `DriveApp`은 기어와 규제의 마지막 값을 들고 `parkedFrom` 결과로 `DriveBoard`를 갱신한다.
- [ ] **Step 4:** 시험 PASS, `assembleDebug` 성공
- [ ] **Step 5: 맥 에뮬레이터에서 실측**: Virtual Console 기어 P→D→P에서 `adb logcat -v time -s DRIVE:I`로 규제 값과 `parked` 전환을 확인한다. 기어 P에서 `requiresDo=false`가 아니면 원인을 확인하고 보고한다(값을 임의로 뒤집지 않는다).
- [ ] **Step 6: 커밋** `feat: CarUxRestrictions 를 정차 판정에 합친다`

### Task 4: 메뉴 4개, 창 크기, 화면 전환

**Files:**
- Modify: `app/src/main/java/com/byway/ui/Screen.kt`, `MainActivity.kt`, `BrowseKit.kt`
- Create: `app/src/main/java/com/byway/ui/Rail.kt` (`TabBar.kt` 삭제)
- Modify: `Dimens.kt`(`RAIL_WIDTH = 156`, `RAIL_ITEM_HEIGHT = 88`, `RAIL_TOP_HEIGHT = 92`)
- Test: `app/src/test/java/com/byway/ui/NavTest.kt`, `ScreenForTest.kt`

**Interfaces:**
- Produces:
  `enum class Tab { HOME, BROWSE, RANKING, MINE }` (제목 "홈","둘러보기","랭킹","내 코스")
  `enum class WindowClass { FULL, TWO, THIRD }`, `fun windowClassOf(widthDp: Int): WindowClass` (`< 1200` THIRD, `< 2100` TWO, 그 외 FULL)
  `data class AtlasCamera(val cx: Float, val cy: Float, val scale: Float) : Serializable`
  `data class Nav(val tab: Tab = Tab.HOME, val courseId: String? = null, val about: Boolean = false, val theme: String? = null, val region: String? = null, val rankBy: RankBy = RankBy.CURVES, val atlas: AtlasCamera? = null) : Serializable` — `courseId`는 홈에 올린 코스
  `Screen`에서 `Preview` 제거, `sealed interface Screen { Browse(tab), Drive, Summary(course), Waiting, About }`
  `fun Context.withRail(selected: Tab, window: WindowClass, onTab: (Tab) -> Unit, content: View): View`
  `BrowseKit`에 `val window: WindowClass`, `onCourse`는 `nav.copy(tab = HOME, courseId = course.id)`로 이동

- [ ] **Step 1: 시험을 바꾼다**: `Tab.entries`가 4개, `windowClassOf(841)==THIRD`, `windowClassOf(1700)==TWO`, `windowClassOf(2560)==FULL`, `everyChoiceSurvivesBeingSaved`에 `atlas = AtlasCamera(1f, 2f, 3f)`, `parkedWithChosenCourseStaysOnTab`(`Nav(tab=HOME, courseId="c1")` → `Browse(HOME)`), `movingNeverShowsBrowse`(모든 Tab, `parked=false` → `Waiting`), `movingWithChosenCourseIsWaiting`, `aboutIsHiddenWhileMoving`
- [ ] **Step 2:** `./gradlew :app:testDebugUnitTest --tests '*NavTest*' --tests '*ScreenForTest*'` → FAIL
- [ ] **Step 3: 구현.** 레일 치수와 모양은 `design/screens/app.css`의 `.rail*`을 따른다. FULL·TWO는 콘텐츠 왼쪽, THIRD는 위쪽이고 `.screen-over`처럼 콘텐츠 위에 반투명으로 겹친다. 위치 권한 후 재시작은 `Screen.Preview` 대신 `nav.courseId`를 쓴다. 이 단계의 새 탭 내용은 다음 Task가 채우며 임시로 기존 화면을 잇는다.
- [ ] **Step 4:** 시험 PASS, `assembleDebug` 성공
- [ ] **Step 5: 커밋** `feat: 메뉴를 왼쪽 레일 네 개로 바꾼다`

### Task 5: 위성 장면 뷰

**Files:**
- Create: `app/src/main/java/com/byway/ui/SceneView.kt`, `SceneGeometry.kt`
- Modify: `app/src/main/java/com/byway/ui/Covers.kt`(`loadScene`, 요청 토큰)
- Test: `app/src/test/java/com/byway/ui/SceneGeometryTest.kt`

**Interfaces:**
- Produces:
  `data class SceneRect(val left: Float, val top: Float, val width: Float, val height: Float)`
  `fun sceneRect(window: WindowClass, viewW: Float, viewH: Float): SceneRect`
  `data class FadeBand(val vertical: Boolean, val from: Float, val to: Float, val alphaFrom: Float)`
  `fun sceneFade(window: WindowClass): FadeBand`
  `class LatestOnly { fun next(): Int; fun isCurrent(token: Int): Boolean }`
  `class SceneView(context) : FrameLayout { fun show(course: Course, scene: CourseScene?, places: List<ScenePlace>, night: Boolean); val credit: String }`
- Consumes: `CourseScene`(Task 2), `WindowClass`(Task 4), `Palette.moodColor`

- [ ] **Step 1: 시험을 쓴다** (값은 `app.css` 기준)

```kotlin
@Test fun twoWindowIsRightAlignedAndCentered() =
    assertEquals(SceneRect(1700f - 2404f, 1184f / 2 - 605f, 2404f, 1210f), sceneRect(WindowClass.TWO, 1700f, 1184f))
@Test fun thirdWindowIsScaled() =
    assertEquals(SceneRect(841f - 1683f, -66f, 1683f, 847f), sceneRect(WindowClass.THIRD, 841f, 1210f))
@Test fun fadeBands() {
    assertEquals(FadeBand(false, 1280f, 1680f, 0.2f), sceneFade(WindowClass.TWO))
    assertEquals(FadeBand(false, 960f, 1420f, 0.2f), sceneFade(WindowClass.FULL))
    assertEquals(FadeBand(true, 590f, 706f, 1f), sceneFade(WindowClass.THIRD))
}
@Test fun lateResultIsDropped() { val l = LatestOnly(); val a = l.next(); val b = l.next(); assertFalse(l.isCurrent(a)); assertTrue(l.isCurrent(b)) }
```

- [ ] **Step 2:** 시험 → FAIL
- [ ] **Step 3: 구현.** 그림은 `sceneRect`에 맞춰 그리고 투명도는 야간 0.66, 주간 0.86(`app.css`), 경로는 glow·case·line 세 겹을 `sec-*` 색으로 `moodColor`에 맞춘다. 좌표는 `sceneSize` 기준으로 스케일한다. 번호 핀은 지름 40. `scene == null`이거나 그림이 없으면 `CoursePathView`로 대신한다. 사진 로드는 `LatestOnly`로 늦은 결과를 버린다. EOX 출처를 항상 그린다.
- [ ] **Step 4:** 시험 PASS
- [ ] **Step 5: 커밋** `feat: 위성 장면과 경로를 그리는 뷰를 더한다`

### Task 6: 홈 화면

**Files:**
- Modify: `app/src/main/java/com/byway/ui/HomeScreen.kt`(전면 교체), `MainActivity.kt`, `CourseBook.kt`, `Dimens.kt`
- Create: `app/src/main/java/com/byway/ui/HomeCopy.kt`, `CourseStrip.kt`
- Test: `app/src/test/java/com/byway/ui/HomeCopyTest.kt`, `CourseBookTest.kt`

**Interfaces:**
- Produces:
  `fun kickFor(mood: SceneMood): String` (문구는 `Main.dc.html`의 `KICKS` 그대로)
  `fun shownPlaces(places: List<ScenePlace>): List<ScenePlace>` (`view`+`photo` 2개, `sight` 2개, `food` 2개까지, 번호 다시 매김)
  `fun placeLine(place: ScenePlace): String` (`label · note · Nkm 지점`, 빈 값 제외)
  `fun canSwipeToScenery(credit: String, hasFile: Boolean): Boolean`
  `CourseBook.suggested(mood: SceneMood, hour: Int): Course`
  `internal fun homeScreen(context, kit, courseId: String?, onPick: (Course) -> Unit, onStart: (String) -> Unit): View`
- Consumes: `SceneView`, `SceneCatalog`, `moodFor(Weather.UNKNOWN, hour, month)`, `suggestFor(courses, mood, hour)`, `pickRow`, `Covers.loadSoft`

- [ ] **Step 1: 시험을 쓴다**: `kickFor(SUNSET) == "노을이 지는 시간에 어울리는 길"`; 경치 3개·명소 3개·음식 3개 입력 → `shownPlaces` 6개, 번호 1..6, 사진은 경치로 셈; `placeLine` 쪽 없는 값 제외; `canSwipeToScenery("", true)==false`, `canSwipeToScenery("사진: x", false)==false`, `canSwipeToScenery("사진: x", true)==true`; `suggested`가 같은 입력에 캐시된 값
- [ ] **Step 2:** 시험 → FAIL
- [ ] **Step 3: 구현.** 배치는 `Main.dc.html`과 `app.css`의 `.side`, `.side-near`, `.facts`, `.strip`을 따른다: 폭 620dp(FULL은 가로 2칸 1120dp, THIRD는 장면 아래 칸이고 `side-kick`·요약·코스 띠를 시안대로 숨기는 대신 **코스 고르기 버튼**으로 같은 기능을 제공). 수치 4개는 거리·굽이·에움 배율·오르막. 밀면 풍경 사진(`canSwipeToScenery`일 때만), 사진 출처 표시. 분위기별 정적 색 얹기(`softWash`)와 `kickFor` 문구만 넣고, 비·눈 움직임 효과는 이 계획에서 넣지 않는다.
- [ ] **Step 4:** 시험 PASS, `assembleDebug` 성공
- [ ] **Step 5: 맥 에뮬레이터 캡처**: 창 3종 × 주야간 6장을 시안과 눈으로 비교한다.
- [ ] **Step 6: 커밋** `feat: 홈을 위성 장면과 정보 칸 구조로 바꾼다`

### Task 7: 한국 지도 뷰와 둘러보기

**Files:**
- Create: `app/src/main/java/com/byway/ui/AtlasView.kt`, `AtlasCameraMath.kt`, `BrowseScreen.kt`
- Delete: `ThemeScreen.kt`, `RegionScreen.kt`, `ShelfScreen.kt`
- Test: `app/src/test/java/com/byway/ui/AtlasCameraMathTest.kt`

**Interfaces:**
- Produces:
  `fun atlasFocus(tab: Tab, window: WindowClass): Box` (`Browse.dc.html`·`Me.dc.html`·`Ranking.dc.html`의 `FOCUS` 값)
  `fun grow(box: Box, room: Float, least: Float): Box`
  `fun fit(box: Box, focus: Box): AtlasCamera`
  `fun courseCamera(spot: AtlasSpot, focus: Box): AtlasCamera` (`fit(grow(box, 0.18f, 120f), focus)`)
  `fun clampScale(scale: Float, minScale: Float, maxScale: Float): Float`
  `class AtlasView(context) : FrameLayout { fun show(tab: Tab, window: WindowClass, courses: List<Course>, chosen: String?, camera: AtlasCamera?, night: Boolean); var onCamera: (AtlasCamera) -> Unit; var onPick: (String) -> Unit }`
  `internal fun browseScreen(context, kit, nav: Nav, go: (Nav) -> Unit): View`
- Consumes: `AtlasSpot`, `SceneIndex.labels`, `withTheme`, `inRegion`

- [ ] **Step 1: 시험을 쓴다**: `atlasFocus(BROWSE, TWO) == Box(196f,40f,744f,1104f)`, `atlasFocus(MINE, FULL) == Box(1316f,96f,1204f,990f)`; `grow`가 최소 크기와 여백을 적용; `fit`의 scale이 `min(w비, h비)`이고 중심이 상자 중심; `clampScale` 양끝
- [ ] **Step 2:** 시험 → FAIL
- [ ] **Step 3: 구현.** 지도 확대는 `+`, `−`, 전체 버튼과 두 손가락 벌리기로 하며, 변경은 `onCamera`로 `Nav.atlas`에 저장해 창이 바뀌어도 남긴다. 고른 코스는 장면 그림을 `frame` 위치에 겹친다. 선 굵기는 배율로 나눠 화면에서 같게 유지한다. 지명·핀 겹침은 시안과 같다. EOX 출처 표시. 지역·테마 칩을 시안대로 카드 패널에 둔다(1/3 창도 같은 칩 제공).
- [ ] **Step 4:** 시험 PASS
- [ ] **Step 5: 맥 에뮬레이터 캡처** 창 3종 × 주야간, 창 막대를 끌어도 지도 위치 유지 확인
- [ ] **Step 6: 커밋** `feat: 한국 지도와 둘러보기 화면을 더한다`

### Task 8: 랭킹과 내 코스

**Files:**
- Modify: `app/src/main/java/com/byway/ui/RankingScreen.kt`, `MineScreen.kt`
- Test: `app/src/test/java/com/byway/ui/CourseBookTest.kt`

**Interfaces:**
- Produces: `internal fun rankingScreen(context, kit, nav: Nav, go: (Nav) -> Unit): View`, `internal fun mineScreen(context, kit, nav: Nav, go: (Nav) -> Unit, onStart: (String) -> Unit, onAbout: () -> Unit): View`, `CourseBook.mine(saved: Set<String>, recent: List<String>): List<Course>`
- Consumes: `SceneView`, `AtlasView`, `MyDrives`, `RankBy`

- [ ] **Step 1: 시험을 쓴다**: `mine`이 최근 기록 먼저·중복 제거·사라진 코스 제외
- [ ] **Step 2:** 시험 → FAIL
- [ ] **Step 3: 구현.** 랭킹은 `Ranking.dc.html`(왼쪽 순위 6개·작은 한국 지도, 오른쪽 장면이 칸 없이 이어짐), 내 코스는 `Me.dc.html`(큰 지도, 번호, 지명, 고르면 경로와 설명, 토글 4개는 `MyDrives`의 음성·연출·음악 값). 표시는 코스에서 계산한 값만 쓰고 속도·시간 순위를 만들지 않는다.
- [ ] **Step 4:** 시험 PASS
- [ ] **Step 5: 맥 에뮬레이터 캡처** 창 3종 × 주야간
- [ ] **Step 6: 커밋** `feat: 랭킹과 내 코스를 지도 구조로 바꾼다`

### Task 9: 주행·대기·요약과 옛 코드 제거

**Files:**
- Modify: `DriveScreen.kt`, `WaitingScreen.kt`, `SummaryScreen.kt`, `MainActivity.kt`, `app/build.gradle.kts`
- Delete: `PreviewScreen.kt`, `PicturePane.kt`, `service/MapPane.kt`, `platform/CourseMapPane.kt`, `androidTest/.../MapPaneTest.kt`, 호출이 없어진 `Theme.mapColors`, 호출처가 남지 않는 경우 `RetryDropped`·`okhttp`·`maplibre` 의존성

- [ ] **Step 1:** `Drive`, `Waiting`, `Summary` 시안과 비교해 새 토큰과 배치를 적용한다. `DriveScreen`에 움직임은 위치 점뿐인 현재 동작을 유지한다.
- [ ] **Step 2:** 호출이 사라진 파일을 지운다. 지우기 전 `grep -rn 'MapPane\|MapStyle\|PicturePane\|previewScreen\|maplibre' app/src`, 지운 뒤 0건 확인. **`RetryDropped`와 okhttp는 계획 2의 서버 연결이 다시 쓸 수 있으므로 호출이 없어도 이번에는 okhttp 의존성만 유지한다.**
- [ ] **Step 3:** `./gradlew :core:test :app:testDebugUnitTest :feature:ambience:test :feature:guide:test :feature:music:test assembleDebug` 전부 PASS, APK 크기가 88MB보다 줄었는지 기록
- [ ] **Step 4: 맥 에뮬레이터**: P→D 전환 시 주행 또는 대기 화면, 주행 중 메뉴 없음
- [ ] **Step 5: 커밋** `refactor: 새 구조로 대체된 화면과 지도 의존성을 지운다`

### Task 10: 권리 고지와 앱 정보

**Files:**
- Create: `docs/권리점검.md`
- Modify: `AboutScreen.kt`, `README.md`
- Test: `app/src/test/java/com/byway/ui/CreditsTest.kt`

**Interfaces:** Produces: `fun creditLines(courses: List<Course>): List<String>`, `val OPEN_SOURCE_NOTICES: List<String>`

- [ ] **Step 1: 시험**: 코스의 `attribution`·`sceneryCredit`이 모두 포함되고 빈 값·중복이 없으며 EOX, OSM, Open-Meteo/Copernicus 문구가 포함됨
- [ ] **Step 2:** FAIL 확인 후 구현, PASS 확인. MapLibre·OpenFreeMap 문구는 제거 대상이면 뺀다. `OPEN_SOURCE_NOTICES`는 `./gradlew :app:dependencies --configuration debugRuntimeClasspath`로 실제 의존성에서 뽑는다.
- [ ] **Step 3: `docs/권리점검.md`**: 코드 라이브러리, 코스·OSM, 위성, 고도, 풍경 사진 12장(`tools/scenery.json`), 커버, 글꼴(시스템 글꼴만 사용 확인), 시뮬레이터 자료(재현 영상을 제출하면 포함)를 표로 쓰고 항목마다 라이선스·출처·표시 위치를 적는다. 확인 못 한 항목은 `미확인`으로 둔다.
- [ ] **Step 4:** 앱 정보에 위치정보·개인정보 안내 문구를 쓰지 않는다(제출본은 서버 전송과 기록이 없으므로 "위치는 코스 안내에만 쓰고 저장·전송하지 않습니다" 한 줄만 사실대로 쓴다. 기록 기능이 생기는 계획 3에서 바꾼다).
- [ ] **Step 5: 커밋** `docs: 권리 점검표와 앱 정보 고지를 더한다`

### Task 11: release 빌드와 제출 산출물

**Files:**
- Modify: `app/build.gradle.kts`, `AndroidManifest.xml`(`android:allowBackup="false"`), `README.md`, `.gitignore`(`!apk/*.apk`)
- Create: `apk/<파일명>` (팀명은 PM 입력 필요, 없으면 Step 2에서 멈춘다)

- [ ] **Step 1:** 서명 설정을 `~/.byway/release.properties`(저장소 밖)에서 읽고 파일이 없으면 서명 없이 실패하도록 한다. 키는 `gpu-wsl:~/.byway/`에 만들고 저장소에 넣지 않는다.
- [ ] **Step 2:** `./gradlew assembleRelease`, `apk/팀명_앱이름_release.apk`로 복사. `aapt2 dump badging`으로 minSdk 28, 권한 목록 확인. **R8은 켜지 않는다**(현재 `isMinifyEnabled=false` 유지, SDK 분석 금지와 충돌 방지).
- [ ] **Step 3:** README에 앱 이름·한 줄 소개, 실행 방법, 테스트 환경, APK 위치, FUSED_LOCATION 권한 부여 명령을 쓴다. `git log`·README·`docs`에 실명 유무를 grep해 결과를 보고한다(규정상 저장소 공개 형태는 PM 결정).
- [ ] **Step 4:** 맥 에뮬레이터에 **release APK**를 설치해 전체 흐름 실행: 코스 선택 → 내비 입히기 → 구간 안내 → 이탈·복귀 → 완주 요약 (`tools/demo-drive.py`)
- [ ] **Step 5: 커밋** `chore: 제출용 release 빌드 설정과 README`

### Task 12: 시연 영상과 최종 확인

- [ ] **Step 1:** `tools/record-demo.sh`로 맥 에뮬레이터 실제 화면 영상을 녹화해 `05_제출물/`에 둔다. 재현 시안 영상은 쓰려면 "시안·연출" 표시를 눈으로 확인한다.
- [ ] **Step 2:** 성능 측정: 앱 실행 시간(`adb shell am start -W`) 10초 이내, 탭 전환 2초 이내, 기어 변경 후 화면 전환 시간 기록
- [ ] **Step 3:** 앱을 뒤로 보냈다가 복귀, 창 막대 끌기, `cmd uimode night yes/no`에서 상태 유지 확인
- [ ] **Step 4:** 격리 확인: `grep -rln 'ai\.pleos\.\|android\.car\.' app/src/main/java core feature | grep -v /platform/` 0건, `grep -rn 'serviceKey\|apiKey' app core feature` 0건
- [ ] **Step 5:** PR 보고서 `~/reports/pr_YYYYMMDD_HHMM.md`(요약, diff, 호출처 grep, 시험·에뮬레이터 로그, 검토 요청), 태그 `submit-20261115`

## 다음 계획

| 계획 | 범위 | 설계 장 |
|---|---|---|
| 2 | Ktor 서버, DB, 업로드·완주·신고 API, 앱 연결, 토큰 암호화 | 7, 12 |
| 3 | 코스 만들기, 동의, 궤적 암호화, 공유·철회 | 6-3, 12 |
| 4 | 장면 자료 생성 작업자, 앱 내려받기·표시 | 7-2-1 |
| 5 | 외부 HTTPS 배포, 방침·약관 문서, PT 빌드 | 7-4, 12 |
