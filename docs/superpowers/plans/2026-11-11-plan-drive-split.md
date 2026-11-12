# 주행 화면 3분할과 파티 확대 지도 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 출발할 때 "차량 정보 | 내비 | 에움길" 3분할(설정으로 "내비 2/3"도 고름)을 만들고, 파티로 달릴 때는 에움길 칸에 멤버가 구별되는 확대 지도를 보인다.

**Architecture:**
- 범위 계산(5km 거르기, 여백, 최소 폭, 고정 규칙)은 core 순수 함수다.
- 출발 판단은 `MainActivity.start` 한곳에서 `startPlan(설정, 창)`으로 갈린다. 안내 중인 출발은 `Nav`에 담는다.
- 확대 지도는 기존 `CoursePathView`에 "보여 줄 범위"를 더해 그린다.
- 내비는 표준 지도 앱 인텐트로 `platform/`에서 띄운다.

**Tech Stack:** Kotlin 1.9.25, AGP 8.7.3, Android View(코드 작성), JUnit4.

**Spec:** `docs/superpowers/specs/2026-11-11-drive-split-design.md`

## Global Constraints

- 설정 저장값은 `"split"`(기본)이나 `"navi"`다. 모르는 값은 `"split"`으로 본다. 설정은 정차 중에만 바꾼다(설정 화면이 이미 정차 전용이다).
- 확대 범위:
  - 내 차에서 5,000m 안의 멤버만 넣는다.
  - 사방 여백은 20%다.
  - 가장 좁은 폭은 1,500m다.
  - 점이 범위 안쪽 80% 밖으로 나가면 다시 잡는다.
  - 점들이 범위의 35%보다 좁게 모이고 더 확대할 수 있을 때도 다시 잡는다.
  - 범위는 애니메이션 없이 한 번에 바꾼다.
- 주행 화면 규칙: 애니메이션은 없고, 움직이는 것은 위치 점과 핀뿐이다. 주행 중 조작은 늘리지 않는다.
- 안내 문구:
  - 제목 "주행 화면을 셋으로 나눕니다"
  - 본문 "오른쪽 끝 ⋮을 왼쪽으로 끌면 화면이 셋으로 나뉘고 바로 출발합니다"
  - 버튼 "나누지 않고 출발", "닫기"
- 설정 문구:
  - 그룹 "주행 화면"
  - 선택지 "3분할 · 내비 가운데", "내비 2/3 화면"
  - 설명 "3분할은 왼쪽 차량 정보, 가운데 내비, 오른쪽 에움길입니다"
- 확대 지도 글:
  - 먼 멤버 "멀리 있는 멤버 N명"
  - 출처는 `course.attribution`이다. 내장 코스는 "© OpenStreetMap contributors (ODbL)"이고, 운전자 기록은 "에움길 운전자 기록"이다.
- AAR이나 시스템 앱을 분석하지 않는다(Pleos 라이선스 3.4). `ai.pleos.*`는 `platform/`에만 둔다. 비공개 창 API는 쓰지 않는다.
- 글은 최소 12sp, 터치 76dp다. 색과 치수는 `Palette`·`Dimens` 토큰만 쓴다.
- 블라인드 심사: 실명·연락처는 쓰지 않는다. 저장소에 테일넷 주소를 넣지 않는다.

## Review Focus

1. **안내 중 기어 D:** 안내 화면에서 기어를 D로 바꾼 뒤 화면을 나눠도 주행 중에는 출발하지 않아야 한다. → Task 3 `guideWaitsWhileMoving`
2. **폭 변화 중복:** 나눈 뒤 폭 변화가 두 번 들어와도(`render`가 여러 번 불려도) 한 번만 출발해야 한다. → Task 3 `guideIsTakenOnce`
3. **창 크기 전:** 지도 칸 크기가 정해지기 전(가로세로 비 0 또는 NaN)에는 범위를 만들지 않아야 하고 앱이 죽지 않아야 한다. → Task 1 `badAspectGivesNoFrame`
4. **먼 멤버만:** 멤버가 모두 5km보다 멀면 확대하지 않고 코스 장면을 그대로 보여야 한다. → Task 1 `noNearMemberMeansNoFrame`
5. **겹친 멤버:** 멤버가 내 차와 같은 자리에 있어도(폭 0) 1.5km 폭을 지키고, 매 위치마다 범위가 흔들리지 않아야 한다. → Task 1 `neverNarrowerThanMinimum`, `staysPutAtMinimumWidth`

