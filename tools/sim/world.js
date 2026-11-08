// 시뮬레이터 바깥 세상. 주차장 → 숲길 → 도심(팔당대교 쪽) → 강변을 한 길로 이어 붙인다.
// 숲·도심·강변 구간은 코스 파일의 실제 경로·고도에서 굽이가 많은 약 1.2km씩을 떼어 진행 방향을 맞춰 잇는다.
// 가까운 곳(도로 양옆 약 200m)은 3D, 먼 산과 하늘은 풍경 사진 띠를 카메라 둘레 원통에 입힌다.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { cylinderUV } from './tex.js';

const SNIPPET = 1200;
const STEP = 2;
export const LANE = 1.75;

function rng(seed) { let s = seed >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }
const smooth = (x) => (x <= 0 ? 0 : x >= 1 ? 1 : x * x * (3 - 2 * x));

/* ---------- 경로 ---------- */
function toLocal(path, elev) {
  const [lat0, lon0] = path[0], k = Math.cos(lat0 * Math.PI / 180);
  return path.map(([lat, lon], i) => {
    const f = (i / (path.length - 1)) * (elev.length - 1), a = Math.floor(f), b = Math.min(a + 1, elev.length - 1);
    return new THREE.Vector3((lon - lon0) * 111320 * k, elev[a] + (elev[b] - elev[a]) * (f - a), -(lat - lat0) * 110540);
  });
}
/* 구간 안에서 SNIPPET 길이 창 가운데 꺾임 합이 큰 곳. 한 점에서 35도 넘게 꺾이는 머리핀은 뺀다 */
function pickWindow(pts, start, end) {
  let best = [start, Math.min(end, start + 30)], score = -1;
  for (let i = start; i < end; i++) {
    let len = 0, turn = 0, sharp = 0, j = i;
    while (j + 1 <= end && len < SNIPPET) {
      const a = pts[j], b = pts[j + 1]; len += Math.hypot(b.x - a.x, b.z - a.z);
      if (j > i) {
        const p = pts[j - 1]; let d = Math.abs(Math.atan2(b.z - a.z, b.x - a.x) - Math.atan2(a.z - p.z, a.x - p.x));
        if (d > Math.PI) d = 2 * Math.PI - d; turn += d; sharp = Math.max(sharp, d);
      }
      j++;
    }
    if (len >= SNIPPET * 0.9 && sharp < 0.6 && turn > score) { score = turn; best = [i, j]; }
  }
  return best;
}
/* 앞 점들 끝에 이어 붙인다: 첫 점을 이전 끝점으로 옮기고 첫 방향을 이전 끝 방향에 맞춘다.
   그대로 붙인 것과 좌우를 뒤집은 것을 둘 다 만들어 앞 길과 덜 겹치는 쪽을 쓴다(길이 감겨 다른 구간 위를 지나지 않게).
   높낮이는 60%로 줄인다 */
function placed(route, piece, mirror) {
  const last = route[route.length - 1], prev = route[route.length - 2];
  const want = Math.atan2(last.z - prev.z, last.x - prev.x);
  const have = Math.atan2(piece[1].z - piece[0].z, piece[1].x - piece[0].x);
  const rot = want - have, c = Math.cos(rot), s = Math.sin(rot), o = piece[0];
  const step = Math.hypot(last.x - prev.x, last.z - prev.z) || 20;
  const h0 = Math.atan2(piece[1].z - piece[0].z, piece[1].x - piece[0].x), out = [];
  piece.forEach((p, i) => {
    if (i === 0) return;
    let x = p.x - o.x, z = p.z - o.z;
    if (mirror) { const along = x * Math.cos(h0) + z * Math.sin(h0), side = -x * Math.sin(h0) + z * Math.cos(h0); x = along * Math.cos(h0) + side * Math.sin(h0); z = along * Math.sin(h0) - side * Math.cos(h0); }
    out.push(new THREE.Vector3(last.x + x * c - z * s + Math.cos(want) * step * 0.6, last.y + (p.y - o.y) * 0.6, last.z + x * s + z * c + Math.sin(want) * step * 0.6));
  });
  return out;
}
function append(route, piece) {
  const old = route.slice(0, Math.max(0, route.length - 8));
  const gap = (cand) => Math.min(...cand.map((p) => Math.min(...old.map((q) => Math.hypot(p.x - q.x, p.z - q.z)))));
  const a = placed(route, piece, false), b = placed(route, piece, true);
  route.push(...(old.length && gap(b) > gap(a) ? b : a));
}
const lengthOf = (pts) => pts.reduce((sum, p, i) => sum + (i ? Math.hypot(p.x - pts[i - 1].x, p.z - pts[i - 1].z) : 0), 0);

