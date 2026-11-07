# 에움길 화면 시안

Pleos Connect 앱 창 1700×1184 기준 시안이다. 야간 원본 8장과, 원본에서 만든 주간판 8장이 있다.

| 파일 | 화면 |
|---|---|
| `index.html` | 모아 보기. 화면 탭, 야간·주간 전환, 전체 보기 |
| `Main.dc.html` | 홈 |
| `Preview.dc.html` | 코스 상세. 위성·코스 그림 전환 |
| `Drive.dc.html` | 내비 옆 주행 칸 |
| `DriveAuto.dc.html` | 구상 화면. 자율주행 연동 시 주행 칸 |
| `Summary.dc.html` | 정차 요약 |
| `Ranking.dc.html` | 랭킹. 코스 15개 중 위 5개 |
| `Me.dc.html` | 내 코스 |
| `Waiting.dc.html` | 주행 중 대기 |
| `*Day.dc.html` | 주간판. `tools/make-day.py`가 만든다. 직접 고치지 않는다 |

## 고치는 법

- 색과 글자 크기는 `tokens.css`에서 온다. 생성 파일이므로 고치지 않는다. 값의 기준은 `../tokens.json`과 `../system/README.md`다.
- 공통 모양은 `app.css` 한 곳에 있다. 주간과 야간이 달라야 하는 효과는 `.stage[data-theme="night"]`, `.stage[data-theme="day"]` 아래에 둔다.
- 화면은 야간 원본만 고치고 `python3 tools/make-day.py`를 다시 돌린다. 테마, 사진 경로(`-night.jpg` 제거), 화면 간 링크가 주간판으로 바뀐다.
- 코스 수치는 `images/course_metrics.json`, 코스 선은 `images/paldang_overlay.json`의 값이다. 이 파일이 바뀌면 화면에 복사된 값도 같이 고친다.
- 지도 바탕과 코스 선 SVG는 `.map-canvas` 한 박스를 같이 쓴다. `.map`의 `aspect-ratio`는 `viewBox`와 같아야 한다.
- `support.js`는 브라우저에서 `.dc.html`을 돌리는 렌더러다.
- 코스 커버는 `images/covers`다. 앱의 `app/src/main/assets/covers`를 가리키는 링크라 복사본이 없다. `tools/make-covers.py`만 돌리면 앱과 시안이 함께 바뀐다.
- 코스 설명 뒤의 흐린 배경은 `.soft`와 `.soft-img`로 쓴다. 이 위 글자는 `ink`만 쓴다. 파일은 `images/covers/<id>-soft.jpg`다.
- 추천 표시는 `.picks`와 `.pick`이다. 값은 코스 파일의 `tags.seasons`, `tags.ages`, `tags.moods`다.
- 코스 상세의 풍경 칸은 사진이 정해질 때까지 자리만 둔다.
