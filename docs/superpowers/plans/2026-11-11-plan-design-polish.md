# 화면 다듬기 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PM 의견 여섯 가지(지도 경계, 1/3 비율, 1/3 지도 틀, 코스 분위기, 출처 크기, 예시 내비 지도)를 화면에 반영한다.

**Architecture:**
- 값(띠·가림·크기·출처 문구)은 순수 함수와 상수로 두고 시험한다.
- 그리기는 기존 뷰를 고친다. 대상은 `SceneCanvas`, `AtlasView`, `DriveBackdrop`, `CoursePathView`다.
- 분위기 배경은 `DriveBackdrop`을 코스가 정해진 화면에서 다시 쓴다.
- 6번(내비 지도 합성)은 앱 밖 스크래치 도구로 한다.

**Tech Stack:** Kotlin 1.9.25, Android View(코드 작성), JUnit4, Python(Pillow, 캡처 가공).

**Spec:** `docs/superpowers/specs/2026-11-11-design-polish-design.md`

## Global Constraints

- 글은 12sp 이상이고, 글자 대비는 4.5:1 이상이다. 색과 치수는 `Palette`·`Dimens`·`Tokens`만 쓴다.
- 주행 화면에는 애니메이션이 없고, 움직이는 것은 위치 점과 핀뿐이다.
- 장면 띠:
  - TWO는 장면 좌표 1180→1600, 시작 투명도 0.16
  - FULL은 860→1290, 0.16
  - 1/3 값은 그대로다.
- 한국 지도 가림: 둘러보기·내 코스·설정은 `veil = 0.86`이고, 걷히는 길이는 420dp다.
- 바다색(낮·밤):
  - 일반 지도: #92A4B0, #131419
  - 위성: #192C4D, #0B1A31
- 1/3 칸 값:
  - 주행 지도 칸 740dp, 홈 장면 840dp
  - 1/3 글: 코스 이름 36sp(주행), 40sp(홈). 남은 거리 숫자는 `DRIVE_NUMBER × 0.7`이고, 구간 이름은 `DRIVE_UNIT` 크기다.
- 출처는 12sp 한 줄이다.
  - 지도: `Terrain Light © EOX · © OpenStreetMap contributors`, 위성: `Sentinel-2 cloudless © EOX · © OpenStreetMap contributors`
  - 사진: `사진 {작가} · {라이선스}`
  - 전체 문구는 앱 정보(`creditLines`)에 둔다.
- AAR이나 시스템 앱을 분석하지 않는다. 비공개 API는 쓰지 않는다. 실명은 쓰지 않는다.

## Review Focus

1. **밤 화면:** 지도가 패널 아래 16%로 비쳐도 밤 바탕에서 글자 대비가 4.5:1 아래로 떨어지지 않아야 한다. → Task 2 `textStaysReadableOverTheVeiledMap`
2. **사진 없는 코스:** 분위기 배경이 색만으로 그려지고 빈 화면이 되지 않아야 한다. → Task 5 `moodWithoutPhotoIsColorOnly`
3. **1/3 주행 화면 높이:** 1210dp 안에 지도 740dp, 글, 버튼이 모두 들어가 "코스 끝내기"가 잘리지 않아야 한다. → Task 4 `thirdDriveFitsTheWindow`
4. **긴 출처:** 위성 출처처럼 긴 지도 출처도 1/3 칸(841dp)에서 두 줄을 넘지 않아야 한다. → Task 1 `creditsFitTwoLinesInTheThirdWindow`
5. **바다색과 지도 종류:** 지도 종류(일반·위성)와 낮·밤이 바뀌면 그 바다색으로 칠해야 한다. 다른 바다색이 남으면 다시 틀이 보인다. → Task 3 `seaColorFollowsStyleAndNight`

---

### Task 1: 출처를 작게

**Files:**
- Modify: `app/src/main/java/com/byway/ui/Credits.kt`
- Modify: `SceneView.kt`(출처 칩: 한 줄, 12sp, 폭 제한 없음)
- Modify: `AtlasView.kt`(`addCredit` 같은 꼴)
- Modify: `HomeScreen.kt`, `DriveScreen.kt`(새 함수 사용)
- Test: `app/src/test/java/com/byway/ui/CreditsTest.kt`

**Interfaces — Produces:**
- `fun mapCreditLine(style: MapStyle): String`: 일반 지도는 `"Terrain Light © EOX · © OpenStreetMap contributors"`, 위성은 `"Sentinel-2 cloudless © EOX · © OpenStreetMap contributors"`
- `fun photoCreditLine(credit: String): String`
  - 코스의 `sceneryCredit`(예: `"Katsujiro Maekawa (CC0, Wikimedia Commons)"`)를 `"사진 Katsujiro Maekawa · CC0"`로 줄인다.
  - 괄호가 없으면 `"사진 " + credit`이다. 빈 문자열이면 빈 문자열이다.