export function buildRoute(course) {
  const pts = toLocal(course.path, course.elevation);
  /* 주차장: 칸에서 북쪽(-z)으로 나와 오른쪽 통로, 출구 길 */
  const park = [[0, 0.8], [0, -6], [1.5, -11], [6, -14], [20, -14], [44, -14], [56, -15], [60, -20], [60, -40], [60, -80], [62, -130], [63, -180]].map(([x, z]) => new THREE.Vector3(x, 0, z));
  const route = park.slice(), zones = [{ mood: 'park', from: 0 }];
  const sources = [];
  course.sections.forEach((sec, si) => {
    const [a, b] = pickWindow(pts, sec.startIndex, sec.endIndex);
    const raw = pts.slice(Math.max(0, a - 2), b + 3);
    const piece = raw.slice(1, -1).map((p, i) => new THREE.Vector3((raw[i].x + 2 * p.x + raw[i + 2].x) / 4, (raw[i].y + 2 * p.y + raw[i + 2].y) / 4, (raw[i].z + 2 * p.z + raw[i + 2].z) / 4));
    zones.push({ mood: sec.mood, from: lengthOf(route), section: si, name: sec.name });
    sources.push({ section: si, start: a, end: b, at: lengthOf(route) });
    append(route, piece);
  });
  const curve = new THREE.CatmullRomCurve3(route, false, 'centripetal');
  const length = curve.getLength();
  /* 이어 붙인 거리와 실제 거리는 조금 다르므로 구간 시작 거리를 곡선 길이 비율로 맞춘다 */
  const raw = lengthOf(route), k = length / raw;
  zones.forEach((z) => { z.from *= k; });
  sources.forEach((s) => { s.at *= k; });
  zones.forEach((z, i) => { z.to = i + 1 < zones.length ? zones[i + 1].from : length; });
  return { curve, length, zones, sources };
}
export const zoneAt = (zones, d) => zones.find((z) => d >= z.from && d < z.to) || zones[zones.length - 1];

