// 시뮬레이터 점검: 페이지를 열고 시점·주행·화면 조작·구간 이동을 찍고 초당 프레임을 잰다.
// 사용: node tools/sim/check.mjs <port> <url> <출력 폴더>  (GPU 를 켠 헤드리스 Chrome 이 port 에 떠 있어야 한다)
import fs from 'fs';
const [port, url, out] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const tgt = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(tgt.webSocketDebuggerUrl); await new Promise((r) => (ws.onopen = r));
let id = 0; const p = {}; const logs = [];
ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && p[d.id]) { p[d.id](d.result || d); delete p[d.id]; } if (d.method === 'Runtime.exceptionThrown') logs.push(d.params.exceptionDetails.exception?.description || d.params.exceptionDetails.text); if (d.method === 'Runtime.consoleAPICalled' && d.params.type === 'error') logs.push(JSON.stringify(d.params.args.map((a) => a.value || a.description))); };
const c = (method, params = {}) => new Promise((r) => { const i = ++id; p[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const run = async (expression) => (await c('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.value;
const shot = async (name) => fs.writeFileSync(`${out}/${name}.jpg`, Buffer.from((await c('Page.captureScreenshot', { format: 'jpeg', quality: 85 })).data, 'base64'));
const key = async (k, code, ms) => { await c('Input.dispatchKeyEvent', { type: 'keyDown', key: k, code }); await wait(ms); await c('Input.dispatchKeyEvent', { type: 'keyUp', key: k, code }); };
await c('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
await c('Runtime.enable'); await c('Page.enable'); await c('Page.navigate', { url });
for (let i = 0; i < 120 && !(await run('!!window.sim')); i++) await wait(500);
await wait(1500);
const report = {};
report.fps = await run('new Promise((r) => { let n = 0; const t0 = performance.now(); const f = () => { n++; if (performance.now() - t0 < 2000) requestAnimationFrame(f); else r(Math.round(n / 2)); }; requestAnimationFrame(f); })');
await shot('01_park_front');
for (const [n, yaw] of [['02_left', Math.PI / 2], ['03_right', -Math.PI / 2], ['04_back', Math.PI]]) { await run(`sim.setView(${yaw})`); await wait(700); await shot(n); }
await run('sim.setView(0)'); await wait(500);
await key('d', 'KeyD', 100); await wait(7000); await shot('05_shift_done');
await key('ArrowUp', 'ArrowUp', 6000); await shot('06_driving'); report.afterDrive = await run('({ d: sim.state.d, v: sim.state.v })');
await key('ArrowDown', 'ArrowDown', 4000); report.afterBrake = await run('({ d: sim.state.d, v: sim.state.v })');
await run('sim.enterScreen()'); await wait(1200); await shot('07_screen_mode'); await run('sim.leaveScreen()');
for (const z of ['forest', 'city', 'river']) {
  await run(`document.querySelector('[data-jump=${z}]').click()`); await run('sim.state.gear = "D"; sim.state.v = 12'); await wait(2500); await shot('08_' + z);
}
await run('sim.setView(Math.PI / 2)'); await wait(700); await shot('09_river_left');
await run('sim.setView(0); sim.state.pitch = 0.55'); await wait(800); await shot('10_roof'); await run('sim.state.pitch = -0.06');
await run('sim.state.d = 4; sim.state.v = 0; sim.state.gear = "P"'); await wait(1500); await shot('11_park_again');
report.errors = logs;
fs.writeFileSync(`${out}/report.json`, JSON.stringify(report, null, 1));
console.log(JSON.stringify(report));
await fetch(`http://127.0.0.1:${port}/json/close/${tgt.id}`); ws.close();
