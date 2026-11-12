// 시뮬레이터 3단계 점검: 계절·날씨 9가지를 숲길에서 찍고, 정차 중 에움길 분위기 화면을 찍는다.
// 사용: node tools/sim/check-weather.mjs <port> <url> <출력 폴더>
import fs from 'fs';
const [port, url, out] = process.argv.slice(2);
fs.mkdirSync(out, { recursive: true });
const tgt = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(tgt.webSocketDebuggerUrl); await new Promise((r) => (ws.onopen = r));
let id = 0; const p = {}; const logs = [];
ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && p[d.id]) { p[d.id](d.result || d); delete p[d.id]; } if (d.method === 'Runtime.exceptionThrown') logs.push(d.params.exceptionDetails.exception?.description || d.params.exceptionDetails.text); };
const c = (method, params = {}) => new Promise((r) => { const i = ++id; p[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const run = async (expression) => (await c('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })).result?.value;
const shot = async (name) => fs.writeFileSync(`${out}/${name}.jpg`, Buffer.from((await c('Page.captureScreenshot', { format: 'jpeg', quality: 85 })).data, 'base64'));
await c('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
await c('Runtime.enable'); await c('Page.enable'); await c('Page.navigate', { url });
for (let i = 0; i < 120 && !(await run('!!window.sim')); i++) await wait(500);
await wait(2500);
const names = ['auto', 'spring', 'summer', 'autumn', 'winter', 'rain', 'snow', 'sunset', 'night'];
await run('sim.state.d = sim.world.zones[1].from + 200; sim.state.gear = "D"; sim.state.v = 11');
for (const [i, n] of names.entries()) { await run(`sim.setPreset('${n}')`); await wait(2200); await shot(`${String(i).padStart(2, '0')}_${n}`); }
await run('sim.state.d = 4; sim.state.v = 0; sim.state.gear = "P"; sim.setPreset("rain"); sim.enterScreen()'); await wait(3500); await shot('20_app_rain');
await run('sim.setPreset("snow")'); await wait(3500); await shot('21_app_snow'); await run('sim.leaveScreen()');
console.log(JSON.stringify({ errors: logs, fps: await run('new Promise((r) => { let n = 0; const t0 = performance.now(); const f = () => { n++; if (performance.now() - t0 < 2000) requestAnimationFrame(f); else r(Math.round(n / 2)); }; requestAnimationFrame(f); })') }));
await fetch(`http://127.0.0.1:${port}/json/close/${tgt.id}`); ws.close();
