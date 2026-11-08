// 센터 화면 속 에움길. 정차 중에는 시안 화면(design/screens)을 그대로 띄워 실제로 눌러 조작하고,
// '내비에 코스 입히기'를 누르면 내장 내비(왼쪽)와 에움길 주행 칸(오른쪽)으로 나뉜다.
// 문구는 core/engine/Messages.kt, 차 안 연출 값은 AmbienceFeature, 음악 검색어는 ConnectedMusic 을 따른다.
const SCREENS = '../../design/screens/';
export const SECTION_INFO = [
  { mood: 'forest', cabin: '창문 2단 · 풍량 3단 · 조명 40%', window: 0.45, light: '#9fe0a8', music: '숲길 드라이브 음악', dir: '남한산성 방면' },
  { mood: 'city', cabin: '창문 닫음 · 풍량 3단 · 조명 30%', window: 0, light: '#9fb6ff', music: '도심 드라이브 음악', dir: '팔당대교 방면' },
  { mood: 'river', cabin: '창문 2단 · 풍량 2단 · 조명 70%', window: 0.45, light: '#ffc27a', music: '바닷가 드라이브 음악', dir: '두물머리 방면' },
];
export const SAY = { start: (n) => `${n} 코스를 시작합니다.`, section: (n) => `${n} 구간입니다.`, nextLeg: '다음 구간으로 이어갑니다.', spot: (n) => `잠시 후 ${n}입니다.`, finished: '코스를 끝까지 달렸습니다.' };

