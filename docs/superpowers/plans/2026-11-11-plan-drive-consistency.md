# 주행 화면 홈 맞춤 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 파티 확대 지도와 주행 글 칸을 홈과 같은 지도, 선, 표시, 출처, 목록, 버튼으로 맞춘다.

**Architecture:** 웹 메르카토르 계산은 `core/geo/MapTiles.kt`(순수 Kotlin)에 둔다. 앱은 조각을 받아 캐시하는 `MapTileCache`와, 장면과 함께 쓰는 그리기 함수 `MapMarks.kt`를 더한다. `CoursePathView`의 확대 모드가 이것들로 그린다.

**Tech Stack:** Kotlin, Android View/Canvas, HttpURLConnection, JUnit.

**Spec:** `docs/superpowers/specs/2026-11-11-drive-consistency-design.md`

## Global Constraints

- 주행 화면: 자동 애니메이션 없음, 움직이는 것은 위치 점뿐.
- Gradle은 `--no-watch-fs`로 돌린다. 시험: `:core:test`, `:app:testDebugUnitTest`, `:app:testReleaseUnitTest`.
- 커밋 작성자는 `byway-dev`. 실명과 테일넷 주소는 저장소에 넣지 않는다.
- 줌은 10~15, 위성 밤 대비 기준값은 45, 요청 머리말은 `byway-app`이다.

## Review Focus

1. 위도가 다른 코스(제주 33.4°, 강원 37.9°)에서 조각과 코스 선이 어긋나지 않아야 한다.
2. 범위가 바뀌어 줌이 달라지면, 이전 줌 조각을 섞지 않고 새 조각만 그린다.
3. 네트워크가 없을 때 예외 없이 지금의 바탕(색과 선)으로 그린다.
4. 화면을 떠난 뒤 도착한 조각은 버린다(떠난 뷰를 다시 그리지 않는다).
5. 밤 위성 조각 사이에 밝기 이음매가 없다(대비 기준값 고정).

---

### Task 1: 메르카토르 투영과 조각 계산(core)

**Files:** Create `core/src/main/kotlin/com/byway/core/geo/MapTiles.kt`, Test `core/src/test/kotlin/com/byway/core/geo/MapTilesTest.kt`

**Produces:**
- `fun worldX(lon: Double): Double`, `fun worldY(lat: Double): Double` (0~1)
- `data class Viewport(val left: Float, val top: Float, val width: Float, val height: Float)`
- `class MapProjection(frame: GeoFrame, fit: Viewport)`: `x(p)`, `y(p)`, `pxPerWorld`, `metersPerPx`
- `fun tileZoom(pxPerWorld: Double, min: Int = 10, max: Int = 15): Int`
- `data class TileSpot(val z: Int, val x: Int, val y: Int, val left: Float, val top: Float, val size: Float)`
- `fun MapProjection.tilesFor(viewW: Float, viewH: Float, z: Int): List<TileSpot>`
- `fun pointAt(path: List<GeoPoint>, km: Double): GeoPoint?`

- [ ] 시험: 범위 중심이 맞춤 칸 중심에 온다. 범위가 칸에 꽉 맞는다(가로 또는 세로). 조각 하나의 화면 크기는 `pxPerWorld / 2^z`이다. 조각 위치에 놓은 위경도가 `x(p)`와 1px 안에서 같다(위도 33.4°와 37.9°). 줌은 화면 1px에 조각 1px에 가깝게 고르고 10~15로 묶는다. `pointAt`은 0km가 첫 점, 전체 길이를 넘으면 끝 점, 중간은 선분 위 보간이다.
- [ ] RED 확인 → 구현 → GREEN → 커밋.

### Task 2: 밤 조각 변환과 조각 주소(app, 순수 함수)

**Files:** Create `app/src/main/java/com/byway/ui/TileLook.kt`, Test `app/src/test/java/com/byway/ui/TileLookTest.kt`

**Produces:** `fun tileUrl(style: MapStyle, z: Int, x: Int, y: Int): String`, `fun nightMapPixel(rgb: Int, bg: Int): Int`, `fun nightSatPixel(rgb: Int): Int`