- 기존 `mapCredit`, `shortMapCredit`, `driveMapCredit`, `driveCredit`은 화면에서 쓰지 않게 되면 지운다. `creditLines`는 그대로 둔다.

- [ ] **Step 1: 실패하는 시험을 쓴다.**
  - `mapCreditIsOneShortLine`: 두 종류 모두 위 문자열과 같고, 60자 이하다.
  - `photoCreditKeepsAuthorAndLicence`
  - `creditsFitTwoLinesInTheThirdWindow`: 문자 수 × 12sp × 0.55 ≤ 2 × (841 − 48)
  - `everyRightsHolderIsStillInAppInfo`: `creditLines(...)`에 EOX·OpenStreetMap·Copernicus가 있다.
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*CreditsTest'` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현한다.**
  - 지도 출처 칩은 지도 오른쪽 아래에 둔다.
  - 사진 출처는 사진이 보일 때 사진 오른쪽 아래에 따로 둔다. 주행 화면은 글 칸 배경 오른쪽 아래, 홈 풍경 사진은 사진 위다.
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → PASS. 전체 앱 시험도 통과해야 한다.
- [ ] **Step 5: 커밋한다.** `feat: 출처를 한 줄로 줄인다`

### Task 2: 지도를 화면 전체에

**Files:**
- Modify: `SceneGeometry.kt`(`sceneFade` TWO·FULL 값)
- Modify: `AtlasView.kt`(`AtlasShade.veil`)
- Modify: `BrowseScreen.kt`, `MineScreen.kt`, `SettingsScreen.kt`(veil 0.86, 걷히는 길이 420dp)
- Modify: `DriveBackdrop.kt`(가로 마스크 시작을 띠 시작에 맞춤)
- Test: `SceneGeometryTest.kt`(값 갱신), `app/src/test/java/com/byway/ui/VeilTest.kt`

**Interfaces — Produces:**
- `internal class AtlasShade(val fromRight: Boolean, val solid: Float, val clear: Float, val veil: Float = 1f)`
- `fun veiledContrast(ink: Int, bg: Int, map: Int, mapAlpha: Float): Double`: 바탕 위에 지도를 `mapAlpha`로 섞은 색과 글자색의 대비다.