export function createApp(root, { course, navmap, onApply, onSay }) {
  const COURSE = window.COURSES.find((c) => c.id === course.id), TOTAL = COURSE.km;
  root.insertAdjacentHTML('beforeend', `
    <div class="appfull" id="appFull"><iframe id="appFrame" src="${SCREENS}MainFullDay.dc.html"></iframe></div>
    <div class="nav" id="nav"><svg class="navmap" viewBox="0 0 1680 2400"><g id="navg"></g></svg><div class="navcar"></div>
      <div class="tbt"><div class="main"><svg id="tbtArrow" viewBox="0 0 76 96"></svg><div><div class="dist" id="tbtDist"></div><div class="dir" id="tbtDir"></div></div></div>
        <div class="lanes"><svg viewBox="0 0 34 46"><path d="M17 44 V12 M5 22 L17 8 L29 22" stroke="#fff" stroke-width="5" fill="none"/></svg><svg viewBox="0 0 34 46"><path d="M17 44 V12 M5 22 L17 8 L29 22" stroke="#666" stroke-width="5" fill="none"/></svg></div></div>
      <div class="next"><svg id="nextArrow" viewBox="0 0 76 96"></svg><span id="nextDist"></span></div>
      <div class="navbtn" style="left: 716px; top: 24px; flex-direction: column; font-size: 28px; line-height: 28px;">2D<span style="font-size: 26px;">▲</span></div>
      <div class="eta"><div class="dots">···</div><div class="row" style="margin-top: 22px;"><span class="time" id="etaTime"></span><span class="left" id="etaLeft"></span></div>
        <div class="row" style="margin-top: 12px;"><span class="road" id="etaRoad"></span><span class="left">▬ 78%</span></div>
        <div class="bar"><i style="left: 30%"></i><i style="left: 72%"></i><b id="etaPos" style="left: 0%"></b></div>
        <div class="end"><span>⌕</span><strong>안내 종료</strong></div></div>
    </div>
    <div class="pane" id="pane"><section class="drive" id="drive">
      <div class="glow" id="glow"></div>
      <div class="drive-head"><span class="t-drive-line bold">${COURSE.name}</span><span class="t-label ink-2 num" id="secno"></span></div>
      <h1 class="t-drive-section" id="secname"></h1>
      <div class="drive-remain"><span class="t-drive-number num" id="remain"></span><span class="t-drive-unit ink-2">km</span><span class="t-drive-line ink-2">남은 거리</span></div>
      <div class="sec-bar sec-bar-lg" id="bar">${COURSE.scene.sections.map((s) => `<i class="sec-${s.mood}"><b></b></i>`).join('')}</div>
      <div class="drive-line" id="lineBox"><p class="t-drive-line" id="line"></p></div>
      <div class="info-line"><b id="cabin"></b><span>차 안 연출 · 창문·풍량·조명</span></div>
      <div class="info-line"><b id="music"></b><span>연결된 음악 앱에 재생 요청</span></div>
      <button type="button" class="btn-quiet t-drive-line" id="endBtn">코스 끝내기</button>
    </section><div class="toast" id="toast"><b>내비에 코스를 입혔습니다</b>경유지를 2곳씩 나눠 이어서 안내합니다</div></div>
    <div class="apparea" id="sumApp" style="display:none"><iframe src="${SCREENS}SummaryDay.dc.html"></iframe></div>`);
  const $ = (id) => root.querySelector('#' + id);
  const secs = COURSE.scene.sections.map((s, i, a) => ({ ...s, km: TOTAL * (course.sections[i].endIndex - course.sections[i].startIndex + 1) / course.path.length }));
  let acc = 0; secs.forEach((s) => { s.from = acc; acc += s.km; s.to = acc; });
  [...$('bar').children].forEach((el, i) => (el.style.flex = secs[i].km.toFixed(1)));
  /* 주행 내비 */
  const r = navmap.route, cum = [0], turns = [];
  for (let i = 1; i < r.length; i++) cum.push(cum[i - 1] + Math.hypot(r[i][0] - r[i - 1][0], r[i][1] - r[i - 1][1]));
  const ang = (a, b) => Math.atan2(b[1] - a[1], b[0] - a[0]);
  for (let i = 2; i < r.length - 2; i++) { let d = ang(r[i], r[i + 2]) - ang(r[i - 2], r[i]); d = Math.atan2(Math.sin(d), Math.cos(d)); if (Math.abs(d) > 0.7 && (!turns.length || i - turns[turns.length - 1].i > 4)) turns.push({ i, at: cum[i], dir: d > 0 ? 'right' : 'left' }); }
  const line = (pts) => 'M' + pts.map((p) => p[0] + ' ' + p[1]).join('L'), g = [];
  navmap.water.forEach((w) => g.push(`<path d="${line(w)}Z" fill="#b7d1ea"/>`));
  const W = { hw: [20, '#c9a24a', '#f8d27a'], main: [15, '#c3c7cd', '#ffffff'], link: [10, '#c3c7cd', '#ffffff'], minor: [9, '#cfd2d7', '#ffffff'] };
  ['minor', 'link', 'main', 'hw'].forEach((c) => { const [w, e, f] = W[c], d = navmap.roads[c].map(line).join(''); g.push(`<path d="${d}" fill="none" stroke="${e}" stroke-width="${w + 4}" stroke-linecap="round" stroke-linejoin="round"/><path d="${d}" fill="none" stroke="${f}" stroke-width="${w}" stroke-linecap="round" stroke-linejoin="round"/>`); });
  g.push(`<path d="${line(r)}" fill="none" stroke="#fff" stroke-width="30" stroke-linecap="round" stroke-linejoin="round"/><path d="${line(r)}" fill="none" stroke="#2f7cf6" stroke-width="20" stroke-linecap="round" stroke-linejoin="round"/><path id="routeDone" fill="none" stroke="#9aa3ad" stroke-width="20" stroke-linecap="round" stroke-linejoin="round"/>`);
  $('navg').innerHTML = g.join('');
  const arrow = (dir) => dir === 'left' ? '<path d="M58 92 V44 Q58 30 44 30 H22" stroke="#fff" stroke-width="12" fill="none"/><path d="M28 10 L4 30 L28 50 Z" fill="#fff"/>'
    : dir === 'right' ? '<path d="M18 92 V44 Q18 30 32 30 H54" stroke="#fff" stroke-width="12" fill="none"/><path d="M48 10 L72 30 L48 50 Z" fill="#fff"/>'
    : '<path d="M38 92 V24" stroke="#fff" stroke-width="12" fill="none"/><path d="M14 30 L38 4 L62 30 Z" fill="#fff"/>';
  const fmt = (m) => (m >= 1000 ? (m / 1000).toFixed(1) + 'km' : Math.max(10, Math.round(m / 10) * 10) + 'm');
  const at = (s) => { let i = cum.findIndex((c) => c >= s); if (i <= 0) i = 1; const x = (s - cum[i - 1]) / (cum[i] - cum[i - 1] || 1); const a = r[Math.max(0, i - 2)], b = r[Math.min(r.length - 1, i + 1)]; return { p: [r[i - 1][0] + (r[i][0] - r[i - 1][0]) * x, r[i - 1][1] + (r[i][1] - r[i - 1][1]) * x], i, h: Math.atan2(b[1] - a[1], b[0] - a[0]) }; };
  /* 스팟: 코스 파일의 스팟 가운데 경로 위 가까운 점 */
  const spots = course.spots.map((s) => { let bi = 0, bd = Infinity; course.path.forEach((p, i) => { const d = (p[0] - s.lat) ** 2 + (p[1] - s.lon) ** 2; if (d < bd) { bd = d; bi = i; } }); return { name: s.name, at: cum[bi] }; });

  const app = { applied: false, finished: false, section: -1, saidSpots: new Set(), mode: 'browse' };
  /* 시안 화면에서 '내비에 코스 입히기'를 누르면 Drive 화면으로 넘어가려 한다. 그 순간 가로채 주행 칸으로 바꾼다 */
  $('appFrame').addEventListener('load', () => {
    const path = $('appFrame').contentWindow.location.pathname;
    if (/Drive/.test(path)) { $('appFrame').src = SCREENS + 'MainFullDay.dc.html'; apply(); }
  });
  function apply() {
    if (app.applied) return;
    app.applied = true; app.appliedAt = performance.now();
    onSay(SAY.start(COURSE.name)); onApply && onApply();
  }
  $('endBtn').addEventListener('click', () => { app.finished = true; });
  app.update = ({ gear, routeS, zoneIndex, zoneFrac, kmh }) => {
    const split = app.applied && !app.finished;
    $('appFull').style.display = !app.applied ? 'block' : 'none';
    $('nav').style.display = $('pane').style.display = split ? 'block' : 'none';
    $('pane').style.display = split ? 'flex' : 'none';
    $('sumApp').style.display = app.finished && gear === 'P' ? 'block' : 'none';
    $('toast').style.opacity = app.applied && performance.now() - app.appliedAt < 4500 ? 1 : 0;
    if (!split) return;
    /* 이어 붙인 시연 경로의 구간 진행률을 실제 코스 km 로 바꾼다 */
    const si = Math.max(0, zoneIndex - 1), sec = secs[si], km = zoneIndex === 0 ? 0 : sec.from + sec.km * zoneFrac;
    if (zoneIndex >= 1 && si !== app.section) {
      app.section = si; onSay(SAY.section(sec.name));
      if (si > 0) setTimeout(() => onSay(SAY.nextLeg), 3500);
      app.onSection && app.onSection(si);
    }
    const info = SECTION_INFO[Math.max(0, app.section)];
    $('secno').textContent = `구간 ${Math.max(0, app.section) + 1}/${secs.length}`;
    $('secname').textContent = secs[Math.max(0, app.section)].name; $('secname').style.color = `var(--section-${info.mood})`;
    $('drive').style.background = `var(--air-${info.mood})`;
    $('glow').style.opacity = info.mood === 'river' ? 0.9 : 0;
    $('remain').textContent = (TOTAL - km).toFixed(1);
    [...$('bar').children].forEach((el, i) => { const f = Math.min(1, Math.max(0, (km - secs[i].from) / secs[i].km)); el.className = `sec-${secs[i].mood} ${f >= 1 ? 'is-done' : 'is-todo'}`; el.firstChild.style.width = (f * 100).toFixed(1) + '%'; });
    $('cabin').textContent = app.section < 0 ? '출발 전' : info.cabin;
    $('music').textContent = app.section < 0 ? '대기' : `'${info.music}'`;
    /* 내비: 실제 코스 경로 위 같은 자리 */
    const { p, i, h } = at(routeS);
    root.querySelector('#navg').setAttribute('transform', `translate(840 1900) rotate(${(-h * 180) / Math.PI - 90}) scale(1.15) translate(${-p[0]} ${-p[1]})`);
    $('routeDone').setAttribute('d', line(r.slice(0, i).concat([p])));
    const ahead = turns.filter((x) => x.at > routeS + 5);
    $('tbtArrow').innerHTML = arrow(ahead[0] && ahead[0].at - routeS < 2000 ? ahead[0].dir : 'straight');
    $('tbtDist').textContent = ahead[0] ? fmt(ahead[0].at - routeS) : '';
    $('tbtDir').textContent = info.dir;
    $('nextArrow').innerHTML = arrow(ahead[1] ? ahead[1].dir : 'straight'); $('nextDist').textContent = ahead[1] ? fmt(ahead[1].at - routeS) : '';
    const leftKm = TOTAL - km, mins = Math.round(leftKm / 45 * 60), now = new Date(Date.now() + mins * 60000);
    $('etaTime').textContent = `${now.getHours() % 12 || 12}:${String(now.getMinutes()).padStart(2, '0')} ${now.getHours() < 12 ? 'AM' : 'PM'}`;
    $('etaLeft').textContent = `${mins}분 · ${leftKm.toFixed(1)}km`; $('etaRoad').textContent = secs[Math.max(0, app.section)].name;
    $('etaPos').style.left = ((km / TOTAL) * 100).toFixed(1) + '%';
    /* 스팟 예고: 같은 구간 안에서 300m 앞 */
    const next = spots.find((s) => s.at > routeS && s.at - routeS < 300 && !app.saidSpots.has(s.name));
    if (next && kmh > 5) { app.saidSpots.add(next.name); onSay(SAY.spot(next.name)); }
    $('line').textContent = next ? SAY.spot(next.name).replace('.', '') : app.section === 1 ? '팔당대교를 건너면 다음 구간으로 이어갑니다' : '좋은 길 되세요';
  };
  app.cumAt = (i) => cum[Math.min(i, cum.length - 1)];
  app.finish = () => { if (!app.finished) { app.finished = true; onSay(SAY.finished); } };
  return app;
}