/* ---------- 땅, 도로, 선 ---------- */
const HILLS = { park: [0, 0], forest: [10, 7], city: [2, 2], river: [10, -1] };
const GRASS = { park: ['#8fb04c', '#9aae54'], forest: ['#8fb04c', '#b4aa52'], city: ['#96b262', '#a4b06c'], river: ['#b8b85c', '#c8ac58'] };
function hillsAt(zones, d) {
  const z = zoneAt(zones, d), i = zones.indexOf(z), next = zones[i + 1];
  const h = HILLS[z.mood];
  if (!next || d < z.to - 150) return h;
  const f = smooth((d - (z.to - 150)) / 150), n = HILLS[next.mood];
  return [h[0] + (n[0] - h[0]) * f, h[1] + (n[1] - h[1]) * f];
}
function makeGround(curve, length, zones, mats) {
  /* 단면 꼭짓점. 긴 삼각형은 다른 도로 위를 지날 수 있어 바깥도 50m 이하 간격으로 끊는다 */
  const offs = [-150, -100, -60, -35, -20, -12, -5.4, -4.4, 4.4, 5.4, 12, 20, 35, 60, 100, 150];
  const heightAt = (d, base, off) => {
    if (Math.abs(off) <= 4.4) return base;
    const [l, r] = hillsAt(zones, d), mood = zoneAt(zones, d).mood;
    if (mood === 'river' && off > 12) return base - 7;
    if (mood === 'city' && off > 70) return base - 5;
    const n = Math.sin(d * 0.013 + off) * 0.8 + Math.sin(d * 0.004 + off * 0.3) * 2.5;
    /* 언덕은 멀어질수록 높아지지만 상한(구간 계수 × 3m)에 다가갈 뿐이다. 끝없이 솟으면 하늘을 덮는다 */
    return base - 0.15 + (off < 0 ? l : r) * 3 * (1 - Math.exp(-Math.max(Math.abs(off) - 14, 0) / 50)) + (Math.abs(off) > 14 ? n * Math.min(1, (Math.abs(off) - 14) / 20) : 0);
  };
  const n = Math.floor(length / STEP), pos = [], col = [], idx = [], up = new THREE.Vector3(0, 1, 0), c = new THREE.Color();
  const kinds = [], alt = [], moods = [], uvs = [];
  /* 땅 꼭짓점이 자기 단면보다 다른 지점의 도로에 더 가까우면 그 도로의 땅 아래로 내린다.
     이어 붙인 길이 다른 구간 근처를 다시 지나거나, 급커브 안쪽에서 먼 단면끼리 엇갈려 접힐 때 땅이 머리 위를 덮지 않게 한다 */
  const SAMPLE = 8, samples = curve.getSpacedPoints(Math.ceil(length / SAMPLE)), sd = samples.map((_, i) => (i * length) / (samples.length - 1));
  const foreign = [];
  const tucked = (x, z, d, off, h) => {
    let best = -1, bd = off * off;
    for (let i = 0; i < samples.length; i++) {
      if (Math.abs(sd[i] - d) < 12) continue;   /* 자기 단면 근처만 빼고 모두 본다. 급커브 안쪽에서 접힌 단면도 잡는다 */
      const dd = (samples[i].x - x) ** 2 + (samples[i].z - z) ** 2;
      if (dd < bd) { bd = dd; best = i; }
    }
    foreign.push(best >= 0);
    if (best < 0) return h;
    const q = samples[best], rr = new THREE.Vector3().crossVectors(curve.getTangentAt(sd[best] / length), up).normalize();
    const o2 = (x - q.x) * rr.x + (z - q.z) * rr.z;
    return Math.min(h, heightAt(sd[best], q.y, o2) - 0.4);
  };
  for (let i = 0; i <= n; i++) {
    const d = i * STEP, u = d / length, p = curve.getPointAt(u), r = new THREE.Vector3().crossVectors(curve.getTangentAt(u), up).normalize(), mood = zoneAt(zones, d).mood;
    offs.forEach((off, k) => {
      const x = p.x + r.x * off, z = p.z + r.z * off, h = heightAt(d, p.y, off);
      uvs.push(off / 5, d / 5);
      if (Math.abs(off) <= 5.4) foreign.push(false);
      pos.push(x, Math.abs(off) <= 5.4 ? h : tucked(x, z, d, Math.abs(off), h), z);
      const a = Math.abs(off);
      kinds.push(a <= 4.4 ? (mood === 'park' && d < 70 ? 3 : 0) : a <= 5.4 ? 1 : 2); alt.push((i + k) % 3 ? 0 : 1); moods.push(mood);
      c.set(a <= 4.4 ? (mood === 'park' && d < 70 ? '#f0f0f0' : (i % 7 ? '#e4e4e4' : '#dcdcdc')) : a <= 5.4 ? '#b8ab8e' : GRASS[mood][(i + k) % 3 ? 0 : 1]);
      col.push(c.r, c.g, c.b);
    });
  }
  const w = offs.length;
  /* 다른 지점 도로에 더 가까운 꼭짓점이 하나라도 있는 면은 그리지 않는다. 그 자리는 그 도로의 땅이 덮는다.
     도로 면(가운데 칸)과 풀밭 면을 나눠 서로 다른 실사 재질을 쓴다 */
  const roadK = offs.indexOf(-4.4), roadIdx = [];
  for (let i = 0; i < n; i++) for (let k = 0; k < w - 1; k++) {
    const a = i * w + k;
    if (foreign[a] || foreign[a + 1] || foreign[a + w] || foreign[a + w + 1]) continue;
    (k === roadK ? roadIdx : idx).push(a, a + w, a + 1, a + 1, a + w, a + w + 1);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2));
  g.setIndex(idx.concat(roadIdx)); g.addGroup(0, idx.length, 0); g.addGroup(idx.length, roadIdx.length, 1); g.computeVertexNormals();
  const mesh = new THREE.Mesh(g, [
    new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide, map: mats.grass, normalMap: mats.grassN }),
    new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide, map: mats.road, normalMap: mats.roadN }),
  ]);
  mesh.receiveShadow = true;
  return { mesh, heightAt, kinds, alt, moods, mats };
}
function stripe(curve, from, to, off, width, color, dash) {
  const pos = [], idx = [], up = new THREE.Vector3(0, 1, 0); let q = 0;
  for (let d = from; d < to - 1; d += 1) {
    if (dash && Math.floor(d / dash) % 2) continue;
    [d, d + 1].forEach((m) => {
      const u = m / curve.getLength(), p = curve.getPointAt(Math.min(u, 1)), r = new THREE.Vector3().crossVectors(curve.getTangentAt(Math.min(u, 1)), up).normalize();
      [-width / 2, width / 2].forEach((w) => pos.push(p.x + r.x * (off + w), p.y + 0.03, p.z + r.z * (off + w)));
    });
    idx.push(q, q + 2, q + 1, q + 1, q + 2, q + 3); q += 4;
  }
  const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setIndex(idx);
  return new THREE.Mesh(g, new THREE.MeshBasicMaterial({ color, side: THREE.DoubleSide }));
}

