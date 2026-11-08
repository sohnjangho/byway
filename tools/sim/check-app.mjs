// 시뮬레이터 2단계 점검: 화면 속 에움길을 눌러 코스를 고르고 내비에 입힌 뒤 달려 구간 반응을 본다.
// 사용: node tools/sim/check-app.mjs <port> <url> <출력 폴더> [touch]
// touch 를 붙이면 iPad 가로(1180×820) 터치 기기로 흉내 내고, 화면·단추·앱을 모두 손가락 탭으로 누른다.
import fs from 'fs';
const [port, url, out, mode] = process.argv.slice(2);
const TOUCH = mode === 'touch';
fs.mkdirSync(out, { recursive: true });
const tgt = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(tgt.webSocketDebuggerUrl); await new Promise((r) => (ws.onopen = r));
let id = 0; const p = {}; const logs = [];
ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && p[d.id]) { p[d.id](d.result || d); delete p[d.id]; } if (d.method === 'Runtime.exceptionThrown') logs.push(d.params.exceptionDetails.exception?.description || d.params.exceptionDetails.text); };
const c = (method, params = {}) => new Promise((r) => { const i = ++id; p[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const run = async (expression) => (await c('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.value;
const shot = async (name) => fs.writeFileSync(`${out}/${name}.jpg`, Buffer.from((await c('Page.captureScreenshot', { format: 'jpeg', quality: 85 })).data, 'base64'));
/* 터치: 요소의 화면 위 사각형(3D 변환·iframe 포함)을 받아 가운데를 손가락으로 누른다 */
const touch = (type, pts) => c('Input.dispatchTouchEvent', { type, touchPoints: pts.map(([x, y]) => ({ x, y })) });
const tap = async (x, y, hold = 60) => { await touch('touchStart', [[x, y]]); await wait(hold); await touch('touchEnd', []); };
const quadOf = async (expr) => { const o = await c('Runtime.evaluate', { expression: expr }); if (!o.result?.objectId) return null; const q = await c('DOM.getContentQuads', { objectId: o.result.objectId }); const v = q.quads?.[0]; return v ? [(v[0] + v[2] + v[4] + v[6]) / 4, (v[1] + v[3] + v[5] + v[7]) / 4] : null; };
const tapEl = async (expr, hold) => { const pt = await quadOf(expr); if (!pt) return 'none'; await tap(pt[0], pt[1], hold); return 'ok'; };
const inFrame = (body) => `(() => { const d = document.querySelector('.pleos #appFrame').contentDocument; return ${body}; })()`;
const frame = (js) => run(`(() => { const d = document.querySelector('.pleos #appFrame').contentDocument; return (${js})(d); })()`);
await c('Emulation.setDeviceMetricsOverride', TOUCH ? { width: 1180, height: 820, deviceScaleFactor: 1, mobile: true } : { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
if (TOUCH) await c('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 5 });
await c('Runtime.enable'); await c('Page.enable'); await c('DOM.enable'); await c('Page.navigate', { url });
for (let i = 0; i < 120 && !(await run('!!window.sim')); i++) await wait(500);
await wait(3000);
const report = { said: [] };
await run(`(() => { const s = sim.say; window.__said = []; })()`);
if (TOUCH) {
  /* 터치 끌기로 둘러보기, 가운데 화면 탭으로 화면 모드, '운전석으로' 단추·화면 밖 탭으로 나가기 */
  const y0 = await run('sim.state.targetYaw');
  await touch('touchStart', [[700, 500]]); for (let i = 1; i <= 8; i++) { await touch('touchMove', [[700 + i * 25, 500]]); await wait(16); } await touch('touchEnd', []);
  report.dragYaw = +((await run('sim.state.targetYaw')) - y0).toFixed(3); await wait(400);
  await run('sim.setView(0)'); await wait(800);
  report.tapScreen = await tapEl(`document.querySelector('.pleos')`); await wait(1500); report.inScreen = await run('sim.state.screenMode');
  report.touchHidden = await run(`getComputedStyle(document.querySelector('.touch')).display === 'none'`);
  report.backBtn = await tapEl(`document.getElementById('backSeat')`); await wait(1200); report.afterBack = await run('sim.state.screenMode');
  await tapEl(`document.querySelector('.pleos')`); await wait(1500);
  const sq = await quadOf(`document.querySelector('.pleos')`); await tap(1150, Math.round(sq[1])); await wait(1200); report.afterOutsideTap = await run('sim.state.screenMode');
  await tapEl(`document.querySelector('.pleos')`); await wait(1500);
} else await run('sim.enterScreen()');
await wait(1500); await shot('01_screen_main');
const go = TOUCH
  ? (sel, text) => tapEl(inFrame(`[...d.querySelectorAll('${sel}')].find((x) => x.textContent.includes('${text}'))`))
  : (sel, text) => frame(`(d) => { const e = [...d.querySelectorAll('${sel}')].find((x) => x.textContent.includes('${text}')); if (!e) return 'none'; e.click(); return 'ok'; }`);
report.browse = await go('.rail-item', '둘러보기'); await wait(2500); await shot('02_browse');
report.region = await go('.chip', '수도권'); await wait(1500);
report.card = await go('.map-card', '팔당 노을'); await wait(1500); await shot('03_card');
const press = (sel) => TOUCH ? tapEl(inFrame(`d.querySelector('${sel}')`)) : frame(`(d) => { const e = d.querySelector('${sel}'); if (!e) return 'none'; e.click(); return 'ok'; }`);
report.detail = await press('.cards-panel .btn-primary'); await wait(2500); await shot('04_detail');
report.go = await press('.btn-primary'); await wait(2500);
report.applied = await run('sim.app.applied'); await shot('05_applied');
report.guideStep = await run(`[...document.querySelectorAll('#guide li')].findIndex((li) => li.className === 'now') + 1`);
if (TOUCH) { await tapEl(`document.getElementById('backSeat')`); await wait(800); await tapEl(`document.querySelector('[data-key=d]')`); }
else { await run('sim.leaveScreen()'); await wait(800); await c('Input.dispatchKeyEvent', { type: 'keyDown', key: 'd', code: 'KeyD' }); await c('Input.dispatchKeyEvent', { type: 'keyUp', key: 'd', code: 'KeyD' }); }
await wait(7000);
report.gear = await run('sim.state.gear');
/* 가속: 키(또는 가속 단추를 누르고 있기)로 실제로 출발해 안내 카드가 사라지는지 본다 */
if (TOUCH) await tapEl(`document.querySelector('[data-hold=arrowup]')`, 1500);
else { await c('Input.dispatchKeyEvent', { type: 'keyDown', key: 'ArrowUp', code: 'ArrowUp' }); await wait(1500); await c('Input.dispatchKeyEvent', { type: 'keyUp', key: 'ArrowUp', code: 'ArrowUp' }); }
report.vAfterGo = +(await run('sim.state.v')).toFixed(2); report.guideHidden = await run(`document.getElementById('guide').hidden`);
/* 내비: 같은 구간 안에서 300m 앞으로 옮기기 전후의 지도 변환·다음 회전 거리 */
const navRead = () => run(`({ d: +sim.state.d.toFixed(1), tf: document.querySelector('#navg').getAttribute('transform'), tbt: document.querySelector('#tbtDist').textContent, remain: document.querySelector('#remain').textContent })`);
await run('sim.state.d = sim.world.zones[1].from + 40; sim.state.v = 0'); await wait(1200); report.navA = await navRead();
await run('sim.enterScreen()'); await wait(1500); await shot('05b_nav_a'); await run('sim.leaveScreen()');
await run('sim.state.d += 300; sim.state.v = 0'); await wait(1200); report.navB = await navRead();
await run('sim.enterScreen()'); await wait(1500); await shot('05c_nav_b'); await run('sim.leaveScreen()');
report.navMoved = report.navA.tf !== report.navB.tf;
await run('sim.state.d = sim.world.zones[1].from - 30; sim.state.v = 12'); await wait(5000); await shot('06_forest_enter');
report.section = await run('sim.app.section'); report.voice = await run('document.querySelector(".voice").textContent');
await run('sim.state.d = sim.world.zones[2].from - 20; sim.state.v = 12'); await wait(5000); await shot('07_city_enter');
report.section2 = await run('sim.app.section');
await run('sim.enterScreen()'); await wait(1500); await shot('08_screen_drive'); await run('sim.leaveScreen()');
await run('sim.setView(Math.PI / 2)'); await wait(1200); await shot('09_left_window');
report.errors = logs;
console.log(JSON.stringify(report));
await fetch(`http://127.0.0.1:${port}/json/close/${tgt.id}`); ws.close();