---

### Task 1: 파티 확대 범위 (core)

**Files:**
- Create: `core/src/main/kotlin/com/byway/core/geo/PartyFrame.kt`
- Test: `core/src/test/kotlin/com/byway/core/geo/PartyFrameTest.kt`

**Interfaces — Produces:**
- `data class GeoFrame(val center: GeoPoint, val widthMeters: Double, val heightMeters: Double)`
  - 속성 `west`, `east`, `south`, `north`는 `Double`(도)이다. 위도 1도 = 111,320m, 경도 1도 = 111,320·cos(중심 위도)m로 계산한다.
  - `fun contains(point: GeoPoint, inner: Double = 1.0): Boolean`: 중심에서 반폭·반높이의 `inner` 배 안이면 참이다.
- `data class FrameRules(val reachMeters: Double = 5_000.0, val margin: Double = 0.2, val minMeters: Double = 1_500.0, val inner: Double = 0.8, val tight: Double = 0.35)`
- `data class PartyFrame(val frame: GeoFrame, val far: Int)`
- `fun partyFrame(me: GeoPoint, members: List<GeoPoint>, aspect: Double, current: GeoFrame?, rules: FrameRules = FrameRules()): PartyFrame?`
  - `aspect`는 칸의 가로/세로다.
  - `null`이 되는 경우: `aspect`가 양수가 아님(NaN 포함), 또는 5km 안 멤버가 없음.
  - `far`는 5km 밖 멤버 수다.
  - 맞추기:
    1. 내 차와 가까운 멤버를 담는 상자(m)를 구하고, 폭과 높이에 각각 `1 + 2·margin`을 곱한다.
    2. 가로세로 비를 `aspect`에 맞춰 짧은 쪽을 늘린다.
    3. 폭이 `minMeters`보다 작으면 비를 지킨 채 키운다.
    4. 중심은 상자 가운데다.
  - 그대로 두는 조건(아래가 모두 참이면 `current`를 그대로 돌려준다):
    - `current != null`
    - `current`의 가로세로 비가 `aspect`와 1% 안에서 같다
    - 모든 점이 `current.contains(p, inner)`다
    - "점 상자 폭 < tight·current.widthMeters이고 점 상자 높이 < tight·current.heightMeters이고 current.widthMeters > minMeters + 1"이 아니다

