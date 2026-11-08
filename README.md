# 에움길 (Byway) — Pleos 드라이빙 코스 앱

> 에움길: 빙 둘러서 가는 굽은 길. 2026-11-07 RoadCraft에서 이름 변경.

현대차그룹 차량용 앱 개발 공모전 출품작.

## 한 줄

> 드라이빙 코스를 차의 내비에 그대로 입히는 앱. 최단 경로가 아니라, 그 길을 달리게 합니다.

## 핵심 기능

드라이빙 코스를 경유지 열로 바꿔 차의 내장 내비에 구간별로 올리고, 이탈하면 코스로 되돌린다. 부가 기능은 `feature/` 모듈로 붙인다.

제작 규칙은 `docs/제작가이드.md`를 먼저 읽는다.

## 데이터 출처

시연 코스 도로 좌표: © OpenStreetMap contributors (ODbL)
위성 그림: Sentinel-2 cloudless by EOX IT Services GmbH (Contains modified Copernicus Sentinel data 2016)
고도: Open-Meteo, Copernicus DEM
풍경 사진과 오픈소스 목록은 앱 정보 화면과 `docs/권리점검.md`에 있습니다.

## 문서

기획서·API 조사는 대회 폴더에 있다.

```
~/reports/AI대회/05_현대차_차량용앱/
  01_대회정보/     공모전 요약, 접수양식
  02_개발정보/     SDK 개요, API 검증결과, 개발환경
  03_아이디어/     확정 아이템 상세, 후보, 경쟁사
  04_기획서/       기획서 초안
```

## 저장소 구조

```
core/       코스 엔진과 계약. Android 의존성 없음
feature/    부가 기능. 기능 하나가 모듈 하나
app/        Android 앱. SDK 어댑터, 주행 서비스, 화면
tools/      시연, 녹화, 레인 점검 스크립트
docs/       제작 가이드
```

## 일정

| 날짜 | 내용 |
|---|---|
| 11.15 | 접수 마감 (기획서 + 선택: 시연영상·GitHub = 가산점) |
| 11.23 | 1차 서류 결과 |
| 12.03~04 | 2차 PT (실제 동작 앱 필수) |
| 12.18 | 최종 선정 |

## 개발 환경

| 항목 | 값 |
|---|---|
| Android SDK | API 34 |
| minSdk | 28 |
| 언어 | Kotlin |
| SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| Maven | https://nexus-playground.pleos.ai/repository/maven-releases/ |
| Emulator | Pleos Connect system image (API 34), Cold boot, RAM 4GB |


## 상태

- [x] 기획서 초안
- [x] SDK 조사
- [x] **빌드 환경 구성** (JDK 21, Android SDK 34, Gradle 8.9, Android Studio)
- [x] **프로젝트 빌드 성공** (`app-debug.apk` 11MB)
- [x] Pleos Emulator 구성
- [x] 새 화면 구조 이전(위성 장면, 한국 지도, 레일 메뉴)
- [x] 시연 영상

## 실행 방법

APK 위치: `apk/byway_에움길_release.apk`

```bash
adb install -r apk/*_release.apk
adb shell pm grant --user 10 com.byway pleos.car.permission.FUSED_LOCATION
adb shell am start --user 10 -n com.byway/.ui.MainActivity
```

기어가 P일 때 코스를 고를 수 있습니다. 홈의 "내비에 코스 입히기"를 누르면 차 내비에 코스가 올라갑니다.

## 테스트 환경

- Pleos Connect 에뮬레이터(AVD `Pleos_Connect`, API 34, 2560×1440)
- 앱 창 2/3(1700폭), 1/3(841폭), 주야간 모드에서 확인. 전체 창(2560폭)은 에뮬레이터에서 창을 넓힐 수 없어 화면 확인 전
- 에뮬레이터 내비 위치가 판교로 고정되어 있어 주행 시연은 판교 출발 코스(팔당 노을)로 합니다
- 기어와 속도는 Virtual Console(`adb forward tcp:8088 tcp:8088`)로 바꿉니다. `tools/record-demo.sh` 참고

## 빌드

JDK 21, Android SDK 34, Gradle 8.9가 필요합니다.

```bash
./gradlew :core:test :app:testDebugUnitTest
./gradlew assembleDebug    # app/build/outputs/apk/debug/
./gradlew assembleRelease  # 서명 설정 ~/.byway/release.properties 필요
```