/* ---------- 나무 (블렌더 모델) ---------- */
export async function loadTrees(url) {
  const gltf = await new GLTFLoader().loadAsync(url);
  const mesh = (name) => { const o = gltf.scene.getObjectByName(name); const g = o.geometry.clone(); g.translate(-o.position.x, -o.position.y, -o.position.z); return g; };
  /* 블렌더 나무에는 무늬 좌표가 없어 원기둥 투영으로 만든다 */
  return ['deciduous_0', 'deciduous_1', 'deciduous_2', 'conifer_0', 'conifer_1'].map((n) => ({ wood: cylinderUV(mesh(n + '_wood'), 2, 0.6), leaf: cylinderUV(mesh(n + '_leaf'), 3, 0.5) }));
}
/* 나무 무리. 계절이 바뀌면 recolor 로 잎 색만 다시 칠한다 */
export const LEAVES = {
  spring: ['#f4c4d4', '#f7d8e2', '#b8d86a', '#ffffff', '#a8cf5a'],
  summer: ['#4f8a34', '#5f9a3a', '#3e7a2c', '#6aa244', '#4a8030'],
  autumn: ['#e0702a', '#d8452e', '#f2b33a', '#e8902f', '#9bbf48', '#c8562a'],
  winter: ['#e9eef3', '#dfe6ec', '#f4f7fa', '#d8dfe6'],
};
const CONIFER = { spring: ['#3a6a34', '#46783c'], summer: ['#2f5a2a', '#3a6630'], autumn: ['#2f5a2a', '#3a6630', '#2a4f27'], winter: ['#dce4ea', '#9fb3a6'] };
/* 땅 색: 계절·날씨에 따라 풀과 노면을 다시 칠한다. 'zone' 은 구간 기본색 */
const GROUND = {
  spring: { grass: ['#9fd060', '#b2da74'], road: ['#e4e4e4', '#dcdcdc'], shoulder: '#b8ab8e' },
  summer: { grass: ['#6aa83e', '#78b04a'], road: ['#e4e4e4', '#dcdcdc'], shoulder: '#b8ab8e' },
  snow: { grass: ['#ffffff', '#f2f5f8'], road: ['#ffffff', '#f4f4f4'], shoulder: '#f4f6f8' },
  wet: { grass: ['#5c7a3a', '#68803e'], road: ['#8a8d92', '#84878c'], shoulder: '#8a8070' },
};
export function paintGround(ground, style) {
  const col = ground.mesh.geometry.attributes.color, c = new THREE.Color();
  for (let i = 0; i < ground.kinds.length; i++) {
    const k = ground.kinds[i], g = GROUND[style];
    if (k === 3) c.set(style === 'snow' ? '#ffffff' : style === 'wet' ? '#8a8d92' : '#f0f0f0');
    else if (!g) c.set(k === 0 ? (ground.alt[i] ? '#dcdcdc' : '#e4e4e4') : k === 1 ? '#b8ab8e' : GRASS[ground.moods[i]][ground.alt[i]]);
    else c.set(k === 0 ? g.road[ground.alt[i]] : k === 1 ? g.shoulder : g.grass[ground.alt[i]]);
    col.setXYZ(i, c.r, c.g, c.b);
  }
  col.needsUpdate = true;
  ground.mesh.material[1].map = style === 'snow' ? ground.mats.roadSnow : ground.mats.road;
}
export function recolor(groups, season) {
  const c = new THREE.Color();
  groups.forEach((g) => {
    const pal = g.conifer ? CONIFER[season] : LEAVES[season];
    g.list.forEach((t, i) => g.leaf.setColorAt(i, c.set(pal[Math.floor(t.seed * 997) % pal.length])));
    g.leaf.instanceColor.needsUpdate = true;
  });
}
function plantTrees(scene, kinds, trees, mats) {
  const m = new THREE.Matrix4(), q = new THREE.Quaternion(), Y = new THREE.Vector3(0, 1, 0), groups = [];
  kinds.forEach((kind, k) => {
    const list = trees.filter((t) => t.kind === k);
    if (!list.length) return;
    const leaf = new THREE.InstancedMesh(kind.leaf, new THREE.MeshLambertMaterial({ vertexColors: true, map: mats.leaves }), list.length);
    const wood = new THREE.InstancedMesh(kind.wood, new THREE.MeshLambertMaterial({ map: mats.bark, normalMap: mats.barkN }), list.length);
    leaf.castShadow = wood.castShadow = true;
    list.forEach((t, i) => {
      const s = t.h / 7;
      m.compose(new THREE.Vector3(t.x, t.y - 0.1, t.z), q.setFromAxisAngle(Y, (i * 2.399) % 6.28), new THREE.Vector3(s, s, s));
      leaf.setMatrixAt(i, m); wood.setMatrixAt(i, m); leaf.setColorAt(i, new THREE.Color('#ffffff'));
    });
    scene.add(leaf, wood);
    groups.push({ leaf, list, conifer: k >= 3 });
  });
  return groups;
}

