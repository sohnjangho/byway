# Gleo 음성 연동 설계 — "글레오로 말해서 코스 찾기·출발·주행 중 답하기"

> 작성 2026-11-13 · 기준 커밋 `a9f13b0`(main, PR #1~#13 병합) · 브랜치 `feature/gleo-voice` · 개발 위치 `gpu-wsl:~/projects/byway`
> 표기: **[PM]** PM이 정한 것, **[제안]** 개발이 정한 것(PM 확인 대상), **[문서]** Pleos 공개 문서로 확인한 사실.

## 1. 왜 하는가

- PM 11-12: "글레오 AI 연계로 기능을 추가하자. 글레오로 말하면 코스 검색, 실행 등 여러 가지를 할 수 있게." **[PM]**
- 운전 중에는 에움길 화면을 만지기 어렵다. 특히 2분할(차량 정보 + 내비)로 달리면 에움길 화면이 아예 가려진다.
  - 그래서 "코스를 벗어나고 있습니다. 잠깐 들르시나요?" 질문에 답할 길이 없다.
  - 15번의 위치 동안 답이 없으면 자동으로 복귀 경로를 넣는다(11-12 확인).
- Gleo Interface는 Pleos가 앱에 음성 명령을 붙이라고 내놓은 공식 방법이다. 앱이 자체 음성 인식이나 LLM을 둘 필요가 없다.
  - 공모전 심사의 "SDK 핵심 활용"에도 바로 닿는다.

## 2. Pleos 문서로 확인한 규칙 **[문서]**

출처는 `document.pleos.ai/docs/pleos-only/app-integration-guide/guide/` 아래 core-concepts, app-capability, deliver-schema, action_callback, examples와 use-cases/radio-app이다(11-13 열람). AAR은 열지 않았다(라이선스 3.4).

- **방식:** OpenAI function calling과 비슷하다.
  - 앱이 `assets/schema.json`(App Schema)에 함수를 적는다.
  - Gleo LLM이 사용자 말을 그 함수 호출로 바꿔 Android Intent로 앱에 보낸다.
  - 앱은 결과를 Callback 방송으로 돌려준다.
  - Gleo는 그 결과로 최종 답을 만들어 말한다.
- **함수 수:** `actionCalls`는 **최대 3개**다.
  - 이름은 대문자와 밑줄로 쓰고, 2~3단어로 대상까지 넣는 것을 권한다.
  - 함수 하나가 호출 한 번으로 명령을 끝낼 수 있게 짜라고 권한다.
- **설명 문구:** 앱, 함수, 인자 설명은 모두 영어로 쓰고, 한두 문장으로 쓴다.
  - 예시를 넣으면 안 되고, Gleo 동작을 조종하려는 문구도 안 된다.
- **인자 타입:** `string`, `number`, `boolean`, `array` 중 하나만 쓴다. `enum`은 문자열만 되고 null은 안 된다.
  - `required`에는 꼭 필요한 인자만 넣는다.
- **`component`:**
  - `"activity"`는 `startActivity`로 부른다. 화면이 앞으로 나온다.
  - `"service"`는 `sendBroadcast`로 부른다. 화면 없이 처리한다.
  - 두 경우 모두 `setPackage(앱 패키지)`, `action = 함수 이름`이고, 인자는 extra `ACTION_PARAM`(JSON 문자열)에 담긴다.
  - 문서 예시는 JSON을 따옴표로 한 번 더 감싼 모양(`"\"{...}\""`)이다.
- **매니페스트:** 함수마다 그 이름의 action으로 intent-filter를 단다.
- **Callback 방송**
  - action은 `ai.pleos.playground.intent.action.APP_ACTION_RESPONSE`이다.
  - extra는 `packageName`과 `response`(JSON 문자열)다.
  - `response`에는 `functionName`, `status`, `message`(필수), `mode`, `data`(선택)를 넣는다.
    - `status`는 `success` / `asking` / `fail` 중 하나다.
    - `message`는 영어 완전한 문장이다. 코스 이름 같은 고유명은 한국어로 둬도 된다.
    - `mode`는 `voice`(기본) / `beep` / `silent` 중 하나다.
    - `data`는 JSON 객체 배열을 문자열로 담은 것이다. 넣기를 강하게 권하고, 필요한 것만 담는다.
- **제한 시간:** **7초** 안에 Callback이 없으면 Gleo가 취소한다.
- **등록**
  - Gleo AI 앱은 부팅할 때와 앱을 설치하거나 지울 때 `assets/schema.json`을 읽는다.
  - Pleos가 스키마를 검토해 **승인해야 연동이 켜진다.**
- **화면:** Gleo 대화창은 다른 앱 위에 잠깐 뜬다. 목록과 카드 템플릿, 칩은 Gleo가 그린다. 앱이 템플릿을 정하는 방법은 문서에 없다.

에뮬레이터 상황(11-13 확인):
- `ai.pleos.gleoai.model.provider` 패키지는 있다.
- 하지만 말을 걸 수 있는 Gleo 화면이나 PTT는 확인하지 못했다.
- 그래서 시험은 Gleo가 보낼 Intent를 `adb shell am broadcast`로 흉내 내는 방식으로 한다(8절).

## 3. 목표

함수 3개로 "찾기 → 출발 → 달리는 중 말로 조작"을 덮는다.

| 함수 | component | 하는 일 | 사용자 말(예) |
|---|---|---|---|
| `FIND_DRIVING_COURSES` | service | 코스 찾기. 검색어, 무드, 내 주변 거리로 거른다 | "노을 보기 좋은 코스 찾아줘", "근처 바닷길 있어?" |
| `START_DRIVING_COURSE` | service | 이름으로 코스를 골라 내비에 입히고 출발한다 | "팔당 노을 출발해줘", "두 번째 거로 가자" |
| `CONTROL_RUNNING_COURSE` | service | 달리는 코스 조작과 상태 묻기 | "잠깐 들를게", "코스로 돌아갈래", "얼마나 남았어?", "코스 그만할래" |

세 함수 모두 `service`를 쓴다 **[제안]**.
- 이유: 내비가 넓게 떠 있거나 3분할로 달리는 중에 에움길 화면이 앞으로 튀어나와 배치를 흔들면 안 된다.
- 결과는 Gleo가 말과 대화창으로 전한다.

### 3-1. `FIND_DRIVING_COURSES`

- **인자:** 셋 다 선택이라 `required`는 비운다.
  - `searchTerm`(string): 코스 이름, 지역, 장소, 무드 이름에 들어 있는 말
  - `mood`(string enum): `alone`, `date`, `family`, `spring`, `summer`, `autumn`, `winter`, `rain`, `sunrise`, `day`, `sunset`, `night`
    - `MOOD_KEYS` 12가지와 일대일로 대응한다.
  - `nearbyKm`(number): 차 위치에서 출발 지점까지 거리 상한(km)
- **고르기:** 기존 `filterCourses`를 그대로 쓴다.
  - 검색어는 지금과 같은 방식으로 맞춘다. 모든 낱말이 들어 있어야 한다.
  - `nearbyKm`는 25 / 50 / 75 중 같거나 큰 가장 가까운 값으로 올린다. 75를 넘으면 75로 둔다 **[제안]**.
  - 위치를 모르면 `nearbyKm`는 건너뛰고, `data`와 `message`에 그 사실을 적는다.
- **정렬과 개수**
  - 정렬: 실제 코스가 먼저, 더미 코스가 나중이다. 위치를 알면 각각 안에서 가까운 순이다 **[제안]**.
  - `data`에는 앞의 **3개만** 넣는다. 항목은 `rank`, `name`, `region`, `km`, `awayKm`(모르면 생략), `moods`(한국어 이름 목록), `dummy`(boolean)이다.
  - `message`에는 전체 개수와 3개 이름을 적는다.
- **결과 없음:** 결과가 0개여도 `success`로 보낸다. message는 "no matching courses"다.
- **인자가 모두 비었을 때:** `asking`으로 보낸다(무엇을 찾을지 되묻기).
- **마지막 결과 기억:** 찾은 3개를 기억해 둔다. 다음 `START_DRIVING_COURSE`가 "두 번째 거"를 이름으로 못 받았을 때 대비다. 10분이 지나면 잊는다 **[제안]**.

### 3-2. `START_DRIVING_COURSE`

- **인자**
  - `courseName`(string, **required**)
  - `resultRank`(number, 선택): 직전 찾기 결과의 몇 번째인지
- **이름 맞추기:** 아래 순서로 맞춘다. core의 순수 함수로 두고 시험한다.
  1. 이름이 정확히 같다(공백과 대소문자 무시).
  2. 이름에 말이 들어 있다.
  3. 모든 낱말이 이름, 지역, 장소에 들어 있다.
  4. `courseName`이 비었거나 맞는 것이 없고 `resultRank`가 있으면 직전 결과의 그 순위를 쓴다.
- **답의 갈래**
  - 하나로 정해지면: 출발한다.
  - 여럿이 남으면: `asking`으로 후보를 최대 3개 `data`에 담는다.
  - 없으면: `fail`이다.
- **출발 조건**
  - **주행 중에도 출발한다 [PM 11-13].** 음성이라 화면을 만지지 않는다. 손으로 하는 출발(출발 화면 고르기)은 지금처럼 정차 중에만 된다.
  - 다른 코스가 달리는 중이면 `fail`로 "지금 코스를 끝낸 뒤 다시"를 알린다 **[제안]**. 그래서 달리는 코스를 말 한마디로 갈아 끼우지는 않는다. 다른 앱이 같은 방송을 흉내 내도 달리던 코스는 바뀌지 않는다(6절 보안).
- **출발 방법**
  - `DriveService.start(context, courseId)`를 그대로 쓴다.
  - **화면 배치는 지금 그대로 둔다.** 출발 화면 고르기는 띄우지 않는다.
  - 에움길이 가려져 있으면 뒤에서 달리고, 내비 경로는 지금처럼 들어간다.
  - 출발 화면 고르기는 손으로 출발할 때만 쓴다 **[제안]**.
- **화면 뒤에서 출발할 때 (targetSdk 34):** 안드로이드 12부터는 앱이 백그라운드일 때 포그라운드 서비스 시작이 막힐 수 있다.
  - 에움길이 2분할로 가려져 있고 달리는 코스가 없으면, 앱에 포그라운드 서비스가 없는 백그라운드 상태다.
  - 이때는 `DriveService.start`를 시도한다. `ForegroundServiceStartNotAllowedException`이 나면 `fail`로 "에움길을 화면에 띄운 뒤 다시 말해 달라"를 알린다 **[제안]**.
  - Gleo(시스템 앱)의 방송이 예외 허용을 함께 주는지는 문서에 없다. 그래서 에뮬레이터에서 실측해 결과를 이 절에 적는다.
  - 프로세스가 아예 없을 때도 수신기가 앱을 깨운다. 첫 코스 목록 읽기(더미 150개 포함)와 기어 확인이 6초 안에 끝나는지 함께 잰다.
- **Callback:** 출발 요청을 넣는 즉시 `success`를 보낸다. 내비 경로 계산이 끝나기를 기다리지 않는다(7초 제한).
  - `data`는 `name`, `km`, `sections`(구간 수), `firstSection`이다.
- **겹치는 말 막기:** 엔진은 출발할 때 `Messages.courseStart(이름)`을 직접 말한다(`CourseEngine.start`). 출발 성공 Callback은 `mode: "silent"`로 보내 Gleo가 말하지 않게 하고, 에움길의 출발 안내를 그대로 쓴다 **[제안]**.
  - 문서는 앱이 스스로 소리를 낼 때 `silent`를 쓰라고 안내한다.
  - `data`는 그대로 넣어 대화가 이어지게 한다.
  - `fail`과 `asking`은 Gleo가 말한다(기본 `voice`).

### 3-3. `CONTROL_RUNNING_COURSE`

- **인자:** `command`(string enum, **required**)
- **`command` 값과 동작**

| command | 뜻 | 동작 |
|---|---|---|
| `stop_by` | 잠깐 들를게 | 묻는 중인 `Prompt.DETOUR`에 "예"로 답한다(`DriveService.answer(yes = true)`) |
| `back_to_course` | 코스로 돌아갈래 | `DETOUR`에 "아니요"로 답한다. 복귀 경로를 안내한다 |
| `resume` | 이어서 달릴래 | `Prompt.RESUME`에 "예"로 답한다 |
| `end_course` | 코스 그만 | 지금 화면의 "코스 끝내기"와 같다(`DriveService.stop`) |
| `status` | 얼마나 남았어 | 남은 km, 구간 i/n, 구간 이름, 다음 구간 이름을 `data`로 준다 |

- **화면이 가려져도 되는 이유:** 이 함수는 코스가 달리는 중에만 의미가 있다. 그때는 `DriveService`가 이미 포그라운드 서비스라 앱이 포그라운드로 취급되고, `startService`(지금의 `answer`, `stop`)가 막히지 않는다.
- **안 되는 경우:** 달리는 코스가 없으면 `fail`이다. 묻는 중이 아닌데 `stop_by`, `back_to_course`, `resume`가 오면 `fail`로 "지금은 묻고 있지 않다"를 알린다.
- **`end_course`:** 정차 여부와 상관없이 받는다. 화면의 "코스 끝내기" 버튼도 주행 중에 눌린다(`DriveScreen`, 정차 조건 없음).
- **`stop_by` / `back_to_course`:** 답을 받으면 Gleo가 짧게 말한다. 예: "Okay, I'll wait while you stop by."
- **11-12 문제의 해결:** 이 함수로 "2분할에서 이탈 질문에 답할 수 없다"는 문제가 풀린다.

## 4. 하지 않는 것

- 함께 달리기를 음성으로 다루지 않는다(코드로 참여, 친구 초대, 위치 공유 동의). 동의 문구를 화면에서 읽고 눌러야 하는 흐름이라 음성으로 대신하지 않는다.
- 달린 길 기록, 설정, 공유를 음성으로 다루지 않는다.
- 앱 안에 따로 음성 인식, 호출어, LLM을 두지 않는다. 모두 Gleo 쪽 일이다.
- 음성으로 화면 배치(3분할·2분할)를 바꾸지 않는다. 앱이 창을 나눌 수 없다.
- Gleo 대화창의 모양(목록, 카드, 칩)을 앱이 정하려 하지 않는다. 그런 문서가 없다.
- Pleos 비공개 API, 시스템 앱 분석, AAR 분석은 하지 않는다(라이선스 3.4).

## 5. 이렇게 되면 실패다

- 음성으로 부르는 바람에 달리는 중에 에움길 화면이 튀어나와 내비를 가린다.
- Gleo와 에움길이 같은 말을 동시에 한다(출발은 `silent`로 막는다).
- 7초를 넘겨 Gleo가 "처리하지 못했다"고 말한다. 코스 목록을 처음 읽을 때 느리면 생길 수 있다.
- 다른 앱이 같은 방송을 보내 주행 중에 코스를 갈아 끼운다(6절 보안).

## 6. 구조와 제약

- **층 나누기:** 기존 규칙대로 `ai.pleos.*` 문자열과 API는 `com.byway.platform` 안에만 둔다.
  - **core(순수):**
    - `ACTION_PARAM` 읽기. 맨 JSON과 따옴표로 한 번 더 감싼 JSON 둘 다 받는다.
    - 무드 이름 ↔ `MOOD_KEYS` 대응
    - 이름 맞추기
    - 찾기 결과 정렬과 자르기
    - 함수별 판단: 상태를 넣으면 무엇을 할지와 Callback 내용이 나온다
    - Callback JSON 만들기
  - **app:**
    - 방송 수신기 `GleoReceiver`. exported이고, intent-filter 3개를 단다.
    - `goAsync()`로 받아 6초 안에 마친다. 6초를 넘기면 `fail`로 timeout을 알린다.
    - 판단 결과를 `DriveService`와 `CourseCatalog`에 잇는다.
  - **platform:** `PleosGleo.respond(context, response)`가 Callback 방송을 보낸다.
- **스키마**
  - `app/src/main/assets/schema.json`. `packageName`은 `com.byway`다.
  - `invokeWord`는 `"에움길"`이다. 문서 예시에만 있고 필드 설명에는 없다. 넣어 두고 승인 때 확인한다.
  - `version`은 앱 `versionName`과 같게 둔다.
  - 시험 하나가 schema.json 함수 이름과 매니페스트 action, 코드 상수가 서로 맞는지 확인한다.
- **언어:** `message`는 영어 완전한 문장이다. 코스 이름, 지역, 구간 이름은 한국어 그대로 둔다.
  - 운전자가 하는 말과 듣는 말은 한국어다. 에움길 음성도 한국어다.
  - 영어는 Gleo LLM이 읽는 스키마 설명과 결과 문장에만 쓴다.
- **인자가 영어로 바뀌어 올 위험:** 설명이 영어라 LLM이 "팔당 노을"을 영어로 옮겨 넘길 수 있다. 막는 방법은 세 가지다 **[제안]**.
  - `searchTerm`과 `courseName` 설명에 "사용자가 말한 그대로, 원래 언어로 둔다"는 뜻을 적는다. 이는 값의 성격을 설명하는 문장이라 조종 문구 금지에 걸리지 않는다고 본다.
  - 무드는 영어 enum으로 받으므로 "노을"이 `sunset`으로 와도 맞는다.
  - 문서 예시에서도 Gleo는 `"미팅"`, `"SBS 파워FM"`을 한국어 그대로 넘긴다.
  - 그래도 영어 이름이 오면 `fail`이 아니라 `asking`으로 직전 찾기 결과를 다시 보여 준다.
- **Callback 대상:** 문서 예시대로 패키지를 지정하지 않은 방송으로 보낸다. 문서에는 "Gleo AI는 Android 서비스이므로 broadcast 수신이 가능"하다고 적혀 있다.
  - 수신 쪽 패키지가 문서에 없어서 `setPackage`를 달지 않는다 **[제안]**. 승인 단계에서 확인할 위험 항목으로 둔다.
- **보안**
  - 수신기는 Gleo가 부를 수 있게 exported여야 하므로 차 안의 다른 앱도 부를 수 있다.
  - 할 수 있는 일은 화면 버튼과 같은 범위다. 다만 출발은 주행 중에도 된다(PM 11-13). 코스가 없을 때만 출발하고, 달리는 코스는 갈아 끼우지 않는다.
  - 그래서 차 안의 다른 앱이 할 수 있는 최악의 일은 "코스가 없을 때 코스를 하나 시작한다"이다. 운전자는 음성 안내로 그 사실을 알고, "코스 그만"이나 화면 버튼으로 끝낼 수 있다.
  - 보내는 쪽 권한은 문서에 없어서 걸지 않는다 **[제안]**. 기획서 위험 항목에 적는다.
- **기존 동작 보존:** 화면, 출발 화면 고르기, 이탈 질문 15번 대기는 바꾸지 않는다. 음성은 같은 동작을 부르는 또 하나의 입구일 뿐이다.

## 7. builder에게 맡기는 것

- 영어 `description`과 `message` 문구. 문서 규칙(한두 문장, 예시 금지)을 지킨다.
- 클래스와 파일 이름, 디버그 로그
- 이름 맞추기에서 같은 점수가 나올 때의 순서(카탈로그 순서면 된다)

## 8. 확인 방법

- **단위 시험:** 인자 읽기 두 모양, 무드 대응, 이름 맞추기 네 단계, 찾기 결과 3개와 더미 뒤로 보내기, 함수별 판단(정차·주행·달리는 중·묻는 중), Callback JSON 모양, schema.json과 매니페스트 맞춤
- **에뮬레이터(Gleo 흉내):**
  ```
  adb shell am broadcast -a FIND_DRIVING_COURSES -p com.byway --es ACTION_PARAM '{"searchTerm":"노을"}'
  adb shell am broadcast -a START_DRIVING_COURSE -p com.byway --es ACTION_PARAM '{"courseName":"팔당 노을"}'
  adb shell am broadcast -a CONTROL_RUNNING_COURSE -p com.byway --es ACTION_PARAM '{"command":"status"}'
  ```
  - Callback은 디버그 빌드에서만 그 내용을 logcat에 한 줄로 남겨 확인한다.
  - 이탈 장면은 기존 `detour.py`로 만든 뒤 `stop_by`와 `back_to_course`를 보낸다.
  - 2분할(에움길이 가려진 상태)에서 출발과 이탈 답하기가 되는지 본다.
- **실제 Gleo:** Pleos 스키마 승인이 있어야 하므로 11-15 전에는 확인하지 못한다. 기획서에는 "스키마 작성 완료, 승인 신청 단계"로 쓴다.
  - 승인 뒤에 확인할 것: Gleo가 한국어 인자를 그대로 넘기는지, 최종 답을 한국어로 말하는지, Callback 방송이 도착하는지
- **에뮬레이터 실측 항목:** 2분할로 가려졌고 달리는 코스가 없을 때 `START`가 출발하는지 아니면 `fail`이 되는지. 앱이 꺼진 상태에서 6초 안에 답하는지.

## 9. 시연용 Gleo 흉내 앱 [PM 11-13 "시연용 가짜 Gleo도"]

실제 Gleo는 승인 전이라 쓸 수 없다. 그래서 11-15 시연 영상에 "말해서 찾고 출발" 장면을 넣으려고 Gleo 역할을 하는 작은 시험용 앱을 따로 만든다.

- **위치와 성격:** 저장소의 별도 모듈 `tools/gleo-sim`이고 패키지는 `com.byway.gleosim`이다 **[제안]**.
  - 디버그 빌드로 에뮬레이터에만 설치한다. 제출 APK와 `apk/`에는 들어가지 않는다.
- **모양**
  - 다른 앱 위에 떠 있는 작은 대화창이다. 내비가 넓게 떠 있어도 보인다.
  - 에뮬레이터에서 `adb shell appops set com.byway.gleosim SYSTEM_ALERT_WINDOW allow`로 권한을 준다.
  - 화면에 "시연용 Gleo 흉내 · 실제 Gleo 아님"을 늘 보인다.
  - Gleo 로고, 크롬 애니메이션, 브랜드 색은 쓰지 않는다(실제 제품처럼 보이게 하지 않는다).
- **동작**
  - 음성 인식 대신 미리 정한 한국어 말 버튼을 둔다.
    - "노을 보기 좋은 코스 찾아줘"
    - "1번 코스로 출발해줘"
    - "잠깐 들를게"
    - "코스로 돌아갈래"
    - "얼마나 남았어?"
    - "코스 그만할래"
  - 버튼을 누르면 사용자 말풍선을 보인다.
  - 이어서 Gleo가 보낼 것과 **같은 방송**(action = 함수 이름, `setPackage("com.byway")`, `ACTION_PARAM`)을 보낸다. 실제 Gleo의 LLM 역할은 미리 짠 말 → 함수 호출 표가 대신한다.
  - `APP_ACTION_RESPONSE` 방송을 받아 답 말풍선을 보인다.
    - 답 문장은 `status`와 `data`로 한국어 틀을 채워 만든다(예: "노을 코스 3곳을 찾았어요: 팔당 노을, …").
    - `mode`가 `voice`일 때만 Pleos TTS로 읽는다. `silent`면 읽지 않아 에움길 안내와 겹치지 않는다.
- **이 앱이 증명하는 것:** 에움길의 스키마, 수신기, Callback이 Gleo 규격대로 오간다는 것. 실제 Gleo의 말 이해 능력을 증명하지는 않는다. 영상 자막에도 그렇게 적는다.
- **영상:** 이 앱이 생기면 기획서 세션과 함께 B 클립을 하나 더 찍는다. 장면은 "2분할 주행 중 '잠깐 들를게'"와 "정차 중 '노을 코스 찾아줘' → '1번 코스로 출발해줘'"이다.

## 10. 정해진 것 (PM 11-13)

- 함수 세 개: 찾기, 출발, 달리는 중 조작. 함께 달리기는 뺀다.
- 주행 중에도 음성 출발을 허용한다.
- 시연용 Gleo 흉내 앱을 만든다(9절).