- [ ] **Step 1: 실패하는 시험을 쓴다.** 기준점은 `me = GeoPoint(37.5, 127.3)`이고 `aspect = 841.0 / 580.0`이다. 거리는 동쪽·북쪽으로 m만큼 옮긴 점(시험 안 도우미 `offset(me, eastM, northM)`)으로 만든다.
  - `farMembersAreLeftOutAndCounted`: 1km 동쪽과 20km 북쪽 → `far == 1`, 1km 점은 `contains`, 20km 점은 `!contains`, `widthMeters < 5_000`.
  - `noNearMemberMeansNoFrame`: 멤버가 없을 때와 7km 멤버만 있을 때 둘 다 `null`.
  - `badAspectGivesNoFrame`: `aspect`가 0.0, -1.0, `Double.NaN`일 때 모두 `null`.
  - `fitsAllPointsWithMargin`: 2km 동쪽 → 두 점 모두 `contains(p, 0.8)`, `widthMeters`는 2,800±1.
  - `neverNarrowerThanMinimum`: 100m 멤버와, 같은 자리 멤버 → 두 경우 모두 `widthMeters`는 1,500±1e-6.
  - `keepsAspect`: 위 결과들에서 `widthMeters / heightMeters`가 `aspect`와 ±1e-6로 같다.
  - `keepsFrameWhilePointsStayInside`: 3km 동쪽으로 범위를 잡은 뒤 내 차만 100m 옮겨 다시 부른다 → 같은 `frame`(`==`).
  - `reframesWhenAPointLeavesTheInnerArea`: 같은 범위에서 멤버를 반폭의 0.95배 자리로 옮긴다 → 새 범위가 이전과 다르고 모든 점을 `contains(p, 0.8)`한다.
  - `zoomsInWhenPointsGather`: 4km 범위(폭 5,600)를 잡은 뒤 멤버를 500m로 옮긴다 → 새 폭은 1,500±1e-6.
  - `staysPutAtMinimumWidth`: 폭 1,500 범위에서 점들이 200m 안에 있고 안쪽에 있다 → 같은 `frame`.
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew :core:test --tests '*PartyFrameTest'` → Expected: 컴파일 실패(`partyFrame` 없음).
- [ ] **Step 3: 구현한다.** `PartyFrame.kt`에 위 시그니처대로 쓴다. 거리는 `GeoPoint.distanceTo`를 쓴다. 상자는 내 차 기준의 국소 평면 좌표(m)로 잰다.
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → Expected: PASS 10개. 이어서 `./gradlew :core:test` 전체가 통과해야 한다.
- [ ] **Step 5: 커밋한다.** `feat: 파티 확대 범위를 core에서 계산한다`

### Task 2: 주행 화면 설정과 출발 판단

**Files:**
- Create: `app/src/main/java/com/byway/ui/DriveLayout.kt`
- Create: `app/src/main/java/com/byway/platform/CarWindows.kt`
- Modify: `app/src/main/java/com/byway/service/MyDrives.kt`(`driveLayout` 저장·복원)
- Modify: `app/src/main/java/com/byway/ui/SettingsScreen.kt`("지도 보기" 그룹 아래에 "주행 화면" 그룹)
- Test: `app/src/test/java/com/byway/ui/DriveLayoutTest.kt`

**Interfaces — Produces:**
- `enum class DriveLayout(val title: String) { SPLIT("3분할 · 내비 가운데"), NAVI("내비 2/3 화면"); companion object { fun of(key: String?): DriveLayout } }`
  - 저장값은 `name.lowercase()`다.
- `internal fun currentDriveLayout(): DriveLayout = DriveLayout.of(MyDrives.driveLayout)`
- `enum class StartPlan { GO, GUIDE }`
- `fun startPlan(layout: DriveLayout, window: WindowClass): StartPlan`: `SPLIT`이면서 `THIRD`가 아니면 `GUIDE`, 나머지는 `GO`.
- `const val DRIVE_LAYOUT_NOTE = "3분할은 왼쪽 차량 정보, 가운데 내비, 오른쪽 에움길입니다"`
- `MyDrives.driveLayout: String`: 기본 `"split"`, 키는 `"drive_layout"`이다. `restore`에서 되돌린다.
- `fun showNavigation(context: Context): Boolean`(`platform/CarWindows.kt`)
  - `Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS)`에 `FLAG_ACTIVITY_NEW_TASK`를 붙여 `startActivity`한다.
  - `ActivityNotFoundException`이면 `false`다.

- [ ] **Step 1: 실패하는 시험을 쓴다.** `DriveLayoutTest`:
  - `unknownKeyIsSplit`: `of(null)`, `of("")`, `of("x")`는 `SPLIT`이다. `of("navi")`는 `NAVI`, `of("split")`은 `SPLIT`이다.
  - `splitOutsideThirdAsksToSplit`: `SPLIT`×`TWO`, `SPLIT`×`FULL`은 `GUIDE`다.
  - `splitInThirdGoes`: `SPLIT`×`THIRD`는 `GO`다.
  - `naviAlwaysGoes`: `NAVI`×모든 `WindowClass`는 `GO`다.
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew :app:testDebugUnitTest --tests '*DriveLayoutTest'` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현한다.** `DriveLayout.kt`, `MyDrives.driveLayout`, `CarWindows.kt`를 쓴다. 설정 화면 그룹은 `chipRow(DriveLayout.entries.map { Choice(it.name, it.title) }, currentDriveLayout().name, Dimens.SMALL) { MyDrives.driveLayout = it.lowercase() }`이고 그 아래에 `DRIVE_LAYOUT_NOTE` 라벨(`Dimens.LABEL`, `inkSecondary`)을 둔다.
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → Expected: PASS 4개.
- [ ] **Step 5: 커밋한다.** `feat: 주행 화면 배치 설정과 출발 판단`

