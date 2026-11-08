// 에움길 주행 시뮬레이터 본체: 차·시점·조작·센터 화면.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { CSS3DRenderer, CSS3DObject } from 'three/addons/renderers/CSS3DRenderer.js';
import { buildWorld, loadTrees, zoneAt, LANE } from './world.js';
import { createEnv } from './env.js';

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
const [course, trees, cabinGltf, skyDay, skyDusk] = await Promise.all([
  fetch('../../app/src/main/assets/courses/paldang-sunset.json').then((r) => r.json()),
  loadTrees(DEMO + 'models/trees.glb'),
  new GLTFLoader().loadAsync('models/cabin.glb'),
  tex(DEMO + 'sky/city.jpg'), tex(DEMO + 'sky/river.jpg'),
]);
const world = await buildWorld(course, { trees, skies: [{ tex: skyDay, w: 2220, h: 500 }, { tex: skyDusk, w: 2560, h: 440 }] });
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
  if (o.name.startsWith('glass') || o.name.startsWith('window_')) o.material = new THREE.MeshBasicMaterial({ color: '#a8bccb', transparent: true, opacity: 0.07, depthWrite: false });
  if (o.name === 'screen' || o.name === 'slim') o.material = hole;
  if (o.name === 'ambient') o.material = new THREE.MeshBasicMaterial({ color: '#ffd9a0', toneMapped: false });
  /* 운전자 머리 자리라 운전석 등받이·머리받침은 그리지 않는다(뒤를 볼 때 얼굴 앞을 막는다) */
  if (o.name === 'back_driver' || o.name === 'head_driver') o.visible = false;
});
car.add(camera);
camera.position.copy(EYE);

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
screenEl.className = 'pleos';
screenEl.innerHTML = `<img class="base" id="basePark" src="${DEMO}pleos/park.png"><img class="base" id="baseDrive" src="${DEMO}pleos/drive.png">
  <img class="shift" id="shift"><div class="cover spd" id="spd">0</div><div class="cover clk" id="clk"></div>
  <div class="appfull" id="appFull"><img src="${DEMO}stills/steps/main.jpg"></div>`;
const slimEl = document.createElement('div');
slimEl.className = 'slim';
slimEl.innerHTML = '<div class="spd"><b id="sSpeed">0</b><span>km/h</span></div><div class="gear" id="sGear"><span>P</span><span>R</span><span>N</span><span>D</span></div><div class="meta"><b id="sDest">목적지 없음</b><br><span id="sMedia">미디어 없음</span></div>';
const screens = [mountDom(named.screen, screenEl, 2560), mountDom(named.slim, slimEl, 960)];
const $ = (id) => screenEl.querySelector('#' + id) || slimEl.querySelector('#' + id);
const shiftImgs = Array.from({ length: SHIFT_FRAMES }, (_, i) => { const im = new Image(); im.src = `${DEMO}pleos/shift/${String(i).padStart(3, '0')}.jpg`; return im; });

/* ---------- 상태 ---------- */
const state = { d: 4, v: 0, gear: 'P', shiftAt: -1, yaw: 0, pitch: -0.06, targetYaw: 0, screenMode: false, t: 0 };
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
function enterScreen() { state.screenMode = true; document.body.classList.add('screen-mode'); }
function leaveScreen() { state.screenMode = false; document.body.classList.remove('screen-mode'); }
document.querySelectorAll('[data-jump]').forEach((b) => b.addEventListener('click', () => { const z = world.zones.find((x) => x.mood === b.dataset.jump); state.d = z.from + (z.mood === 'park' ? 4 : 30); state.v = 0; }));

/* ---------- 한 프레임 ---------- */
const up = new THREE.Vector3(0, 1, 0);
function laneAt(d) { const park = world.zones[1].from; return d < park - 60 ? 0 : Math.min(1, (d - (park - 60)) / 60) * LANE; }
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
  /* 하늘과 빛 */
  const eye = camera.getWorldPosition(new THREE.Vector3()), here = env.apply(state.d, eye);
  /* 센터 화면과 슬림 화면 */
  car.updateMatrixWorld(true);
  screens.forEach((s) => { s.obj.matrix.copy(car.matrixWorld).multiply(s.local); s.obj.matrixWorldNeedsUpdate = true; });
  const kmh = Math.round(Math.abs(state.v) * 3.6);
  const shifting = state.shiftAt >= 0 && state.t - state.shiftAt < SHIFT_FRAMES / SHIFT_FPS;
  $('basePark').style.opacity = state.gear === 'P' ? 1 : 0; $('baseDrive').style.opacity = state.gear === 'P' ? 0 : 1;
  $('shift').style.display = shifting && state.gear === 'D' ? 'block' : 'none';
  if (shifting) $('shift').src = shiftImgs[Math.min(SHIFT_FRAMES - 1, Math.floor((state.t - state.shiftAt) * SHIFT_FPS))].src;
  $('spd').style.display = state.gear !== 'P' && !shifting ? 'flex' : 'none'; $('spd').textContent = kmh;
  $('appFull').style.display = state.gear === 'P' && state.shiftAt < 0 ? 'block' : 'none';
  const now = new Date(); $('clk').textContent = `${String(now.getHours() % 12 || 12).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')} ${now.getHours() < 12 ? '오전' : '오후'}`;
  $('sSpeed').textContent = kmh;
  slimEl.querySelectorAll('#sGear span').forEach((g) => (g.className = g.textContent === gearShown ? 'on' : ''));
  document.getElementById('hudZone').textContent = here.zone.mood === 'park' ? '주차장' : here.zone.name;
  document.getElementById('hudSpeed').textContent = `${kmh} km/h · ${gearShown}`;
}
const clock = new THREE.Clock();
function frame() {
  step(Math.min(clock.getDelta(), 0.05));
  renderer.render(world.scene, camera);
  css.render(cssScene, camera);
  requestAnimationFrame(frame);
}
document.getElementById('loading').remove();
frame();
/* 시험용: 상태를 밖에서 정할 수 있다 */
window.sim = { state, world, step, setView: (yaw) => { state.targetYaw = state.yaw = yaw; }, enterScreen, leaveScreen };
