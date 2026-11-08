// 에움길 주행 시뮬레이터 본체: 차·시점·조작·센터 화면.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { CSS3DRenderer, CSS3DObject } from 'three/addons/renderers/CSS3DRenderer.js';
import { buildWorld, loadTrees, zoneAt, LANE, recolor, paintGround } from './world.js';
import { createEnv, PRESETS } from './env.js';
import { createApp, SECTION_INFO } from './app.js';
import { loadMaterials, cabinMaterials, boxUV } from './tex.js';

const DEMO = '../demo/';
const MAX_FWD = 60 / 3.6, MAX_REV = 12 / 3.6;          // 최고 속도(m/s)
const ACCEL = 2.6, BRAKE = 6, COAST = 0.7;             // 가속·제동·관성 감속(m/s²)
const EYE = new THREE.Vector3(-0.37, 1.2, 0.02);       // 운전자 눈(차 기준, three.js 좌표)
const SHIFT_FRAMES = 97, SHIFT_FPS = 15;

/* ---------- 렌더러 ---------- */
const canvas = document.getElementById('gl');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.25;
renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
const css = new CSS3DRenderer({ element: document.getElementById('css') });
const camera = new THREE.PerspectiveCamera(62, 1, 0.05, 9000);
const cssScene = new THREE.Scene();
function resize() { renderer.setSize(innerWidth, innerHeight, false); css.setSize(innerWidth, innerHeight); camera.aspect = innerWidth / innerHeight; camera.updateProjectionMatrix(); }
addEventListener('resize', resize); resize();

/* ---------- 자료 ---------- */
const tex = (url) => new THREE.TextureLoader().loadAsync(url).then((t) => { t.colorSpace = THREE.SRGBColorSpace; return t; });
const [course, navmap, trees, cabinGltf, skyDay, skyDusk, skyCloud, skySnow, skyClear, skyAutumn, mats, cmats] = await Promise.all([
  fetch('../../app/src/main/assets/courses/paldang-sunset.json').then((r) => r.json()),
  fetch(DEMO + 'osm/navmap.json').then((r) => r.json()),
  loadTrees(DEMO + 'models/trees.glb'),
  new GLTFLoader().loadAsync('models/cabin.glb'),
  tex(DEMO + 'sky/city.jpg'), tex(DEMO + 'sky/river.jpg'), tex(DEMO + 'sky/forest.jpg'), tex(DEMO + 'sky/snow.jpg'),
  tex(DEMO + 'sky/clear.jpg'), tex(DEMO + 'sky/autumn.jpg'),
  loadMaterials(), cabinMaterials(),
]);
const world = await buildWorld(course, { trees, mats, skies: [{ tex: skyDay, w: 2220, h: 500 }, { tex: skyDusk, w: 2560, h: 440 }, { tex: skyCloud, w: 2560, h: 452 }, { tex: skySnow, w: 2560, h: 410 }, { tex: skyClear, w: 6144, h: 1024 }, { tex: skyAutumn, w: 6144, h: 1024 }] });
const env = createEnv(world);