### Task 3: 나누기 안내와 출발 연결

**Files:**
- Modify: `app/src/main/java/com/byway/ui/Screen.kt`
  - `StartRequest`를 더한다.
  - `Nav.splitGuide`를 더하고, `toTab`과 `whileDriving`에서 지운다.
  - `Screen.SplitGuide`를 더하고 `screenFor`에서 다룬다.
- Modify: `app/src/main/java/com/byway/ui/DriveLayout.kt`(`takeGuide`, 안내 문구)
- Create: `app/src/main/java/com/byway/ui/SplitGuideScreen.kt`
- Modify: `app/src/main/java/com/byway/ui/MainActivity.kt`
  - `start`의 판단과 권한을 받은 뒤의 판단에 `startPlan`을 쓴다.
  - `render`에서 `takeGuide`를 부른다.
  - `place`에 안내 화면을 둔다.
- Test: `app/src/test/java/com/byway/ui/ScreenForTest.kt`, `NavTest.kt`, `DriveLayoutTest.kt`(추가)

**Interfaces — Consumes:** Task 2의 `startPlan`, `currentDriveLayout`, `showNavigation`.

**Interfaces — Produces:**
- `data class StartRequest(val courseId: String, val resumeFrom: Int) : java.io.Serializable`
- `Nav.splitGuide: StartRequest? = null`
- `Screen.SplitGuide`(data object)
  - `screenFor`에서는 `state.finished` 판단 뒤, `resumable` 판단 앞에 둔다.
  - 정차 중이 아니면 기존 규칙대로 `Waiting`이 먼저 나온다.
- `fun takeGuide(nav: Nav, window: WindowClass, parked: Boolean): Pair<Nav, StartRequest?>`
  - 안내 중이고 `THIRD`이고 정차 중이면 `nav.copy(splitGuide = null) to 요청`을 돌려준다.
  - 아니면 `nav to null`이다.
- 문구 상수: `SPLIT_GUIDE_TITLE = "주행 화면을 셋으로 나눕니다"`, `SPLIT_GUIDE_BODY = "오른쪽 끝 ⋮을 왼쪽으로 끌면 화면이 셋으로 나뉘고 바로 출발합니다"`, `SPLIT_GUIDE_PLAIN = "나누지 않고 출발"`, `SPLIT_GUIDE_CLOSE = "닫기"`
- `internal fun splitGuideScreen(context: Context, kit: BrowseKit, onPlain: () -> Unit, onClose: () -> Unit): View`
  - 세 칸 그림을 그린다. 칸은 `차량 정보`·`내비`·`에움길`이고, 에움길 칸은 `accentSoft`로 칠하며, 오른쪽 끝에 ⋮을 둔다.
  - 그 아래에 제목·본문을 두고, 버튼 두 개는 아래에 붙인다.
- `MainActivity`:
  - `private fun launch(request: StartRequest)`: `DriveService.start(this, courseId, resumeFrom)` 뒤에 `showNavigation(this)`를 부른다.
  - `private fun begin(courseId: String, resumeFrom: Int)`: `startPlan(currentDriveLayout(), windowClassOf(widthDp()))`가 `GO`면 `launch`, `GUIDE`면 `go(nav.copy(splitGuide = StartRequest(...)))`다.
  - 기존 `start`와 `onRequestPermissionsResult`의 `DriveService.start` 호출을 `begin`으로 바꾼다.
  - `render` 첫머리:
    1. 정차 중이 아니면 `splitGuide`를 지운다.
    2. `takeGuide(nav, windowClassOf(widthDp()), state.parked)`가 요청을 주면 `nav`를 바꾸고 `launch`한다.
  - `place`: `Screen.SplitGuide`이면 `splitGuideScreen`을 둔다. "나누지 않고 출발"은 `nav`를 비운 뒤 `launch`하고, "닫기"는 `nav`를 비운다.

