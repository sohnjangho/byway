# 디자인 개선 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 일반 지도·설정·경계·운전석 쪽 조작·코스 훑어보기·소요 시간·아이콘 색을 앱에 넣는다.

**Architecture:** 지도 그림은 도구가 두 레이어로 미리 만들고 앱은 파일 이름만 고른다. 화면 계산은 순수 함수로 빼서 JVM 시험으로 고정하고, View는 그 값을 그린다.

**Tech Stack:** Kotlin 1.9.25, Android View, Python 3 + Pillow(도구), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-09-design-refresh-design.md`

## Global Constraints

- `ai.pleos.*`, `android.car.*` import는 `app/.../platform/`에만.
- 주행 화면(`Screen.Drive`, `Screen.Waiting`)과 그 전환은 건드리지 않는다.
- 색은 `Palette`, 치수는 `Dimens`만 읽는다. `Palette.kt`는 `tools/make-theme.py`가 만든다.
- 터치 영역 76dp 이상(`Dimens.TOUCH_MIN`).
- 커밋 작성자 `byway-dev`. API 키·실명 금지.
- 시험: `./gradlew test`, `python3 tools/test_*.py`.

## Review Focus

1. 일반 지도 파일이 없는 코스(새 코스) → 위성으로 대신하고 충돌하지 않는다. (Task 2 시험)
2. 훑어보기 자료가 없는 코스 → 띠가 "다른 코스"로만 보인다. (Task 7 시험)
3. 코스 주변 정보가 0개인 코스의 소요 시간 → 세 값이 같아도 음수·0이 아니다. (Task 6 시험)
4. 설정에서 지도를 바꾼 직후 화면이 새 지도로 다시 그려진다. (에뮬레이터)
5. 기록을 지운 뒤 내 코스가 빈 목록으로 열린다. (Task 4 시험 + 에뮬레이터)

---

### Task 1: 도구가 일반 지도 레이어를 만든다

**Files:** `tools/make-covers.py`, `tools/make-scenes.py`, `tools/make-atlas.py`, `tools/make-app-scenes.py`, 각 `test_*.py`, 생성물 `design/screens/images/{scene,atlas}`, `app/src/main/assets/{scene,atlas,covers}`

**Produces:** `covers.LAYERS`, `covers.tile(zoom, x, y, layer="satellite")`, `covers.night_map(image)`, 파일 `*-map.jpg`, `*-map-night.jpg`. 흐린 배경(`soft`, `write_soft`, `free_photo`)과 `*-soft.jpg`는 지운다.

- [ ] 시험: `night_map`이 물색(146,164,176)을 (19,20,23)으로, 땅(232,232,232)을 그보다 밝게, 도로(200,200,200)를 땅보다 밝게 만든다. `tile`이 레이어마다 다른 주소와 캐시 폴더를 쓴다. `make-app-scenes`가 `-map` 파일도 복사한다.
- [ ] 실패 확인 → 구현 → 통과
- [ ] 15개 코스 생성, 용량 기록
- [ ] 커밋

### Task 2: 앱이 지도 종류에 맞는 파일을 고른다

**Files:** `ui/MapStyle.kt`(신규), `ui/Covers.kt`, `ui/Credits.kt`, `service/MyDrives.kt`, `test/.../CoverFileTest.kt`, `CreditsTest.kt`

**Produces:** `enum class MapStyle { MAP, SATELLITE }`, `sceneFile(courseId, night, style, files): String?`, `atlasFile(night, style, files): String?`, `coverFile(courseId, night, files, style)`, `mapCredit(style, withFrames): String`, `MyDrives.mapStyle: String`.

- [ ] 시험: MAP이면 `-map` 파일, 없으면 위성 파일. 야간 파일이 없으면 주간. 출처 문구가 종류마다 다르고 `withFrames`면 위성 출처가 붙는다.
- [ ] 구현, 호출부 연결(`SceneView`, `AtlasView`, `cover()`, `Locator`)
- [ ] 커밋

### Task 3: 경계를 고친다

**Files:** `ui/SceneGeometry.kt`, `SceneGeometryTest.kt`, `ui/SceneView.kt`, `ui/AtlasView.kt`, `ui/HomeScreen.kt`, `ui/RankingScreen.kt`, `ui/MineScreen.kt`, 삭제 `ui/SoftPatch.kt`, `ui/SoftBackground.kt`

**Produces:** `sceneFade(window, list)` 새 값(TWO 가로 1460→1530 알파 0→1, FULL 1140→1220, THIRD 세로 423→711, 목록 309→533), `photoFade` 새 값(TWO 756→826, FULL 1256→1336, THIRD 0.5h→0.9h), `EDGE_FADE = 0.12f`.

- [ ] 시험 → 구현(한국 지도는 네 변을 녹이고 바탕을 `bg`로) → 흐린 배경 호출 제거
- [ ] 커밋

### Task 4: 설정 화면

**Files:** `ui/Screen.kt`, `NavTest.kt`, `ScreenForTest.kt`, `ui/Rail.kt`, `ui/SettingsScreen.kt`(신규), `ui/ToggleRow.kt`(MineScreen에서 옮김), `ui/MineScreen.kt`, `ui/MainActivity.kt`, `service/MyDrives.kt`

**Produces:** `Nav.settings: Boolean`, `Screen.Settings`, `settingsScreen(context, kit, onAbout, onChanged)`, `MyDrives.clearRecords(context)`, `RailFrame(…, selected: Tab?, settingsOn: Boolean, onSettings: () -> Unit, …)`.

- [ ] 시험: `screenFor`가 P단에서만 Settings를 낸다. 주행이 시작되면 settings가 풀린다. 메뉴를 누르면 settings가 꺼진다.
- [ ] 구현 → 커밋

### Task 5: 운전석 쪽 조작

**Files:** `ui/AtlasCameraMath.kt`, `AtlasCameraMathTest.kt`, `ui/BrowseScreen.kt`, `ui/AtlasView.kt`, `ui/MineScreen.kt`, `ui/SceneView.kt`, `ui/HomeScreen.kt`

**Produces:** `atlasFocus(Tab.BROWSE, TWO) = Box(916, 40, 744, 1104)`, `FULL = Box(1196, 40, 1324, 1130)`, `AtlasView.addChrome(leftDp, resetLabel, onReset)`(단추는 왼쪽 아래), `SceneView.onPhoto: (Boolean) -> Unit`, `SceneView.togglePhoto()`.

- [ ] 시험 → 구현 → 커밋

### Task 6: 소요 시간

**Files:** `core/.../course/Pace.kt`(신규), `core/.../course/PaceTest.kt`, `ui/HomeCopy.kt`, `HomeCopyTest.kt`, `ui/HomeScreen.kt`

**Produces:** `data class Pace(val light: Int, val rest: Int, val stay: Int)`(분), `fun paceOf(meters: Double, curves: Int, kinds: List<String>): Pace`, `fun paceText(minutes: Int): String`.

- [ ] 시험: 팔당(50.3km, 48굽이, 경치 4·명소 2·음식 2) → 70, 90, 195. 달맞이(26.5km, 20굽이, 2·2·2) → 35, 55, 140. 주변 정보 0개 → 세 값이 양수이고 light ≤ rest ≤ stay. `paceText(70) == "1시간 10분"`, `paceText(35) == "35분"`.
- [ ] 구현 → 커밋

### Task 7: 코스 훑어보기

**Files:** `tools/make-frames.py`(신규), `tools/test_make_frames.py`, `tools/make-app-scenes.py`, `core/.../course/SceneIndex.kt`, `SceneIndexTest.kt`, `ui/ScrubMath.kt`(신규), `ScrubMathTest.kt`, `ui/ScrubStrip.kt`(신규), `ui/SceneView.kt`, `ui/HomeScreen.kt`, `ui/Screen.kt`, `ui/Covers.kt`

**Produces:** `CourseScene.track: List<TrackPoint>`, `CourseScene.frames: List<FrameInfo>`, `TrackPoint(x, y, km)`, `FrameInfo(km, section, mood, elev: Int?, placeNo: Int?)`, `scrubKm(scrollX, filmPx, totalKm)`, `trackAt(track, km): Px`, `frameAt(frames, km): Int`, `Nav.scrub: Boolean = true`, `SceneView.setMarker(at: Px?)`.

- [ ] 시험(도구·파서·계산) → 구현 → 16장 × 15코스 생성 → 커밋

### Task 8: 아이콘과 색

**Files:** `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`, `res/drawable/ic_launcher_foreground.xml`, `res/values/colors.xml`, `AndroidManifest.xml`, `ui/AtlasView.kt`, `ui/SceneView.kt`, `ui/HomeScreen.kt`, `ui/BrowseScreen.kt`, `ui/RankingScreen.kt`, `ui/SummaryScreen.kt`

- [ ] 지도 핀은 `NightPalette.accent`, 누를 수 없는 글은 `inkSecondary`(랭킹 1위 숫자는 `ink`), 일반 지도 주간의 번호는 어두운 원
- [ ] 커밋

### Task 9: 문서와 확인

**Files:** `docs/제작가이드.md`, `docs/권리점검.md`, `README.md`

- [ ] 에뮬레이터 release로 화면 확인, 캡처
- [ ] 커밋