/* ---------- 차 실내 ---------- */
const car = new THREE.Group();
const cabin = cabinGltf.scene;
car.add(cabin);
world.scene.add(car);
const hole = new THREE.MeshBasicMaterial({ color: 0x000000, opacity: 0, transparent: false, blending: THREE.NoBlending });
const named = {};
cabin.traverse((o) => {
  if (!o.isMesh) return;
  named[o.name] = o;
  o.castShadow = false; o.receiveShadow = true;
  const mname = o.material && o.material.name;
  if (o.name.startsWith('glass') || o.name.startsWith('window_')) o.material = new THREE.MeshBasicMaterial({ color: '#a8bccb', transparent: true, opacity: 0.07, depthWrite: false });
  else if (o.name === 'screen' || o.name === 'slim') o.material = hole;
  else if (o.name === 'ambient') o.material = new THREE.MeshBasicMaterial({ color: '#ffd9a0', toneMapped: false });
  else {
    /* 실사 재질: 블렌더 재질 이름(leather·trim_dark·trim_mid·headliner)에 맞춰 바꾸고, 무늬 좌표는 상자 투영으로 만든다.
       재질표에 없는 이름(body·chrome: 사이드미러·보닛·변속 레버)은 밝은 회색 상자로 떠 보여 어두운 트림으로 맞춘다 */
    o.geometry = boxUV(o.geometry, 1.5); o.material = cmats[mname] || cmats.trim_dark;
  }
  /* 운전자 머리 자리라 운전석 등받이·머리받침은 그리지 않는다(뒤를 볼 때 얼굴 앞을 막는다) */
  if (o.name === 'back_driver' || o.name === 'head_driver') o.visible = false;
});
car.add(camera);
camera.position.copy(EYE);
/* 룸미러: 뒤를 보는 작은 카메라를 512×160 그림에 3프레임에 한 번 그려(숲에서 2프레임마다면 50fps 아래로 내려간다) 거울 면(운전자 쪽)에 입힌다. 좌우는 거울처럼 뒤집는다 */
const MIRROR = true;
const mirrorRT = new THREE.WebGLRenderTarget(512, 160, { type: THREE.HalfFloatType });
mirrorRT.texture.repeat.x = -1; mirrorRT.texture.offset.x = 1;
const mirrorCam = new THREE.PerspectiveCamera(16, 512 / 160, 0.1, 3000);
mirrorCam.position.set(0, 1.38, -0.37); mirrorCam.rotation.set(-0.04, Math.PI, 0); car.add(mirrorCam);
const mirrorFace = new THREE.Mesh(new THREE.PlaneGeometry(0.235, 0.062), new THREE.MeshBasicMaterial({ map: mirrorRT.texture, color: '#d8dde2' }));
mirrorFace.position.set(0, 1.38, -0.383); mirrorFace.visible = MIRROR; car.add(mirrorFace);
let mirrorTick = 0;
function renderMirror() {
  /* 거울 속에는 운전석 등받이가 보여야 한다(운전자 시점에서만 숨긴다). 그림자는 본 화면에서 그린 것을 다시 쓴다 */
  const seat = [named.back_driver, named.head_driver].filter(Boolean);
  seat.forEach((o) => (o.visible = true)); renderer.shadowMap.autoUpdate = false;
  renderer.setRenderTarget(mirrorRT); renderer.render(world.scene, mirrorCam); renderer.setRenderTarget(null);
  seat.forEach((o) => (o.visible = false)); renderer.shadowMap.autoUpdate = true;
}

/* 화면 판 위치에 DOM 을 얹는다. 판의 네 모서리로 중심·가로·세로 축을 구해 차 기준 변환을 저장해 둔다 */
function mountDom(mesh, el, pxW) {
  mesh.geometry.computeBoundingBox();
  car.updateMatrixWorld(true);
  const pos = mesh.geometry.attributes.position, pts = [];
  for (let i = 0; i < pos.count; i++) pts.push(new THREE.Vector3().fromBufferAttribute(pos, i).applyMatrix4(mesh.matrixWorld));
  const center = pts.reduce((s, p) => s.add(p), new THREE.Vector3()).multiplyScalar(1 / pts.length);
  /* 한 꼭짓점에서 나가는 세 변 가운데 가장 긴 것은 대각선이다. 나머지 두 변 중 긴 쪽이 가로, 짧은 쪽이 세로 */
  const sides = pts.slice(1).map((q) => q.clone().sub(pts[0])).sort((a, b) => b.length() - a.length()).slice(1);
  let right = sides[0].clone(); const widthM = right.length(); right.normalize();
  let upv = sides[1].clone().normalize();
  if (upv.y < 0) upv.negate();
  let normal = new THREE.Vector3().crossVectors(right, upv);
  const eyeW = camera.getWorldPosition(new THREE.Vector3());
  if (normal.dot(eyeW.clone().sub(center)) < 0) { right.negate(); normal.negate(); }
  const basis = new THREE.Matrix4().makeBasis(right, upv, normal).setPosition(center);
  const local = car.matrixWorld.clone().invert().multiply(basis).multiply(new THREE.Matrix4().makeScale(widthM / pxW, widthM / pxW, 1));
  const obj = new CSS3DObject(el); obj.matrixAutoUpdate = false; cssScene.add(obj);
  return { obj, local, center: car.worldToLocal(center.clone()), normal };
}
const screenEl = document.createElement('div');
screenEl.className = 'pleos stage'; screenEl.dataset.theme = 'day';
screenEl.innerHTML = `<img class="base" id="basePark" src="${DEMO}pleos/park.png"><img class="base" id="baseDrive" src="${DEMO}pleos/drive.png">
  <img class="shift" id="shift"><div class="cover spd" id="spd">0</div><div class="cover clk" id="clk"></div>`;