- [ ] **Step 1: 실패하는 시험을 쓴다.**
  - `ScreenForTest`:
    - `splitGuideShowsWhenParked`
    - `splitGuideComesBeforeResume`(`resumable = true`일 때도 `SplitGuide`)
    - `splitGuideHiddenWhileMoving`(`moving`이면 `Waiting`)
  - `NavTest`:
    - `drivingClosesSplitGuide`
    - `tabClosesSplitGuide`
  - `DriveLayoutTest`:
    - `guideStartsInThirdWhileParked`: `THIRD`, 정차면 요청이 나오고 `nav.splitGuide == null`이다.
    - `guideWaitsInWideWindow`: `TWO`면 `null`이고 nav는 그대로다.
    - `guideWaitsWhileMoving`: `THIRD`라도 `parked = false`면 `null`이다.
    - `guideIsTakenOnce`: 돌려받은 nav로 다시 부르면 `null`이다.
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew :app:testDebugUnitTest --tests '*ScreenForTest' --tests '*NavTest' --tests '*DriveLayoutTest'` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현한다.** 위 Interfaces대로 쓴다. 안내 화면은 다른 정차 화면(`recordConsentScreen`)과 같은 틀(바탕 `palette.bg`, `Dimens` 여백)로 만든다.
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → Expected: PASS. 이어서 `./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :core:test` 전체가 통과해야 한다.
- [ ] **Step 5: 커밋한다.** `feat: 3분할 나누기 안내와 내비 가운데 출발`

### Task 4: 파티 확대 지도

**Files:**
- Modify: `app/src/main/java/com/byway/ui/party/PartyPins.kt`(`MapPin.name`)
- Modify: `app/src/main/java/com/byway/ui/DriveScreen.kt`(`partyPins`가 닉네임을 싣는다, 확대 뷰 자리와 갱신)
- Modify: `app/src/main/java/com/byway/ui/CoursePathView.kt`(범위 맞추기, 닉네임, 축척, 출처, 먼 멤버 수)
- Create: `app/src/main/java/com/byway/ui/ZoomMath.kt`(축척 막대)
- Test: `app/src/test/java/com/byway/ui/PartyPinsTest.kt`(추가), `app/src/test/java/com/byway/ui/ZoomMathTest.kt`

**Interfaces — Consumes:** Task 1의 `GeoFrame`, `partyFrame`, `PartyFrame`.

**Interfaces — Produces:**
- `data class MapPin(val id: String, val point: GeoPoint, val avatar: String, val name: String = "")`
- `fun scaleBarMeters(frameWidthMeters: Double): Int`: [100, 200, 500, 1000, 2000, 5000] 가운데 `frameWidthMeters / 4` 이하인 가장 큰 값이다. 모두 크면 100이다.
- `fun scaleLabel(meters: Int): String`: 1,000 미만이면 `"200 m"` 꼴, 이상이면 `"1 km"`·`"2 km"` 꼴이다.
- `CoursePathView.zoom(frame: GeoFrame?, far: Int)`
  - 범위가 있으면 그 범위를 칸에 꽉 채워(여백 0) 코스 선을 다시 만든다. 범위가 없으면 지금처럼 코스 전체를 여백 10%로 맞춘다.
  - 범위가 있을 때만 다음을 그린다.
    - 바탕(`palette.bg`)
    - 핀 아래 닉네임(`Dimens.LABEL`, `ink`, `photoScrim` 테두리 글자)
    - 왼쪽 아래 축척 막대와 `scaleLabel`
    - 오른쪽 아래 출처(`course.attribution`)
    - `far > 0`이면 왼쪽 위 "멀리 있는 멤버 N명"
- `DriveScreen`:
  - 확대용 `CoursePathView` 하나를 `sceneView`와 같은 자리에 둔다. 1/3 창은 `MATCH × THIRD_DRIVE_SCENE_HEIGHT`, 넓은 창은 `leftMargin = sideWidth`로 글 칸 오른쪽 전부다.
  - 처음에는 `INVISIBLE`로 두어 크기를 잴 수 있게 한다.
  - `show(state)`에서 `partyFrame(state.position, 핀 위치들, 칸 가로/세로, 지난 범위)`가 범위를 주면 `VISIBLE`로 바꾸고 `zoom`·`moveTo`·`setPins`를 부른다. 범위가 없으면 `INVISIBLE`로 바꾼다.
  - 위치가 `null`이거나 칸 크기가 0이면 확대하지 않는다.

- [ ] **Step 1: 실패하는 시험을 쓴다.**
  - `PartyPinsTest.partyPinsCarryNicknames`: 내 멤버, 시연용 1·2, 핀 두 개로 `PartyView`를 만든다 → `partyPins(DriveState(party = view)).map { it.name }`는 `["시연용 1", "시연용 2"]`다.
  - `ZoomMathTest`:
    - `scaleBarFitsAQuarter`: 1,500 → 200, 2,800 → 500, 6,000 → 1,000, 30,000 → 5,000, 200 → 100.
    - `scaleLabelUsesKmFromOneKilometer`: 500 → `"500 m"`, 1,000 → `"1 km"`, 2,000 → `"2 km"`.
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew :app:testDebugUnitTest --tests '*PartyPinsTest' --tests '*ZoomMathTest'` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현한다.** 위 Interfaces대로 쓴다. `CoursePathView.Frame`은 위경도 범위(서·동·남·북)와 여백 비율을 받는 꼴로 일반화한다. 코스 전체를 맞출 때는 지금과 같은 결과가 나와야 한다.
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → Expected: PASS. 이어서 전체(`:app:testDebugUnitTest :app:testReleaseUnitTest :core:test`)가 통과해야 한다.
- [ ] **Step 5: 커밋한다.** `feat: 파티로 달릴 때 멤버가 구별되는 확대 지도`

