// 주행 재현 영상의 정지 화면과 프레임을 찍는다. tools/demo/record-film.sh 가 부른다.
// 사용: node record-film.mjs <서버 주소> <출력 폴더> <fps>
import { spawn } from 'child_process';
import fs from 'fs';
import os from 'os';
import path from 'path';

const [base, out, fpsArg] = process.argv.slice(2);
const fps = Number(fpsArg || 30);
const chrome = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const port = 9341;
const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'film-chrome-'));
const proc = spawn(chrome, ['--headless=new', '--disable-gpu', '--hide-scrollbars', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`, 'about:blank'], { stdio: 'ignore' });
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
for (let i = 0; i < 50; i++) { try { await fetch(`http://127.0.0.1:${port}/json/version`); break; } catch { await wait(200); } }

async function open(width, height) {
  const tgt = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
  const ws = new WebSocket(tgt.webSocketDebuggerUrl); await new Promise((r) => (ws.onopen = r));
  let id = 0; const pending = {};
  ws.onmessage = (m) => { const d = JSON.parse(m.data); if (d.id && pending[d.id]) { pending[d.id](d.result || d); delete pending[d.id]; } };
  const call = (method, params = {}) => new Promise((r) => { const i = ++id; pending[i] = r; ws.send(JSON.stringify({ id: i, method, params })); });
  await call('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
  await call('Page.enable');
  const go = async (url) => { await call('Page.navigate', { url }); await wait(4000); };
  const run = async (expression) => {
    const r = await call('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (r.exceptionDetails) throw new Error(`${expression}: ${JSON.stringify(r.exceptionDetails).slice(0, 300)}`);
    return r.result && r.result.value;
  };
  const shot = async (file, format = 'png') => fs.writeFileSync(file, Buffer.from((await call('Page.captureScreenshot', { format, quality: format === 'jpeg' ? 92 : undefined })).data, 'base64'));
  return { go, run, shot, close: async () => { ws.close(); await fetch(`http://127.0.0.1:${port}/json/close/${tgt.id}`); } };
}

try {
  // 1. 센터 화면에 넣을 시안 정지 화면(2/3 창, 주간)
  const stills = path.join(path.dirname(new URL(import.meta.url).pathname), 'stills');
  fs.mkdirSync(stills, { recursive: true });
  const page = await open(1700, 1184);
  for (const name of ['MainDay', 'SummaryDay']) {
    await page.go(`${base}/design/screens/${name}.dc.html`);
    await page.shot(path.join(stills, `${name}.png`));
  }
  await page.close();

  // 2. 재현 화면을 시각마다 그려 찍는다
  const film = await open(1920, 1080);
  await film.go(`${base}/tools/demo/drive-film.html`);
  await film.run('window.ready');
  await wait(800);
  const end = await film.run('window.END');
  fs.writeFileSync(path.join(out, 'voice.json'), JSON.stringify(await film.run('window.VOICE')));
  fs.writeFileSync(path.join(out, 'end.txt'), String(end));
  const frames = path.join(out, 'frames');
  fs.mkdirSync(frames, { recursive: true });
  const total = Math.round(end * fps);
  for (let i = 0; i < total; i++) {
    await film.run(`render(${(i / fps).toFixed(4)})`);
    await film.shot(path.join(frames, `f${String(i).padStart(5, '0')}.jpg`), 'jpeg');
    if (i % fps === 0) process.stdout.write(`\r${i / fps}/${end}s`);
  }
  process.stdout.write('\n');
  await film.close();
} finally {
  proc.kill();
}