/* ---------- 하늘: 풍경 사진 띠 + 그라데이션 + 해 ---------- */
function backdrop(tex, w, h) {
  const R = 3200; tex.wrapS = THREE.RepeatWrapping; tex.repeat.set(3, 1);
  const height = (2 * Math.PI * R / 3) * h / w, geo = new THREE.CylinderGeometry(R, R, height, 96, 1, true);
  geo.translate(0, height / 2 - 60, 0);
  const mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ map: tex, side: THREE.BackSide, fog: false, depthWrite: false, transparent: true }));
  mesh.renderOrder = -1; return mesh;
}
function skyDome() {
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: { top: { value: new THREE.Color('#b3c5e5') }, zenith: { value: new THREE.Color('#3f7cc8') } },
    vertexShader: 'varying vec3 v; void main(){ v = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: 'uniform vec3 top; uniform vec3 zenith; varying vec3 v; void main(){ float h = clamp((v.y - 0.3) / 0.7, 0.0, 1.0); gl_FragColor = vec4(mix(top, zenith, pow(h, 0.7)), 1.0); }',
  });
  const dome = new THREE.Mesh(new THREE.SphereGeometry(4200, 32, 16), mat); dome.renderOrder = -2; return dome;
}
function sunSprite() {
  const c = document.createElement('canvas'); c.width = c.height = 256;
  const g = c.getContext('2d'), grad = g.createRadialGradient(128, 128, 0, 128, 128, 128);
  grad.addColorStop(0, 'rgba(255,255,250,1)'); grad.addColorStop(0.08, 'rgba(255,252,235,1)'); grad.addColorStop(0.16, 'rgba(255,240,200,0.55)');
  grad.addColorStop(0.45, 'rgba(255,220,160,0.16)'); grad.addColorStop(1, 'rgba(255,210,150,0)');
  g.fillStyle = grad; g.fillRect(0, 0, 256, 256);
  const tx = new THREE.CanvasTexture(c); tx.colorSpace = THREE.SRGBColorSpace;
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tx, fog: false, depthWrite: false, transparent: true, toneMapped: false }));
  sp.scale.set(900, 900, 1); return sp;
}