- [ ] 시험: 주소는 도구의 두 층과 같다(`terrain-light_3857`, `s2cloudless_3857`, `{z}/{y}/{x}.jpg`). `nightMapPixel`은 물(파랑−빨강 ≥ 15)이면 바탕색, 땅은 회색값 v에 대해 `clamp(14,84, round(26 + (246 − v) × 0.7)) + 바탕` 채널별(255 상한)이다. `nightSatPixel`은 `((c × 0.62) − 45) × 1.12 + 45`를 0~255로 묶는다. 기댓값은 파이썬 원본 식으로 계산한 값이다.
- [ ] RED → 구현 → GREEN → 커밋.

### Task 3: 조각 캐시(app)

**Files:** Create `app/src/main/java/com/byway/ui/MapTileCache.kt`

**Produces:** `object MapTileCache { fun get(context, style, night, z, x, y, onReady: () -> Unit): Bitmap? }`

- [ ] 메모리 LRU(64장) → 디스크(`cacheDir/tiles/<층>/<z>/<x>/<y>.jpg`) → 네트워크 순서. 두 일꾼 스레드, 같은 조각은 한 번만 받는다. 밤 변환은 디스크에서 읽은 뒤 한다. 실패하면 null을 남기고 다음 요청 때 다시 시도한다(30초 뒤). 도착하면 메인 스레드에서 `onReady`를 부른다.
- [ ] 단위 시험은 Task 2의 순수 함수로 갈음한다. 동작은 Task 5의 에뮬레이터 확인에서 본다. 커밋.

### Task 4: 장면과 함께 쓰는 그리기 함수와 확대 지도

**Files:** Create `app/src/main/java/com/byway/ui/MapMarks.kt`; Modify `SceneView.kt`(SceneCanvas가 MapMarks를 쓴다, 동작 같음), `CoursePathView.kt`

**Produces:** `MapMarks.route(canvas, paths: List<Pair<String, Path>>, ...)`, `MapMarks.place(...)`, `MapMarks.marker(...)`, `MapMarks.mapAlpha(night)`; `CoursePathView.zoomFit: Viewport?`, `CoursePathView.places`

- [ ] 확대 모드: `MapProjection`(맞춤 칸 = `zoomFit` 또는 뷰 전체)으로 선과 핀을 놓는다. 그 아래에 조각을 `mapAlpha` 층으로 그리고 `sceneFadeOnScreen` 띠로 가린다. 선은 `MapMarks.route`, 장소 번호는 `pointAt(path, place.km)` 자리에 `MapMarks.place`, 위치는 `MapMarks.marker`이다. 확대 안 출처 글자(`zoomCredits`)는 없앤다.
- [ ] 시험: 기존 시험이 그대로 통과한다(SceneCanvas 동작 같음). 커밋.

### Task 5: 주행 화면

**Files:** Modify `DriveScreen.kt`, `DriveReadout.kt`, `SceneView.kt`(`setMapShown`), `Style.kt`(`driveButton`), `record/RecordScreens.kt`; Test `DriveReadoutTest.kt`

- [ ] 시험 먼저: `aheadLine(place, drivenKm)` = "경치 · 해질녘 · 1.5km 앞", 설명이 없으면 "경치 · 1.5km 앞".
- [ ] 확대 뷰는 장면과 같은 크기(2/3 전체, 1/3 752dp)이고 `zoomFit`은 2/3에서 글 칸 오른쪽이다. 확대 중에는 `sceneView.setMapShown(false)`로 칩만 남기고, 배경은 넓히지 않는다. 1/3은 사진을 깔지 않는다. 목록은 홈 줄 형식이다. "코스 끝내기"와 기록 중 버튼은 `driveButton`(면 `bgRaised` + 선, 눌림 움직임 없음)이다.
- [ ] 전체 시험 GREEN, 시연 빌드, 에뮬레이터 3분할·2/3 확대 지도(낮·밤, 일반·위성)와 일반 주행 확인, 커밋.