const slimEl = document.createElement('div');
slimEl.className = 'slim';
slimEl.innerHTML = '<div class="spd"><b id="sSpeed">0</b><span>km/h</span></div><div class="gear" id="sGear"><span>P</span><span>R</span><span>N</span><span>D</span></div><div class="meta"><b id="sDest">목적지 없음</b><br><span id="sMedia">미디어 없음</span></div>';
const screens = [mountDom(named.screen, screenEl, 2560), mountDom(named.slim, slimEl, 960)];
const $ = (id) => screenEl.querySelector('#' + id) || slimEl.querySelector('#' + id);
/* 음성: 브라우저 한국어 음성 합성. 화면 아래에 자막도 띄운다 */
const voiceEl = document.createElement('div'); voiceEl.className = 'voice'; document.body.append(voiceEl);
const cabinNote = document.createElement('div'); cabinNote.className = 'cabin-note'; cabinNote.style.display = 'none'; document.body.append(cabinNote);
let voiceTimer = 0;
function say(text) {
  voiceEl.innerHTML = `<i>에움길 음성</i>${text}`; voiceEl.style.display = 'flex'; clearTimeout(voiceTimer); voiceTimer = setTimeout(() => (voiceEl.style.display = 'none'), 3500);
  try { const u = new SpeechSynthesisUtterance(text); u.lang = 'ko-KR'; speechSynthesis.speak(u); } catch (e) { /* 음성 합성이 없으면 자막만 */ }
}
/* 차 안 연출: 창문 높이와 앰비언트 조명 색이 구간마다 바뀐다 */
const cabinFx = { window: 0, light: new THREE.Color('#ffd9a0') };
const windowBase = {};
['window_fl', 'window_fr'].forEach((n) => { if (named[n]) windowBase[n] = named[n].position.y; });
const app = createApp(screenEl, { course, navmap, onSay: say, onApply: () => { cabinNote.style.display = 'block'; cabinNote.innerHTML = '<b>내비에 코스를 입혔습니다</b><br>구간에 들어서면 차 안 연출이 바뀝니다'; } });
app.onSection = (si) => {
  const info = SECTION_INFO[si];
  cabinFx.window = info.window; cabinFx.light.set(info.light);
  cabinNote.style.display = 'block';
  cabinNote.innerHTML = `<b>차 안 연출</b> ${info.cabin}<br><b>음악</b> 연결된 음악 앱에 '${info.music}' 재생 요청`;
};
const shiftImgs = Array.from({ length: SHIFT_FRAMES }, (_, i) => { const im = new Image(); im.src = `${DEMO}pleos/shift/${String(i).padStart(3, '0')}.jpg`; return im; });

