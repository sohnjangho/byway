# 에움길 (Byway) — Pleos 드라이빙 코스 앱

> 에움길: 빙 둘러서 가는 굽은 길. 2026-11-07 RoadCraft에서 이름 변경.

현대차그룹 차량용 앱 개발 공모전 출품작.

## 한 줄

> 드라이빙 코스를 차의 내비에 그대로 입히는 앱. 최단 경로가 아니라, 그 길을 달리게 합니다.

## 핵심 기능

1. **코스 저장·공유** — 달려본 길이 콘텐츠가 된다. 완주수·저장수·추천수·댓글수로 평가.
2. **코스 그대로 내비에 넣기** — 남이 만든 코스를 내 차 내비에 실행. 경로 추천 기준을 시간·비용에서 경험으로 바꾼다.
3. 보조: 코스 입력, 구간별 차량 제어, 스팟(먹거리·경치·휴식·포토·주의), 조건·목적 기반 추천

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
app/        Android (Kotlin, Pleos Connect SDK)
backend/    코스 공유 API (경량)
tools/      일출·일몰 계산, 코스 데이터 변환
docs/       개발 메모
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