### Task 5: 문서, 에뮬레이터 확인, 캡처

**Files:**
- Modify: `docs/제작가이드.md`. 구조에 `platform/CarWindows.kt`를 적고, 주행 화면 단락에 3분할 배치와 확대 지도를 적는다.

- [ ] **Step 1: 가이드를 고친다.** `grep -rn "ai\.pleos\." app/src/main/java --include=*.kt | grep -v /platform/` → Expected: 0줄.
- [ ] **Step 2: 시연 빌드를 만든다.** `./gradlew :app:assembleRelease -Pbyway.serverUrl=http://<테일넷 주소>:8787`로 만들어 에뮬레이터에 설치한다(`apk/` 밖에 둔다). `tools/emulator-lock.sh`를 써서 다음을 확인하고, 각 단계를 `dumpsys activity activities`의 작업 경계로 기록한다.
  1. 2분할에서 출발하면 안내가 뜬다. ⋮을 끌면 내비(853~1694)와 에움길(1718~2559)로 자동 출발한다.
  2. 1/3에서 출발하면 안내 없이 같은 배치가 된다.
  3. "내비 가운데, 에움길 오른쪽"인 상태에서 출발해도 배치가 그대로다.
  4. 이어 달리기에서도 안내가 뜬다.
  5. "내비 2/3" 설정이면 내비가 앱 칸을 쓴다.
  6. 안내 중 기어를 D로 바꾸면 대기 화면이 되고 출발하지 않는다.
  7. 파티(가상 멤버 2)로 달리면 확대 지도에 내 차와 멤버 ±1km가 구별되고 닉네임이 보인다. 위치 공유 동의 뒤 서버 DB `sharing = 1`도 확인한다.
  8. FATAL 0건.
- [ ] **Step 3: 캡처한다.** `~/reports/byway_drive_split_captures_20261111/`에 다음을 저장한다.
  - 3분할 주행(파티 확대)
  - 안내 화면
  - 설정
  - 내비 2/3
- [ ] **Step 4: 커밋한다.** `docs: 주행 화면 3분할을 가이드에 적는다`