/* ---------- 날씨 효과 ---------- */
const BOX = [60, 30, 60];
function particles(n, rain) {
  const pos = new Float32Array(n * (rain ? 6 : 3));
  for (let i = 0; i < n; i++) {
    const x = (Math.random() - 0.5) * BOX[0], y = Math.random() * BOX[1], z = (Math.random() - 0.5) * BOX[2];
    if (rain) pos.set([x, y, z, x + 0.05, y - 0.55, z], i * 6); else pos.set([x, y, z], i * 3);
  }
  const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  const o = rain ? new THREE.LineSegments(g, new THREE.LineBasicMaterial({ color: '#c8d2dc', transparent: true, opacity: 0.55 }))
    : new THREE.Points(g, new THREE.PointsMaterial({ color: '#ffffff', size: 0.09, transparent: true, opacity: 0.95 }));
  o.frustumCulled = false; o.visible = false; world.scene.add(o); return o;
}
const rainFx = particles(7000, true), snowFx = particles(6000, false);
function fall(o, dt, speed, rain) {
  const a = o.geometry.attributes.position, per = rain ? 2 : 1;
  for (let i = 0; i < a.count; i += per) {
    let y = a.getY(i) - speed * dt; let x = a.getX(i) + (rain ? 0 : Math.sin(state.t * 0.8 + i) * 0.3 * dt);
    if (y < 0) y += BOX[1];
    a.setXY(i, x, y); if (rain) a.setXY(i + 1, x + 0.05, y - 0.55);
  }
  a.needsUpdate = true;
}
/* 전조등 두 개: 비·눈·밤에 켠다 */
const heads = [-0.7, 0.7].map((x) => { const s = new THREE.SpotLight('#f4f6ff', 0, 80, 0.38, 0.6, 1.4); s.position.set(x, 0.75, -2.3); s.target.position.set(x * 2, 0, -40); car.add(s, s.target); return s; });
/* 와이퍼: 앞 유리 아래 두 개. 유리면 안에서 부채꼴로 쓸어 올린다 */
const wipers = [-0.45, 0.2].map((x) => {
  const pivot = new THREE.Group(); pivot.position.set(x, 0.99, -0.86); pivot.rotation.x = -0.56;
  const blade = new THREE.Mesh(new THREE.BoxGeometry(0.58, 0.014, 0.025), new THREE.MeshLambertMaterial({ color: '#111214' })); blade.position.x = 0.29; blade.geometry.translate(0, 0, 0);
  pivot.add(blade); car.add(pivot); pivot.visible = false; return pivot;
});
function setPreset(name) {
  const pr = PRESETS[name]; env.setPreset(name);
  recolor(world.treeGroups, pr.season); paintGround(world.ground, pr.ground);
  rainFx.visible = !!pr.rain; snowFx.visible = !!pr.snow; wipers.forEach((w) => (w.visible = !!pr.wipers));
  heads.forEach((h) => (h.intensity = pr.lights ? (pr.night ? 140 : 60) : 0));
  renderer.toneMappingExposure = pr.night ? 1.6 : 1.25;
  app.setMood(pr.mood); screenEl.dataset.mood = pr.mood || 'plain';
  document.querySelectorAll('[data-preset]').forEach((b) => b.classList.toggle('on', b.dataset.preset === name));
}
document.querySelectorAll('[data-preset]').forEach((b) => b.addEventListener('click', () => setPreset(b.dataset.preset)));

