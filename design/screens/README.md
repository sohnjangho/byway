# 에움길 화면 시안

Pleos Connect 앱 창 시안이다. 2/3 창 1700×1184, 전체 2560×1210, 1/3 창 841×1210 세 크기로 홈, 둘러보기, 랭킹, 내 코스를 본다. 손으로 고치는 원본은 야간 8장이고 나머지는 도구가 만든다.

| 파일 | 화면 |
|---|---|
| `index.html` | 모아 보기. 화면 탭, 야간·주간 전환, 전체 보기 |
| `Main.dc.html` | 메인. 왼쪽 코스 이름과 설명, 주변 정보, 오른쪽 위성지도와 경로. 경로를 밀면 풍경 |
| `Browse.dc.html` | 둘러보기. 왼쪽 한국 지도, 오른쪽 카드. 카드를 누르면 지도가 그 코스로 확대되고, 지역 칩을 누르면 그 지역으로 확대된다 |
| `Drive.dc.html` | 내비 옆 주행 칸 |
| `DriveAuto.dc.html` | 구상 화면. 자율주행 연동 시 주행 칸 |
| `Summary.dc.html` | 정차 요약 |
| `Ranking.dc.html` | 랭킹. 왼쪽 순위와 작은 한국 지도(코스 위치), 오른쪽은 홈처럼 이어지는 위성 지도와 경로 |
| `Me.dc.html` | 내 코스. 큰 지도에 내 코스 경로와 번호, 지역·도시 이름. 코스를 누르거나 확대하면 경로와 설명이 나온다 |
| `Waiting.dc.html` | 주행 중 대기 |
| `*Full`, `*Third` | 홈, 둘러보기, 랭킹, 내 코스의 전체 화면과 1/3 창. `tools/make-day.py`가 2/3 원본에서 만든다. 메뉴 링크는 같은 창 크기 판으로 이어진다 |
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
- 추천 표시는 `.picks`와 `.pick`이다. 값은 코스 파일의 `tags.seasons`, `tags.ages`, `tags.moods`다.
- 밀어서 넘기는 칸은 `.swipe`와 `.swipe-page`다. 첫 장은 경로, 다음 장은 풍경이다. 스크립트 없이 CSS 스크롤로 넘어간다. 메인은 같은 방식을 `.scene-swipe`로 쓴다.
- 메인 바탕은 `images/scene/<id>(-night).jpg`다. `tools/make-scenes.py`가 위성 사진을 경로 칸에 맞춰 만들고, 경로 선은 `courses.js`의 `scene.sections`로 그린다.
- 메뉴는 `.rail`이다. 전체와 2/3 창은 왼쪽, 1/3 창은 위쪽이고 지도 위에 반투명하게 얹는다(`.screen-over`).
- 분위기 효과는 `effects.css`다. `tools/make-effects.py`가 만들므로 직접 고치지 않는다. 효과는 왼쪽 정보 칸에만 들어가고 지도와 경로에는 넣지 않는다.
- 주변 정보는 `tools/nearby.json`의 값이다. 고친 뒤 `tools/make-screen-data.py`를 돌린다.
- 한국 전체 지도는 `images/atlas/korea(-night).jpg`다. `tools/make-atlas.py`가 장면과 같은 위성 타일로 만들고, 코스 선·코스 상자·장면 자리·지명 좌표를 `images/atlas/atlas.json`에 낸다. `tools/make-screen-data.py`가 이를 `courses.js`의 `atlas`와 `window.KOREA`로 옮긴다.
- 지도 확대는 `.atlas-world` 하나에 `translate`와 `scale`을 줘서 움직인다. 화면 스크립트의 `FOCUS`가 창 크기별로 지도가 들어갈 자리다. 코스를 고르면 그 코스의 장면 그림(`images/scene`)이 지도 위 제자리에 겹쳐 또렷해진다.
- 지도 선은 CSS 확대 안에서 `non-scaling-stroke`가 듣지 않아 `--s`(배율)로 나눠 굵기를 맞춘다.
- 2016년판 위성 원본에는 남해·동해 쪽에 회색 이음매가 있다. 지도를 어둡게 깔아 덜 보이게 했을 뿐 없애지는 못했다.