- [ ] **Step 1: 실패하는 시험을 쓴다.**
  - `fadeBands`를 새 값으로 바꾼다.
  - `theMapShowsFaintlyUnderTheTextColumn`: TWO·FULL의 `alphaFrom == 0.16f`
  - `textStaysReadableOverTheVeiledMap`: 낮(ink, bg, 지도 가장 어두운 색 #5A6470, 0.16)과 밤(ink, bg, 지도 가장 밝은 색 #C8CCD2, 0.16)이 모두 4.5 이상이다.
  - 기존 `theMapStartsBesideTheTextColumnAndIsClearBeforeTheRoute`는 띠 끝이 경로보다 앞이라는 성질만 남긴다.
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*SceneGeometryTest' --tests '*VeilTest'` → Expected: FAIL 또는 컴파일 실패.
- [ ] **Step 3: 구현한다.**
  - `SceneCanvas.drawPicture`는 띠 값만 바뀌므로 코드는 그대로다.
  - `AtlasView.drawShade`는 `base`의 알파에 `veil`을 곱한다.
  - `DriveBackdrop` 가로 마스크의 시작은 `from - SIDE_FADE_LEAD`이고, 새 띠 시작에 맞춰 리드를 그대로 둔다.
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → PASS, 전체 앱 시험도 PASS.
- [ ] **Step 5: 커밋한다.** `feat: 지도를 패널 아래까지 잇는다`

### Task 3: 1/3 전체 지도 틀 없애기

**Files:**
- Modify: `AtlasView.kt`(그림 바깥 바다색, 1/3 아래쪽 세로 녹임)
- Create: `app/src/main/java/com/byway/ui/AtlasSea.kt`
- Test: `app/src/test/java/com/byway/ui/AtlasSeaTest.kt`

**Interfaces — Produces:** `fun seaColor(style: MapStyle, night: Boolean): Int`. 값은 Global Constraints의 네 색이다.

- [ ] **Step 1: 실패하는 시험을 쓴다.** `seaColorFollowsStyleAndNight`(네 조합)
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*AtlasSeaTest'` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현한다.**
  - `AtlasView`의 지도 칸 `onDraw` 첫머리에서 바다색을 칠한다.
  - 1/3 칸이면 칸 아래 120dp를 바다색에서 바탕색으로 세로 그라데이션 처리한다.
  - 넓은 창은 `AtlasShade`가 패널 쪽을 덮는다.
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → PASS, 전체 앱 시험도 PASS.
- [ ] **Step 5: 커밋한다.** `feat: 1/3 칸 한국 지도의 사각 틀을 없앤다`

### Task 4: 1/3 칸은 지도가 주인공

**Files:**
- Modify: `Dimens.kt`(`THIRD_DRIVE_SCENE_HEIGHT = 740`, `THIRD_SCENE_HEIGHT = 840`, `THIRD_DRIVE_NAME = 36f`, `THIRD_HOME_NAME = 40f`)
- Modify: `DriveScreen.kt`(1/3에서 이름·구간·숫자 크기)
- Modify: `HomeScreen.kt`(1/3 이름 크기)
- Modify: `SceneGeometry.kt`(1/3 주행 장면 rect가 칸 높이를 따르면 그 값)
- Test: `app/src/test/java/com/byway/ui/ThirdLayoutTest.kt`

**Interfaces — Produces:** `fun thirdDriveHeight(): Int`. 1/3 주행 화면 세로 합(dp)의 어림이다. 지도 + 이름 + 구간 + 숫자 + 막대 + 안내 2줄 + 앞으로 1줄 + 버튼 + 여백을 `Dimens`로 계산한다.

- [ ] **Step 1: 실패하는 시험을 쓴다.**
  - `thirdDriveFitsTheWindow`: `thirdDriveHeight() <= 1210`
  - `thirdTextsStayReadable`: 1/3 글 크기가 모두 12sp 이상이다.
  - `thirdDriveNumberStaysLargest`: 숫자 > 이름 > 구간이다.
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*ThirdLayoutTest'` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현한다.**
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → PASS, 전체 앱 시험도 PASS.
- [ ] **Step 5: 커밋한다.** `feat: 1/3 칸에서 지도를 키우고 글을 줄인다`

### Task 5: 코스 분위기

**Files:**
- Create: `app/src/main/java/com/byway/ui/CourseMood.kt`(코스와 구간 → 분위기 색, 사진 유무)
- Modify: `DriveBackdrop.kt`(창 전체를 덮는 `full` 모드: 띠 없이 화면 전체)
- Modify: `HomeScreen.kt`, `BrowseScreen.kt`(고른 코스일 때 패널 바탕)
- Modify: `party/PartyScreen.kt`, `SplitGuideScreen.kt`, `SummaryScreen.kt`(화면 바탕)
- Modify: `CoursePathView.kt`(확대 지도 바탕: 분위기 색 + 흐린 사진 25%)
- Test: `app/src/test/java/com/byway/ui/CourseMoodTest.kt`

**Interfaces — Produces:**
- `data class Mood(val tint: Int, val photo: Boolean)`
- `fun courseMood(course: Course, sectionIndex: Int, palette: Palette, photoAvailable: Boolean): Mood`: 구간 범위 밖이면 첫 구간이고, 색은 `palette.airColor(section.mood)`다.

- [ ] **Step 1: 실패하는 시험을 쓴다.**
  - `moodFollowsTheSection`
  - `outOfRangeSectionUsesTheFirst`
  - `moodWithoutPhotoIsColorOnly`
  - `moodTintKeepsTextReadable`: 모든 분위기 색 위에서 ink 대비가 4.5 이상이다(낮·밤).
- [ ] **Step 2: 시험이 실패하는지 본다.** `./gradlew --no-watch-fs :app:testDebugUnitTest --tests '*CourseMoodTest'` → Expected: 컴파일 실패.
- [ ] **Step 3: 구현한다.**
  - 각 화면 맨 아래에 `DriveBackdrop`(전체 모드)을 깔고, 원래의 `setBackgroundColor(palette.bg)`를 지운다.
  - 확대 지도는 `canvas.drawColor(colors.bg)` 대신 tint를 칠하고, 흐린 사진을 25%로 그린다.
- [ ] **Step 4: 시험이 통과하는지 본다.** 같은 명령 → PASS, 전체 앱 시험도 PASS.
- [ ] **Step 5: 커밋한다.** `feat: 코스가 정해진 화면에 코스 분위기를 깐다`

### Task 6: 에뮬레이터 확인, 기획서 예시 합성, 보고

- [ ] **Step 1: 비교 캡처를 찍는다.** 낮·밤 × 2/3·1/3로 홈, 둘러보기, 주행(파티 확대), 파티, 안내, 요약을 찍는다. 전후를 비교하고 FATAL 0인지 본다.
- [ ] **Step 2: 내비 지도를 합성한다.** 스크래치 `navi-composite.py`로 3분할 캡처의 내비 칸 바탕에 EOX Terrain Light 지도와 경로를 합성하고, 아래에 합성 표기를 단다. 결과는 `~/reports/byway_design_polish_captures_20261111/`에 둔다.
- [ ] **Step 3: 가이드를 고치고 보고서를 쓴다.** `docs/제작가이드.md`의 화면 단락을 고친다. PR 보고서를 쓰고, 기획서 세션에 캡처 교체를 알린다.