/* ---------- 상태 ---------- */
const state = { d: 4, v: 0, gear: 'P', shiftAt: -1, ambient: new THREE.Color('#ffd9a0'), yaw: 0, pitch: -0.06, targetYaw: 0, screenMode: false, t: 0 };
const keys = new Set();
addEventListener('keydown', (e) => {
  keys.add(e.key.toLowerCase());
  if (e.key === 'Escape') leaveScreen();
  if (state.screenMode) return;
  const k = e.key.toLowerCase();
  if (k === 'd' && state.gear !== 'D' && Math.abs(state.v) < 0.3) { state.gear = 'D'; state.shiftAt = state.t; }
  if (k === 'p' && Math.abs(state.v) < 0.3) state.gear = 'P';
  if (k === '1') state.targetYaw = 0;
  if (k === '2') state.targetYaw = Math.PI / 2;
  if (k === '3') state.targetYaw = -Math.PI / 2;
  if (k === '4') state.targetYaw = Math.PI;
});
addEventListener('keyup', (e) => keys.delete(e.key.toLowerCase()));
/* 화면 단추: 누르는 동안 키를 누른 것과 같다(가속·감속), 한 번 누르면 키 한 번(기어·시점) */
document.querySelectorAll('[data-hold]').forEach((b) => {
  const k = b.dataset.hold;
  b.addEventListener('pointerdown', (e) => { e.preventDefault(); keys.add(k); });
  ['pointerup', 'pointerleave', 'pointercancel'].forEach((ev) => b.addEventListener(ev, () => keys.delete(k)));
});
document.querySelectorAll('[data-key]').forEach((b) => b.addEventListener('click', () => dispatchEvent(new KeyboardEvent('keydown', { key: b.dataset.key }))));
/* 마우스: 끌면 둘러보고, 끌지 않고 누르면 센터 화면을 누른 것인지 본다 */
let drag = null;
canvas.addEventListener('pointerdown', (e) => { drag = { x: e.clientX, y: e.clientY, moved: false }; canvas.setPointerCapture(e.pointerId); });
canvas.addEventListener('pointermove', (e) => {
  if (!drag) return;
  const dx = e.clientX - drag.x, dy = e.clientY - drag.y;
  if (Math.abs(dx) + Math.abs(dy) > 3) drag.moved = true;
  state.targetYaw += dx * 0.004; state.yaw = state.targetYaw; state.pitch = Math.max(-0.7, Math.min(0.5, state.pitch - dy * 0.003));
  drag.x = e.clientX; drag.y = e.clientY;
});
canvas.addEventListener('pointerup', (e) => {
  if (drag && !drag.moved) {
    const ray = new THREE.Raycaster(); ray.setFromCamera(new THREE.Vector2((e.clientX / innerWidth) * 2 - 1, -(e.clientY / innerHeight) * 2 + 1), camera);
    if (ray.intersectObject(named.screen).length) enterScreen();
  }
  drag = null;
});
/* 터치: 손을 뗄 때 따라오는 클릭을 막는다. 화면 모드로 바뀐 직후 같은 자리의 화면 속 앱이 눌리거나 바로 빠져나가지 않게 */
canvas.addEventListener('touchend', (e) => e.preventDefault(), { passive: false });
/* 화면 모드에서 나가기: 단추, 또는 화면 밖을 탭(끌지 않고). 화면 모드에서는 캔버스가 눌리지 않아 아래 #css 층이 받는다 */
document.getElementById('backSeat').addEventListener('click', () => leaveScreen());
const cssEl = document.getElementById('css'); let outside = null;
cssEl.addEventListener('pointerdown', (e) => { outside = state.screenMode && !screenEl.contains(e.target) ? { x: e.clientX, y: e.clientY } : null; });
cssEl.addEventListener('pointerup', (e) => { if (outside && Math.hypot(e.clientX - outside.x, e.clientY - outside.y) < 12) leaveScreen(); outside = null; });
function enterScreen() { state.screenMode = true; document.body.classList.add('screen-mode'); }
function leaveScreen() { state.screenMode = false; document.body.classList.remove('screen-mode'); }
document.querySelectorAll('[data-jump]').forEach((b) => b.addEventListener('click', () => { const z = world.zones.find((x) => x.mood === b.dataset.jump); state.d = z.from + (z.mood === 'park' ? 4 : 30); state.v = 0; }));