/* ---------- 세상 만들기 ---------- */
export async function buildWorld(course, assets) {
  const { curve, length, zones, sources } = buildRoute(course);
  const scene = new THREE.Scene();
  const ground = makeGround(curve, length, zones, assets.mats);
  scene.add(ground.mesh);
  /* 땅 띠 바깥: 경로에서 가장 낮은 곳보다 아래에 넓은 바닥을 깐다. 어느 도로에서도 머리 위로 오지 않는다 */
  const lowY = Math.min(...curve.getSpacedPoints(400).map((q) => q.y)) - 12;
  const baseTex = assets.mats.grass.clone(); baseTex.repeat.set(2400, 2400); baseTex.needsUpdate = true;
  const base = new THREE.Mesh(new THREE.PlaneGeometry(12000, 12000), new THREE.MeshLambertMaterial({ map: baseTex, color: '#8fb04c' }));
  base.rotation.x = -Math.PI / 2; base.position.set(curve.getPointAt(0.5).x, lowY, curve.getPointAt(0.5).z); base.receiveShadow = true; scene.add(base);
  /* 도로선은 주차장 출구 길(통로 끝)부터 끝까지 */
  const lineFrom = 70;
  scene.add(stripe(curve, lineFrom, length, -0.12, 0.12, '#f2c230'), stripe(curve, lineFrom, length, 0.12, 0.12, '#f2c230'));
  scene.add(stripe(curve, lineFrom, length, 4.0, 0.15, '#f4f4f0'), stripe(curve, lineFrom, length, -4.0, 0.15, '#f4f4f0'));
  const up = new THREE.Vector3(0, 1, 0), m = new THREE.Matrix4(), col = new THREE.Color();
  const frame = (d) => { const u = Math.min(Math.max(d / length, 0), 1), p = curve.getPointAt(u), r = new THREE.Vector3().crossVectors(curve.getTangentAt(u), up).normalize(); return { p, r }; };
  /* 물체를 놓을 땅: 놓을 자리에서 가장 가까운 도로 지점을 찾아 그 단면의 높이를 쓴다.
     굽은 길 안쪽에서는 단면이 겹쳐, 놓을 때 쓴 단면과 실제로 보이는 땅이 다를 수 있다 */
  const STEP4 = 4, samples = curve.getSpacedPoints(Math.ceil(length / STEP4)), sampleD = samples.map((_, i) => (i * length) / (samples.length - 1));
  const nearest = (x, z) => {
    let best = 0, bd = Infinity;
    for (let i = 0; i < samples.length; i++) { const dd = (samples[i].x - x) ** 2 + (samples[i].z - z) ** 2; if (dd < bd) { bd = dd; best = i; } }
    const d = sampleD[best], { p, r } = frame(d), off = (x - p.x) * r.x + (z - p.z) * r.z;
    return { d, p, off, dist: Math.sqrt(bd) };
  };
  const groundAt = (x, z) => { const n = nearest(x, z); return { y: ground.heightAt(n.d, n.p.y, n.off), dist: n.dist }; };
  const clear = (x, z, gap) => nearest(x, z).dist >= gap;
  /* 주차장: 칸, 차, 건물 */
  const lotTex = assets.mats.road.clone(); lotTex.repeat.set(28, 14); lotTex.needsUpdate = true;
  const lot = new THREE.Mesh(new THREE.PlaneGeometry(140, 70), new THREE.MeshLambertMaterial({ map: lotTex, color: '#f0f0f0' }));
  lot.rotation.x = -Math.PI / 2; lot.position.set(20, 0.01, -14); lot.receiveShadow = true; scene.add(lot);
  const line = (x, z, w, d) => { const g = new THREE.Mesh(new THREE.PlaneGeometry(w, d), new THREE.MeshBasicMaterial({ color: '#e9e9e4' })); g.rotation.x = -Math.PI / 2; g.position.set(x, 0.03, z); scene.add(g); };
  for (let x = -30; x <= 56; x += 2.7) { line(x - 1.35, 0, 0.12, 5.4); line(x - 1.35, -28, 0.12, 5.4); }
  line(13, -2.7, 86, 0.12); line(13, -25.3, 86, 0.12);
  const rand = rng(4242), spots = [];
  for (let x = -30; x <= 56; x += 2.7) { if (Math.abs(x) > 6 && rand() < 0.8) spots.push([x, 0, 0]); if (rand() < 0.75) spots.push([x, -28, Math.PI]); }
  const body = new THREE.BoxGeometry(1.85, 0.75, 4.6); body.translate(0, 0.62, 0);
  const cabin = new THREE.BoxGeometry(1.6, 0.6, 2.5); cabin.translate(0, 1.3, -0.2);
  const bodies = new THREE.InstancedMesh(body, new THREE.MeshLambertMaterial(), spots.length), cabins = new THREE.InstancedMesh(cabin, new THREE.MeshLambertMaterial({ color: '#23262b' }), spots.length);
  const paints = ['#f2f2f0', '#1d1f22', '#9aa0a6', '#c8ccd0', '#2c4a74', '#7a1f24', '#e7e3da'];
  spots.forEach(([x, z, r], i) => { m.compose(new THREE.Vector3(x, 0, z), new THREE.Quaternion().setFromAxisAngle(up, r), new THREE.Vector3(1, 1, 1)); bodies.setMatrixAt(i, m); cabins.setMatrixAt(i, m); bodies.setColorAt(i, col.set(paints[Math.floor(rand() * paints.length)])); });
  bodies.castShadow = cabins.castShadow = true; scene.add(bodies, cabins);
  const building = new THREE.Mesh(new THREE.BoxGeometry(70, 14, 16), new THREE.MeshLambertMaterial({ color: '#c9c6bf' }));
  building.position.set(10, 7, 16); building.castShadow = true; scene.add(building);
  /* 나무: 구간마다 밀도가 다르다 */
  const trees = [];
  zones.forEach((z, zi) => {
    const count = { park: 140, forest: 2600, city: 300, river: 700 }[z.mood], r = rng(zi * 977 + 13);
    for (let i = 0; i < count; i++) {
      const d = z.from + r() * (z.to - z.from), side = r() < 0.5 ? -1 : 1;
      if (z.mood === 'river' && side > 0 && r() < 0.85) continue;
      const off = (z.mood === 'park' ? 40 : 11) + Math.pow(r(), 1.3) * 110, { p, r: rr } = frame(d);
      const x = p.x + rr.x * off * side, zz = p.z + rr.z * off * side;
      const g = groundAt(x, zz);
      if (g.dist < 12) continue;
      trees.push({ x, z: zz, y: g.y, h: 7 + r() * 6, kind: Math.floor(r() * 5), seed: r() });
    }
  });
  const treeGroups = plantTrees(scene, assets.trees, trees, assets.mats);
  recolor(treeGroups, 'autumn');
  /* 도심 건물 */
  const city = zones.find((z) => z.mood === 'city'), blocks = [], rb = rng(99);
  for (let i = 0; i < 420; i++) {
    const d = city.from + rb() * (city.to - city.from), side = rb() < 0.5 ? -1 : 1;
    if (side > 0 && rb() < 0.45) continue;
    const off = 30 + rb() * 140, { p, r } = frame(d), x = p.x + r.x * off * side, z = p.z + r.z * off * side;
    const g = groundAt(x, z);
    if (g.dist < 22) continue;
    blocks.push({ x, z, y: g.y, h: 12 + Math.pow(rb(), 2) * 70, w: 12 + rb() * 18, dd: 12 + rb() * 16, c: ['#ffffff', '#f2ece2', '#e4e9ef', '#efe8dc', '#dfe3e7'][Math.floor(rb() * 5)] });
  }
  const box = new THREE.BoxGeometry(1, 1, 1); box.translate(0, 0.5, 0);
  const facade = assets.mats.facade.clone(); facade.repeat.set(2, 3); facade.needsUpdate = true;
  const houses = new THREE.InstancedMesh(box, new THREE.MeshLambertMaterial({ map: facade }), blocks.length); houses.castShadow = true;
  blocks.forEach((b, i) => { m.compose(new THREE.Vector3(b.x, b.y - 0.5, b.z), new THREE.Quaternion().setFromAxisAngle(up, (i * 0.7) % 1.5), new THREE.Vector3(b.w, b.h, b.dd)); houses.setMatrixAt(i, m); houses.setColorAt(i, col.set(b.c)); });
  scene.add(houses);
  /* 강: 강변 구간 오른쪽 */
  const river = zones.find((z) => z.mood === 'river');
  const wpos = [], widx = [];
  for (let d = river.from - 100, q = 0; d < river.to; d += 8, q += 2) {
    const { p, r } = frame(d), y = p.y - 4;
    wpos.push(p.x + r.x * 12, y, p.z + r.z * 12, p.x + r.x * 700, y, p.z + r.z * 700);
    if (d > river.from - 100) widx.push(q - 2, q - 1, q, q, q - 1, q + 1);
  }
  const wg = new THREE.BufferGeometry(); wg.setAttribute('position', new THREE.Float32BufferAttribute(wpos, 3)); wg.setIndex(widx); wg.computeVertexNormals();
  const water = new THREE.Mesh(wg, new THREE.MeshLambertMaterial({ color: '#7f9fb8', side: THREE.DoubleSide })); scene.add(water);
  /* 가로등 */
  const poleSpots = [];
  for (let d = 90; d < length; d += 40) {
    const { p, r } = frame(d), x = p.x + r.x * 6.8, z = p.z + r.z * 6.8, g = groundAt(x, z);
    if (g.dist >= 5.8) poleSpots.push([x, g.y, z]);
  }
  const pole = new THREE.BoxGeometry(0.15, 8, 0.15); pole.translate(0, 4, 0);
  const lamps = new THREE.InstancedMesh(pole, new THREE.MeshLambertMaterial({ color: '#6c7075' }), poleSpots.length);
  poleSpots.forEach(([x, y, z], i) => { m.makeTranslation(x, y - 0.2, z); lamps.setMatrixAt(i, m); });
  lamps.castShadow = true; scene.add(lamps);
  /* 가로등 불: 밤에만 밝힌다 */
  const bulbs = new THREE.InstancedMesh(new THREE.SphereGeometry(0.35, 8, 6), new THREE.MeshBasicMaterial({ color: '#4a4a44', toneMapped: false }), poleSpots.length);
  poleSpots.forEach(([x, y, z], i) => { m.makeTranslation(x, y + 7.9, z); bulbs.setMatrixAt(i, m); });
  scene.add(bulbs);
  /* 하늘과 빛 */
  const dome = skyDome(); scene.add(dome);
  const backs = assets.skies.map((s) => backdrop(s.tex, s.w, s.h)); backs.forEach((b) => scene.add(b));
  const sunDisc = sunSprite(); scene.add(sunDisc);
  const hemi = new THREE.HemisphereLight('#dde6f0', '#6b6a4a', 1.6); scene.add(hemi);
  const sun = new THREE.DirectionalLight('#fff4e0', 3.2);
  sun.castShadow = true; sun.shadow.mapSize.set(2048, 2048);
  Object.assign(sun.shadow.camera, { left: -90, right: 90, top: 90, bottom: -90, near: 10, far: 700 }); sun.shadow.bias = -0.0006;
  scene.add(sun, sun.target);
  scene.fog = new THREE.Fog('#c6d4e2', 250, 3000);
  return { scene, curve, length, zones, sources, frame, treeGroups, trees, dome, backs, sunDisc, hemi, sun, water, ground, groundAt, lamps, bulbs, base };
}
