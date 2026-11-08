// 사용: node tools/demo/peek.mjs <port> <url> <out폴더> <t...> : 재현 화면을 몇 순간만 찍어 본다
const [port, url, out, ...times] = process.argv.slice(2);
const tgt = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
const ws = new WebSocket(tgt.webSocketDebuggerUrl); await new Promise((r) => (ws.onopen = r));
let id = 0; const p = {}; ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && p[d.id]) { p[d.id](d.result); delete p[d.id]; } };
const c = (method, params = {}) => new Promise((r) => { const i = ++id; p[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
const fs = await import('fs'); fs.mkdirSync(out, { recursive: true });
await c('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false });
await c('Page.enable'); await c('Page.navigate', { url }); await new Promise((r) => setTimeout(r, 3000));
const r0 = await c('Runtime.evaluate', { expression: 'window.ready', awaitPromise: true, returnByValue: true });
if (r0.exceptionDetails) console.log('ERR', JSON.stringify(r0.exceptionDetails).slice(0, 400));
await new Promise((r) => setTimeout(r, 800));
for (const t of times) {
  const r = await c('Runtime.evaluate', { expression: `render(${t})`, returnByValue: true });
  if (r.exceptionDetails) console.log('ERR', t, JSON.stringify(r.exceptionDetails).slice(0, 400));
  const shot = await c('Page.captureScreenshot', { format: 'jpeg', quality: 85 });
  fs.writeFileSync(`${out}/t${String(t).padStart(5, '0')}.jpg`, Buffer.from(shot.data, 'base64'));
}
await fetch(`http://127.0.0.1:${port}/json/close/${tgt.id}`); ws.close();
