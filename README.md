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

**주의**: 이 맥미니에는 Java·Android SDK·Gradle이 설치되어 있지 않다. 빌드 환경 구성이 선행되어야 한다.

## 상태

- [x] 기획서 초안
- [x] SDK 조사
- [x] **빌드 환경 구성** (JDK 21, Android SDK 34, Gradle 8.9, Android Studio)
- [x] **프로젝트 빌드 성공** (`app-debug.apk` 11MB)
- [ ] Pleos Emulator 구성
- [ ] 프로토타입 화면 (코스 목록 → 실행 → 스팟)
- [ ] 시연 영상
- [ ] GitHub 저장소 공개

## 빌드

```bash
cd ~/projects/byway
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```