/* ---------- 한 프레임 ---------- */
const guideEl = document.getElementById('guide'), guideSteps = [...guideEl.querySelectorAll('li')];
let guideDone = false;
const up = new THREE.Vector3(0, 1, 0);
/* 오른쪽 차로 가운데(중앙선에서 LANE). 운전석 눈은 차 중심에서 왼쪽 0.37m 라 중앙선 오른쪽 약 1.38m.
   출발 주차 칸(d<6)에서만 칸 가운데에 서 있다가 빠져나오며(d 6~20) 차로로 옮긴다. 후진·정차도 같은 규칙 */
function laneAt(d) { return Math.min(1, Math.max(0, (d - 6) / 14)) * LANE; }
function step(dt) {
  state.t += dt;
  /* 운전: 앞뒤로만. 경로 밖으로 나가지 않는다 */
  const fwd = keys.has('arrowup') || keys.has('w'), back = keys.has('arrowdown') || keys.has('s') || keys.has(' ');
  if (state.screenMode || state.gear === 'P') state.v *= 0.9;
  else if (fwd) state.v = state.v < 0 ? Math.min(0, state.v + BRAKE * dt) : Math.min(MAX_FWD, state.v + ACCEL * dt);
  else if (back) state.v = state.v > 0 ? Math.max(0, state.v - BRAKE * dt) : Math.max(-MAX_REV, state.v - ACCEL * 0.6 * dt);
  else state.v = Math.sign(state.v) * Math.max(0, Math.abs(state.v) - COAST * dt);
  state.d = Math.min(world.length - 20, Math.max(2, state.d + state.v * dt));
  if (state.d <= 2 || state.d >= world.length - 20) state.v = 0;
  const gearShown = state.gear === 'P' ? 'P' : state.v < -0.2 ? 'R' : 'D';
  /* 차 자세: 길 방향을 보고, 오른쪽 차로 가운데에 선다 */
  const u = state.d / world.length, p = world.curve.getPointAt(u), t = world.curve.getTangentAt(u), r = new THREE.Vector3().crossVectors(t, up).normalize();
  car.position.copy(p).addScaledVector(r, laneAt(state.d));
  car.lookAt(car.position.clone().sub(t));
  /* 시점: 운전석에서 둘러본다. 화면 조작 때는 센터 화면 앞으로 다가간다 */
  state.yaw += (state.targetYaw - state.yaw) * Math.min(1, dt * 8);
  const scr = screens[0];
  if (state.screenMode) {
    const target = scr.center.clone().add(new THREE.Vector3(-0.05, 0.08, 0.42));
    camera.position.lerp(target, Math.min(1, dt * 6));
    const m = new THREE.Matrix4().lookAt(camera.position, scr.center, up); camera.quaternion.slerp(new THREE.Quaternion().setFromRotationMatrix(m), Math.min(1, dt * 6));
  } else {
    camera.position.lerp(EYE, Math.min(1, dt * 6));
    camera.quaternion.slerp(new THREE.Quaternion().setFromEuler(new THREE.Euler(state.pitch, state.yaw, 0, 'YXZ')), Math.min(1, dt * 10));
  }
  /* 하늘과 빛, 날씨 */
  const eye = camera.getWorldPosition(new THREE.Vector3()), here = env.apply(state.d, eye);
  [rainFx, snowFx].forEach((o) => o.position.set(eye.x, eye.y - 8, eye.z));
  if (rainFx.visible) fall(rainFx, dt, 16, true);
  if (snowFx.visible) fall(snowFx, dt, 1.4, false);
  wipers.forEach((w, i) => { if (w.visible) w.rotation.z = 0.15 + 1.25 * (0.5 - 0.5 * Math.cos(state.t * 3.2)); });
  /* 센터 화면과 슬림 화면 */
  car.updateMatrixWorld(true);
  screens.forEach((s) => { s.obj.matrix.copy(car.matrixWorld).multiply(s.local); s.obj.matrixWorldNeedsUpdate = true; });
  const kmh = Math.round(Math.abs(state.v) * 3.6);
  const shifting = state.shiftAt >= 0 && state.t - state.shiftAt < SHIFT_FRAMES / SHIFT_FPS;
  $('basePark').style.opacity = state.gear === 'P' ? 1 : 0; $('baseDrive').style.opacity = state.gear === 'P' ? 0 : 1;
  $('shift').style.display = shifting && state.gear === 'D' ? 'block' : 'none';
  if (shifting) $('shift').src = shiftImgs[Math.min(SHIFT_FRAMES - 1, Math.floor((state.t - state.shiftAt) * SHIFT_FPS))].src;
  $('spd').style.display = state.gear !== 'P' && !shifting ? 'flex' : 'none'; $('spd').textContent = kmh;
  /* 에움길: 구간 진행률과 실제 코스 경로 위 위치 */
  const zi = world.zones.indexOf(here.zone), zf = Math.min(1, Math.max(0, (state.d - here.zone.from) / (here.zone.to - here.zone.from)));
  /* 주차장(구간 0)에서도 첫 구간 기준으로 재어, 출구로 나가는 동안 내비가 코스 시작점으로 다가간다(전에는 처음 120m 동안 멈춰 있었다) */
  const src = world.sources[Math.max(0, zi - 1)], navS = app.cumAt(src.start) + (state.d - src.at);
  app.update({ gear: state.gear, routeS: navS, zoneIndex: zi, zoneFrac: zf, kmh });
  if (state.d >= world.length - 25 && Math.abs(state.v) < 0.3 && app.applied) app.finish();
  $('sDest').textContent = app.applied ? (app.finished ? '도착 · 양평 두물머리' : '두물머리까지 ' + $('remain').textContent + 'km') : '목적지 없음';
  $('sMedia').textContent = app.section >= 0 ? '♪ ' + SECTION_INFO[app.section].music : '미디어 없음';
  /* 차 안 연출 */
  Object.entries(windowBase).forEach(([n, y]) => { const o = named[n]; o.position.y += (y - cabinFx.window * 0.38 - o.position.y) * Math.min(1, dt * 1.5); });
  state.ambient.lerp(cabinFx.light, Math.min(1, dt * 1.2)); named.ambient.material.color.copy(state.ambient);
  const now = new Date(); $('clk').textContent = `${String(now.getHours() % 12 || 12).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')} ${now.getHours() < 12 ? '오전' : '오후'}`;
  $('sSpeed').textContent = kmh;
  slimEl.querySelectorAll('#sGear span').forEach((g) => (g.className = g.textContent === gearShown ? 'on' : ''));
  /* 처음 안내: ① 코스 고르기 ② D ③ 가속. 지금 할 단계를 강조하고 셋이 함께 끝나면 숨긴다 */
  if (!guideDone) {
    const done = [app.applied, state.gear === 'D', state.gear === 'D' && state.v > 0.5];
    if (done.every(Boolean)) { guideDone = true; guideEl.hidden = true; }
    else guideSteps.forEach((li, i) => { const c = done[i] ? 'done' : i === done.indexOf(false) ? 'now' : ''; if (li.className !== c) li.className = c; });
  }
  document.getElementById('hudZone').textContent = here.zone.mood === 'park' ? '주차장' : here.zone.name;
  document.getElementById('hudSpeed').textContent = `${kmh} km/h · ${gearShown}`;
}
const clock = new THREE.Clock();
function frame() {
  step(Math.min(clock.getDelta(), 0.05));
  if (MIRROR && !state.screenMode && mirrorTick++ % 3 === 0) renderMirror();
  renderer.render(world.scene, camera);
  css.render(cssScene, camera);
  requestAnimationFrame(frame);
}
document.getElementById('loading').remove();
frame();
/* 시험용: 상태를 밖에서 정할 수 있다 */
window.sim = { state, world, step, app, say, setPreset, setView: (yaw) => { state.targetYaw = state.yaw = yaw; }, enterScreen, leaveScreen };
