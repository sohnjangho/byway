# 에움길 화면 시안

Pleos Connect 앱 창 시안이다. 2/3 창 1700×1184, 전체 2560×1210, 1/3 창 841×1210 세 크기로 메인을 본다. 손으로 고치는 원본은 야간 8장이고 나머지는 도구가 만든다.

| 파일 | 화면 |
|---|---|
| `index.html` | 모아 보기. 화면 탭, 야간·주간 전환, 전체 보기 |
| `Main.dc.html` | 메인. 왼쪽 코스 이름과 설명, 주변 정보, 오른쪽 위성지도와 경로. 경로를 밀면 풍경 |
| `Browse.dc.html` | 코스 고르기. 경로 카드 격자 |
| `Drive.dc.html` | 내비 옆 주행 칸 |
| `DriveAuto.dc.html` | 구상 화면. 자율주행 연동 시 주행 칸 |
| `Summary.dc.html` | 정차 요약 |
| `Ranking.dc.html` | 랭킹. 왼쪽 순위, 오른쪽 고른 코스 |
| `Me.dc.html` | 내 코스 |
| `Waiting.dc.html` | 주행 중 대기 |
| `MainFull`, `MainThird` | 전체 화면, 1/3 창. `tools/make-day.py`가 `Main`에서 만든다 |
| `MainRain` 등 11장 | 날씨, 시간대, 계절 분위기. `tools/make-day.py`가 `Main`에서 만든다 |
| `*Day.dc.html` | 주간판. `tools/make-day.py`가 만든다. 직접 고치지 않는다. 밤 분위기는 주간판이 없다 |

## 고치는 법

- 색과 글자 크기는 `tokens.css`에서 온다. 생성 파일이므로 고치지 않는다. 값의 기준은 `../tokens.json`과 `../system/README.md`다.
- 공통 모양은 `app.css` 한 곳에 있다. 주간과 야간이 달라야 하는 효과는 `.stage[data-theme="night"]`, `.stage[data-theme="day"]` 아래에 둔다.
- 화면은 야간 원본만 고치고 `python3 tools/make-day.py`를 다시 돌린다. 테마, 사진 경로(`-night.jpg` 제거), 화면 간 링크가 주간판으로 바뀐다.
- 코스 값은 `courses.js`에서 읽는다. `tools/make-screen-data.py`가 앱 코스 파일에서 만들므로 직접 고치지 않는다. 코스 선은 `images/paldang_overlay.json`의 값이다.
- 풍경 사진은 `images/scenery`다. 앱 자산을 가리키는 링크다.
- 지도 바탕과 코스 선 SVG는 `.map-canvas` 한 박스를 같이 쓴다. `.map`의 `aspect-ratio`는 `viewBox`와 같아야 한다.
- `support.js`는 브라우저에서 `.dc.html`을 돌리는 렌더러다.
- 코스 커버는 `images/covers`다. 앱의 `app/src/main/assets/covers`를 가리키는 링크라 복사본이 없다. `tools/make-covers.py`만 돌리면 앱과 시안이 함께 바뀐다.
- 코스 설명 뒤의 흐린 배경은 `.soft`와 `.soft-img`로 쓴다. 이 위 글자는 `ink`만 쓴다. 파일은 `images/covers/<id>-soft.jpg`다.
- 추천 표시는 `.picks`와 `.pick`이다. 값은 코스 파일의 `tags.seasons`, `tags.ages`, `tags.moods`다.
- 밀어서 넘기는 칸은 `.swipe`와 `.swipe-page`다. 첫 장은 경로, 다음 장은 풍경이다. 스크립트 없이 CSS 스크롤로 넘어간다. 메인은 같은 방식을 `.scene-swipe`로 쓴다.
- 메인 바탕은 `images/scene/<id>(-night).jpg`다. `tools/make-scenes.py`가 위성 사진을 경로 칸에 맞춰 만들고, 경로 선은 `courses.js`의 `scene.sections`로 그린다.
- 메뉴는 `.rail`이다. 전체와 2/3 창은 왼쪽, 1/3 창은 위쪽이고 지도 위에 반투명하게 얹는다(`.screen-over`).
- 분위기 효과는 `effects.css`다. `tools/make-effects.py`가 만들므로 직접 고치지 않는다. 효과는 왼쪽 정보 칸에만 들어가고 지도와 경로에는 넣지 않는다.
- 주변 정보는 `tools/nearby.json`의 값이다. 고친 뒤 `tools/make-screen-data.py`를 돌린다.
