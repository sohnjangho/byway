// 시뮬레이터 바깥 세상. 주차장 → 숲길 → 도심(팔당대교 쪽) → 강변을 한 길로 이어 붙인다.
// 숲·도심·강변 구간은 코스 파일의 실제 경로·고도에서 굽이가 많은 약 1.2km씩을 떼어 진행 방향을 맞춰 잇는다.
// 가까운 곳(도로 양옆 약 200m)은 3D, 먼 산과 하늘은 풍경 사진 띠를 카메라 둘레 원통에 입힌다.
import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { cylinderUV, boxUV } from './tex.js';

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
/* 풀 색: 흑백 풀 무늬에 곱한다. 채도를 낮춘 올리브·황록 두 색을 넓은 얼룩(blotch)으로 섞어 사진 같은 풀밭을 만든다 */
const GRASS = { park: ['#7d8f4e', '#958f5a'], forest: ['#71854a', '#8f8a55'], city: ['#7f8d5a', '#949063'], river: ['#999a62', '#a89660'] };
/* 풀 얼룩: 위치에 따라 0~1. 수십 m 크기로 두 풀 색이 번갈아 나온다 */
const blotch = (x, z) => smooth(0.5 + 0.9 * Math.sin(x * 0.045 + Math.sin(z * 0.03) * 2) * Math.sin(z * 0.05 + Math.sin(x * 0.02) * 2));
function hillsAt(zones, d) {
  const z = zoneAt(zones, d), i = zones.indexOf(z), next = zones[i + 1], prev = zones[i - 1];
  const h = HILLS[z.mood];
  /* 주차장 길은 칸 앞 통로에서 꺾여 나가 땅 띠가 서로 맞닿는다. 주차장 끝에서 언덕을 올리면 통로 쪽 평지와 맞닿는 곳에
     10m 넘는 단이 져 언덕 조각이 공중에 떠 보인다. 그래서 주차장 안은 평지로 두고 다음 구간 첫 150m에서 올린다 */
  if (prev && prev.mood === 'park' && d < z.from + 150) {
    const f = smooth((d - z.from) / 150), p = HILLS.park;
    return [p[0] + (h[0] - p[0]) * f, p[1] + (h[1] - p[1]) * f];
  }
  if (!next || z.mood === 'park' || d < z.to - 150) return h;
  const f = smooth((d - (z.to - 150)) / 150), n = HILLS[next.mood];
  return [h[0] + (n[0] - h[0]) * f, h[1] + (n[1] - h[1]) * f];
}
function makeGround(curve, length, zones, mats) {
  /* 단면 꼭짓점. 긴 삼각형은 다른 도로 위를 지날 수 있어 바깥도 50m 이하 간격으로 끊는다.
     ±200 은 치마: 띠 바깥 테두리를 경로 최저점 − 13m 로 내린다. 높은 구간 띠를 밖(주차장 등)에서 볼 때
     ±150 에서 뚝 끊긴 언덕 단면이 공중에 뜬 판처럼 보이지 않고 아래 바닥까지 비탈로 내려간다 */
  const offs = [-200, -150, -100, -60, -35, -20, -12, -5.4, -4.4, 4.4, 5.4, 12, 20, 35, 60, 100, 150, 200];
  const skirtY = Math.min(...curve.getSpacedPoints(400).map((q) => q.y)) - 13;
  const heightAt = (d, base, off) => {
    if (Math.abs(off) <= 4.4) return base;
    const [l, r] = hillsAt(zones, d), mood = zoneAt(zones, d).mood;
    if (mood === 'river' && off > 12) return base - 7;
    if (mood === 'city' && off > 70) return base - 5;
    const n = Math.sin(d * 0.013 + off) * 0.8 + Math.sin(d * 0.004 + off * 0.3) * 2.5;
    /* 언덕은 멀어질수록 높아지지만 상한(구간 계수 × 3m)에 다가갈 뿐이다. 끝없이 솟으면 하늘을 덮는다 */
    return base - 0.15 + (off < 0 ? l : r) * 3 * (1 - Math.exp(-Math.max(Math.abs(off) - 14, 0) / 50)) + (Math.abs(off) > 14 ? n * Math.min(1, (Math.abs(off) - 14) / 20) : 0);
  };
  const n = Math.floor(length / STEP), pos = [], col = [], idx = [], up = new THREE.Vector3(0, 1, 0), c = new THREE.Color(), c2 = new THREE.Color();
  const kinds = [], alt = [], moods = [], uvs = [], blot = [];
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
      const a = Math.abs(off);
      /* 치마 꼭짓점은 늘 바닥 높이지만, 급커브 안쪽처럼 다른 지점 도로에 더 가까우면 그 면을 그리지 않도록 검사는 한다 */
      if (a <= 5.4) foreign.push(false);
      pos.push(x, a <= 5.4 ? h : a > 150 ? (tucked(x, z, d, a, h), skirtY) : tucked(x, z, d, a, h), z);
      const b = blotch(x, z); blot.push(b);
      kinds.push(a <= 4.4 ? (mood === 'park' && d < 70 ? 3 : 0) : a <= 5.4 ? 1 : 2); alt.push((i + k) % 3 ? 0 : 1); moods.push(mood);
      if (a > 5.4) c.set(GRASS[mood][0]).lerp(c2.set(GRASS[mood][1]), b);
      else c.set(a <= 4.4 ? (mood === 'park' && d < 70 ? '#f0f0f0' : (i % 7 ? '#e4e4e4' : '#dcdcdc')) : '#b8ab8e');
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
  return { mesh, heightAt, kinds, alt, blot, moods, mats };
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
  /* 블렌더 파일은 종마다 x 로 9m 씩 떨어뜨려 두었다. 꼭짓점이 물체 기준 좌표인지 장면 좌표인지에 기대지 않도록
     줄기 밑동(가장 낮은 0.3m 안 꼭짓점 평균)을 원점으로 옮긴다. 노드 위치를 빼던 방식은 꼭짓점이 이미 물체 기준이라
     단풍나무 이후 종이 9·k m 씩 옆으로 밀려 심겼다(도로 12m 금지 규칙도 깨졌다) */
  const geo = (name) => gltf.scene.getObjectByName(name).geometry.clone();
  return ['deciduous_0', 'deciduous_1', 'deciduous_2', 'conifer_0', 'conifer_1'].map((n) => {
    const wood = geo(n + '_wood'), leaf = geo(n + '_leaf'), p = wood.attributes.position;
    let minY = Infinity, sx = 0, sz = 0, k = 0;
    for (let i = 0; i < p.count; i++) minY = Math.min(minY, p.getY(i));
    for (let i = 0; i < p.count; i++) if (p.getY(i) < minY + 0.3) { sx += p.getX(i); sz += p.getZ(i); k++; }
    [wood, leaf].forEach((g) => g.translate(-sx / k, 0, -sz / k));
    /* 블렌더 나무에는 무늬 좌표가 없어 원기둥 투영으로 만든다 */
    return { wood: cylinderUV(wood, 2, 0.6), leaf: sphereUV(leaf, 3, 2) };
  });
}
/* 구면 투영: 잎 뭉치(수관). 원기둥 투영은 수관 위아래 면에서 세로 줄무늬가 진다. 수관 가운데에서 본 경도·위도를 무늬 좌표로 쓴다 */
function sphereUV(geo, around = 3, up = 2) {
  geo.computeBoundingBox();
  const c = geo.boundingBox.getCenter(new THREE.Vector3()), p = geo.attributes.position, uv = new Float32Array(p.count * 2), v = new THREE.Vector3();
  for (let i = 0; i < p.count; i++) {
    v.fromBufferAttribute(p, i).sub(c).normalize();
    uv[i * 2] = (Math.atan2(v.z, v.x) / (Math.PI * 2) + 0.5) * around; uv[i * 2 + 1] = (Math.acos(Math.max(-1, Math.min(1, v.y))) / Math.PI) * up;
  }
  geo.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  return geo;
}
/* 나무 무리. 계절이 바뀌면 recolor 로 잎 색만 다시 칠한다.
   종별 잎 색: [0 느티나무, 1 단풍나무, 2 은행나무, 3 소나무(상록), 4 메타세쿼이아]. 한 그루 안에서는 모델 꼭짓점 색이 곱해져 얼룩이 진다.
   겨울 활엽수는 잎이 거의 없는 느낌으로 밝은 회갈색, 눈(snow)에서는 흰색.
   봄에는 단풍나무(1) 자리를 벚꽃색(연분홍)으로 칠한다. 수종과 맞지 않는 연출이다(가로수 벚꽃길 느낌).
   잎 무늬(회색)·꼭짓점 색이 곱해져 탁한 자주로 가라앉으므로 #f3c6d3 계열을 BRIGHT 배로 밝힌다 */
const PINE = ['#2f5a2a', '#3a6630', '#2a4f27'];
const SPECIES = {
  spring: [['#a6c766', '#b4d078', '#9abd5e'], ['#f8c3cf', '#fbcfd8', '#f3b8c6'], ['#b8d46c', '#c6dc82'], ['#3f6a34', '#4a7640'], ['#bcd884', '#c8e092']],
  summer: [['#4f7f34', '#5a8a3a', '#46762e'], ['#4a7a30', '#568636', '#42702c'], ['#5c8a38', '#66923e'], PINE, ['#4f8236', '#5a8c3e']],
  autumn: [['#b8642a', '#c4762e', '#a8582a'], ['#b8221c', '#d0381e', '#e0582a', '#a81c1c'], ['#ffd23a', '#ffdc50', '#f6c428'], PINE, ['#a8522a', '#b8602e', '#96462a']],
  winter: [['#c2b8aa', '#cec4b6'], ['#bcb0a2', '#c8bcae'], ['#c6bcae', '#d2c8ba'], PINE, ['#b09c88', '#bca894']],
  snow: [['#eef2f6', '#e4eaf0'], ['#eef2f6', '#e4eaf0'], ['#eef2f6', '#e4eaf0'], ['#46684a', '#5a7a5c'], ['#e8edf2', '#dde4ea']],
};
const BRIGHT = { spring: [1, 1.55, 1, 1, 1] };
/* 땅 색: 계절·날씨에 따라 풀과 노면을 다시 칠한다. 'zone' 은 구간 기본색 */
const GROUND = {
  spring: { grass: ['#8aa456', '#a2a866'], road: ['#e4e4e4', '#dcdcdc'], shoulder: '#b8ab8e' },
  summer: { grass: ['#5a7d3c', '#748446'], road: ['#e4e4e4', '#dcdcdc'], shoulder: '#b8ab8e' },
  snow: { grass: ['#ffffff', '#f2f5f8'], road: ['#ffffff', '#f4f4f4'], shoulder: '#f4f6f8' },
  wet: { grass: ['#5c7a3a', '#68803e'], road: ['#8a8d92', '#84878c'], shoulder: '#8a8070' },
};
export function paintGround(ground, style) {
  const col = ground.mesh.geometry.attributes.color, c = new THREE.Color(), c2 = new THREE.Color();
  for (let i = 0; i < ground.kinds.length; i++) {
    const k = ground.kinds[i], g = GROUND[style];
    if (k === 3) c.set(style === 'snow' ? '#ffffff' : style === 'wet' ? '#8a8d92' : '#f0f0f0');
    else if (k === 2) { const pal = g ? g.grass : GRASS[ground.moods[i]]; c.set(pal[0]).lerp(c2.set(pal[1]), ground.blot[i]); }
    else if (!g) c.set(k === 0 ? (ground.alt[i] ? '#dcdcdc' : '#e4e4e4') : '#b8ab8e');
    else c.set(k === 0 ? g.road[ground.alt[i]] : g.shoulder);
    col.setXYZ(i, c.r, c.g, c.b);
  }
  col.needsUpdate = true;
  ground.mesh.material[1].map = style === 'snow' ? ground.mats.roadSnow : ground.mats.road;
}
export function recolor(groups, season) {
  const c = new THREE.Color(), table = SPECIES[season] || SPECIES.autumn, gain = BRIGHT[season];
  groups.forEach((g) => {
    g.list.forEach((t, i) => { const pal = table[t.kind]; g.leaf.setColorAt(i, c.set(pal[Math.floor(t.seed * 997) % pal.length]).multiplyScalar(gain ? gain[t.kind] : 1)); });
    g.leaf.instanceColor.needsUpdate = true;
  });
}
/* 나무는 경로 거리 CHUNK(300m) 마디 × 수종으로 InstancedMesh 를 나눈다. 한 덩어리가 세상 전체를 덮으면 경계구가 커서
   카메라·그림자 카메라 밖이어도 걸러지지 않고 수천 그루를 매번(그림자 패스까지 두 번) 다 그린다.
   t.d: 가장 가까운 도로 거리, t.s: 배율을 직접 줄 때, t.lean·t.axis: 기울기(라디안)와 기울이는 축(단풍 터널) */
const CHUNK = 300;
function plantTrees(scene, kinds, trees, mats) {
  const m = new THREE.Matrix4(), q = new THREE.Quaternion(), q2 = new THREE.Quaternion(), Y = new THREE.Vector3(0, 1, 0), groups = [], buckets = new Map();
  const leafMat = new THREE.MeshLambertMaterial({ vertexColors: true, map: mats.leaves });
  /* 줄기에도 꼭짓점 색이 있다(소나무 윗줄기만 적갈색) */
  const woodMat = new THREE.MeshLambertMaterial({ vertexColors: true, map: mats.bark, normalMap: mats.barkN });
  trees.forEach((t) => { const key = Math.floor(t.d / CHUNK) * 8 + t.kind; if (!buckets.has(key)) buckets.set(key, []); buckets.get(key).push(t); });
  [...buckets.keys()].sort((a, b) => a - b).forEach((key) => {
    const list = buckets.get(key), k = key % 8, kind = kinds[k];
    const leaf = new THREE.InstancedMesh(kind.leaf, leafMat, list.length), wood = new THREE.InstancedMesh(kind.wood, woodMat, list.length);
    list.forEach((t, i) => {
      /* 모델 높이 11m. 메타세쿼이아는 그대로 키우면 20m 까지 커져 0.75 배 */
      const s = t.s || (t.h / 7) * (k === 4 ? 0.75 : 1);
      q.setFromAxisAngle(Y, (t.seed * 7919) % 6.28);
      if (t.lean) q.premultiply(q2.setFromAxisAngle(t.axis, t.lean));
      m.compose(new THREE.Vector3(t.x, t.y - 0.1, t.z), q, new THREE.Vector3(s, s, s));
      leaf.setMatrixAt(i, m); wood.setMatrixAt(i, m); leaf.setColorAt(i, new THREE.Color('#ffffff'));
    });
    /* 마디마다 경계를 맞춰 둔다. 그림자·줄기 켜기는 updateChunks 가 카메라 거리로 정한다 */
    leaf.computeBoundingSphere(); wood.computeBoundingSphere(); leaf.computeBoundingBox(); wood.computeBoundingBox();
    scene.add(leaf, wood);
    groups.push({ leaf, wood, list, kind: k, chunk: Math.floor(key / 8), box: leaf.boundingBox.clone().union(wood.boundingBox) });
  });
  return groups;
}

/* ---------- 강변 꽃풀 ---------- */
/* 캔버스로 그린 풀 덤불 알파 무늬. kind: 'cosmos'(분홍·흰·자주 꽃), 'reed'(억새 은빛 이삭), 'grass'(초록 풀) */
function meadowTexture(kind, seed) {
  const c = document.createElement('canvas'); c.width = c.height = 256;
  const g = c.getContext('2d'), r = rng(seed), pick = (a) => a[Math.floor(r() * a.length)];
  g.lineCap = 'round';
  const blade = (x, top, bend, color, w) => { g.strokeStyle = color; g.lineWidth = w; g.beginPath(); g.moveTo(x, 256); g.quadraticCurveTo(x + bend * 0.3, (256 + top) / 2, x + bend, top); g.stroke(); return [x + bend, top]; };
  if (kind === 'grass') for (let i = 0; i < 70; i++) blade(20 + r() * 216, 70 + r() * 150, (r() - 0.5) * 70, pick(['#5d8a3a', '#6f9a44', '#4c7a30', '#82a64e']), 2 + r() * 2.5);
  if (kind === 'cosmos') {
    for (let i = 0; i < 30; i++) {
      const [tx, ty] = blade(24 + r() * 208, 30 + r() * 120, (r() - 0.5) * 50, pick(['#4f7a32', '#5a8638']), 1.8);
      /* 잎: 줄기 둘레의 가는 깃털 잎 */
      for (let k = 0; k < 4; k++) { const y = ty + (256 - ty) * (0.3 + k * 0.17), x = tx + (r() - 0.5) * 10; g.strokeStyle = '#5f8c3a'; g.lineWidth = 1.4; g.beginPath(); g.moveTo(x, y); g.lineTo(x + (r() - 0.5) * 26, y - 6 - r() * 8); g.stroke(); }
      const rad = 8 + r() * 6, petal = pick(['#f49ac1', '#ffffff', '#e0559a', '#f7b6d2', '#c2367f', '#fbe3ee']);
      g.fillStyle = petal;
      for (let k = 0; k < 8; k++) { const a = (k / 8) * Math.PI * 2; g.beginPath(); g.ellipse(tx + Math.cos(a) * rad * 0.55, ty + Math.sin(a) * rad * 0.45, rad * 0.5, rad * 0.26, a, 0, Math.PI * 2); g.fill(); }
      g.fillStyle = '#f2c230'; g.beginPath(); g.arc(tx, ty, 2.6, 0, Math.PI * 2); g.fill();
    }
  }
  if (kind === 'reed') {
    for (let i = 0; i < 34; i++) blade(60 + r() * 136, 60 + r() * 140, (r() - 0.5) * 180, pick(['#8f8a5a', '#a39a62', '#7e7a4c']), 2.2);
    /* 이삭: 꼭대기에서 한쪽으로 처지는 가는 은빛 털 */
    for (let i = 0; i < 8; i++) {
      const [tx, ty] = blade(70 + r() * 116, 8 + r() * 50, (r() - 0.5) * 60, '#b8a878', 2), lean = r() < 0.5 ? -1 : 1;
      for (let k = 0; k < 26; k++) {
        const len = 22 + r() * 30, a = Math.PI / 2 + lean * (0.15 + r() * 0.5);
        g.strokeStyle = pick(['rgba(244,240,230,0.95)', 'rgba(232,220,206,0.95)', 'rgba(250,248,242,0.9)']); g.lineWidth = 1.3;
        const y0 = ty + r() * 10; g.beginPath(); g.moveTo(tx, y0); g.quadraticCurveTo(tx + Math.cos(a) * len * 0.5 * lean, y0 + len * 0.3, tx + Math.cos(a) * len * lean, y0 + Math.sin(a) * len); g.stroke();
      }
    }
  }
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
}
/* 두 장 교차 판(폭 1, 높이 1, 밑이 원점). 노멀을 모두 위로 두어 땅과 같은 밝기로 비친다(판 방향 따라 얼룩지지 않게) */
function crossQuads() {
  const pos = [], uv = [], nor = [], idx = [];
  [0, Math.PI / 2].forEach((a, k) => {
    const cx = Math.cos(a) * 0.5, cz = Math.sin(a) * 0.5;
    [[-1, 0, 0, 0], [1, 0, 1, 0], [1, 1, 1, 1], [-1, 1, 0, 1]].forEach(([sx, y, u, v]) => { pos.push(cx * sx, y, cz * sx); uv.push(u, v); nor.push(0, 1, 0); });
    const b = k * 4; idx.push(b, b + 1, b + 2, b, b + 2, b + 3);
  });
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2)); g.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3)); g.setIndex(idx);
  return g;
}
function makeMeadow(river, frame, groundAt, avoid) {
  const geo = crossQuads(), r = rng(71), m = new THREE.Matrix4(), q = new THREE.Quaternion(), Y = new THREE.Vector3(0, 1, 0), spots = { cosmos: [], reed: [] };
  for (let i = 0; i < 4200; i++) {
    const d = river.from + 20 + r() * (river.to - river.from - 40), side = r() < 0.5 ? -1 : 1, off = 5.6 + r() * (side > 0 ? 5.9 : 8.4);
    const { p, r: rr } = frame(d), x = p.x + rr.x * off * side, z = p.z + rr.z * off * side;
    if (Math.hypot(x - avoid.x, z - avoid.z) < 3.4) continue;   /* 정자 자리 */
    const g = groundAt(x, z);
    if (g.dist < 5.3) continue;
    const reed = r() < 0.38, w = reed ? 1.3 + r() * 0.6 : 1.1 + r() * 0.5, h = reed ? 1.4 + r() * 0.6 : 0.8 + r() * 0.5;
    m.compose(new THREE.Vector3(x, g.y - 0.05, z), q.setFromAxisAngle(Y, r() * Math.PI), new THREE.Vector3(w, h, w));
    spots[reed ? 'reed' : 'cosmos'].push(m.clone());
  }
  const mat = (map) => new THREE.MeshLambertMaterial({ map, alphaTest: 0.4, side: THREE.DoubleSide });
  const inst = (list, material) => { const o = new THREE.InstancedMesh(geo, material, list.length); list.forEach((mm, i) => o.setMatrixAt(i, mm)); o.receiveShadow = true; o.computeBoundingSphere(); return o; };
  const cosmos = inst(spots.cosmos, mat(meadowTexture('cosmos', 3))), reed = inst(spots.reed, mat(meadowTexture('reed', 5)));
  const grass = inst(spots.cosmos.concat(spots.reed), mat(meadowTexture('grass', 7)));
  const group = new THREE.Group(); group.add(cosmos, reed, grass);
  let now = null;
  return {
    group, cosmos, reed, grass,
    set(season) {
      if (season === now) return; now = season;
      const autumn = season === 'autumn', green = season === 'spring' || season === 'summer';
      cosmos.visible = reed.visible = autumn; grass.visible = green;
    },
  };
}

/* ---------- 먼 산: 겹겹 능선 ---------- */
/* 3D 땅(±200m)과 사진 띠(3200m) 사이를 메운다. 경로 전체의 볼록 껍질을 D 만큼 밖으로 민 둘레에 능선 띠를 세운다
   (경로를 그대로 밀면 굽이마다 띠가 꼬인다). 겹마다 거리·높이·색이 다르고 안개가 먼 겹일수록 더 흐리게 한다.
   [D 거리, 높이 최소·최대(경로 높이 기준), 색, 씨앗, 안개 상한]. 안개 상한: 1700m 겹은 안개 끝(1300m) 밖이라
   그대로 두면 안개색 한 덩어리로 사라진다. 겹마다 안개 비율 상한을 두어 먼 겹도 윤곽이 남게 한다 */
/* [거리, 최저, 최고 높이, 색, 씨앗, 안개 상한, 기슭 안개 가중치]. 가까운 겹일수록 안개를 덜 먹여야 뒤의 사진 산보다 진하게 보인다(원근) */
const RIDGES = [[600, 70, 150, '#2c4632', 1, 0.42, 0.08], [1100, 110, 210, '#3a5068', 2, 0.62, 0.18], [1700, 150, 260, '#536a86', 3, 0.78, 0.3]];
/* 능선 재질: 안개 비율에 상한(k)을 두고, 기슭(haze=1)으로 갈수록 안개를 최대 0.3 더 얹어 골짜기에 낀 안개처럼 보이게 한다 */
function ridgeMaterial(color, k, hz = 0.3) {
  const mat = new THREE.MeshLambertMaterial({ color, side: THREE.DoubleSide });
  mat.onBeforeCompile = (sh) => {
    sh.vertexShader = sh.vertexShader.replace('#include <common>', '#include <common>\nattribute float haze;\nvarying float vHaze;').replace('#include <begin_vertex>', '#include <begin_vertex>\nvHaze = haze;');
    sh.fragmentShader = sh.fragmentShader.replace('#include <common>', '#include <common>\nvarying float vHaze;')
      .replace('#include <fog_fragment>', `#ifdef USE_FOG\nfloat fogFactor = smoothstep( fogNear, fogFar, vFogDepth );\ngl_FragColor.rgb = mix( gl_FragColor.rgb, fogColor, min( 1.0, fogFactor * ${k.toFixed(2)} + vHaze * ${hz.toFixed(2)} ) );\n#endif`);
  };
  mat.customProgramCacheKey = () => 'ridge' + k + '/' + hz;
  return mat;
}
function makeRidges(curve, length, lowY) {
  const pts = curve.getSpacedPoints(Math.ceil(length / 20)).map((p) => ({ x: p.x, z: p.z, y: p.y }));
  /* 볼록 껍질(단조 사슬) */
  const sorted = pts.slice().sort((a, b) => a.x - b.x || a.z - b.z), cross = (o, a, b) => (a.x - o.x) * (b.z - o.z) - (a.z - o.z) * (b.x - o.x);
  const half = (list) => { const h = []; list.forEach((p) => { while (h.length >= 2 && cross(h[h.length - 2], h[h.length - 1], p) <= 0) h.pop(); h.push(p); }); h.pop(); return h; };
  const hull = half(sorted).concat(half(sorted.slice().reverse()));
  const group = new THREE.Group();
  RIDGES.forEach(([D, lo, hi, color, seed, fogK, hz]) => {
    /* 방향마다 껍질에서 가장 멀리 나간 꼭짓점 + D. 각도 순으로 이으면 모서리는 원호, 변은 직선인 민 둘레가 된다 */
    const raw = [];
    for (let i = 0; i < 1440; i++) {
      const a = (i / 1440) * Math.PI * 2, ux = Math.cos(a), uz = Math.sin(a);
      let best = hull[0], bv = -Infinity; hull.forEach((h) => { const v = h.x * ux + h.z * uz; if (v > bv) { bv = v; best = h; } });
      raw.push({ x: best.x + ux * D, z: best.z + uz * D, ux, uz });
    }
    /* GAP(20m) 간격으로 고르게 다시 찍는다 */
    const GAP = 20, ring = []; let acc = 0;
    for (let i = 0; i < raw.length; i++) {
      const a = raw[i], b = raw[(i + 1) % raw.length], seg = Math.hypot(b.x - a.x, b.z - a.z);
      for (let t = (GAP - acc) % GAP; t < seg; t += GAP) { const f = seg ? t / seg : 0; ring.push({ x: a.x + (b.x - a.x) * f, z: a.z + (b.z - a.z) * f, ux: a.ux, uz: a.uz }); }
      acc = (acc + seg) % GAP;
    }
    const n = ring.length, ph = rng(seed * 101), p1 = ph() * 6.28, p2 = ph() * 6.28, p3 = ph() * 6.28, p4 = ph() * 6.28;
    /* 기준 높이: 가장 가까운 경로 점 높이를 앞뒤 ±600m 로 고르게 편다 */
    const near = ring.map((q) => { let bd = Infinity, y = 0; pts.forEach((p) => { const dd = (p.x - q.x) ** 2 + (p.z - q.z) ** 2; if (dd < bd) { bd = dd; y = p.y; } }); return y; });
    const W = 30, yRef = near.map((_, i) => { let sum = 0; for (let k = -W; k <= W; k++) sum += near[(i + k + n) % n]; return sum / (2 * W + 1); });
    const pos = [], idx = [], haze = [], ridged = (v) => 1 - Math.abs(Math.sin(v));
    ring.forEach((q, i) => {
      const sArc = i * GAP;
      /* 평면 굴곡과 높이: 사인 합성. 1-|sin| (뾰족한 봉우리)을 크기별로 겹쳐 모래 언덕처럼 매끈하지 않게 한다 */
      const wob = 110 * Math.sin(sArc / 900 + p1) + 50 * Math.sin(sArc / 310 + p2);
      const k = 0.42 + 0.24 * Math.sin(sArc / 700 + p3) + 0.3 * (ridged(sArc / 260 + p4) - 0.5) + 0.22 * (ridged(sArc / 97 + p2) - 0.5) + 0.12 * (ridged(sArc / 41 + p1) - 0.5);
      const H = lo + (hi - lo) * Math.min(1, Math.max(0, k)), x = q.x + q.ux * wob, z = q.z + q.uz * wob, top = yRef[i] + H;
      /* 단면: 안쪽 기슭(바닥) → 안쪽 어깨 → 능선 → 바깥 기슭. haze: 골짜기 안개 비율 */
      [[-1.9, lowY - 20, 1], [-0.6, yRef[i] + H * 0.55, 0.45], [0, top, 0], [1.3, lowY - 20, 1]].forEach(([o, y, h]) => { pos.push(x + q.ux * o * H, y, z + q.uz * o * H); haze.push(h); });
    });
    for (let i = 0; i < n; i++) { const a = i * 4, b = ((i + 1) % n) * 4; for (let k = 0; k < 3; k++) idx.push(a + k, b + k, a + k + 1, a + k + 1, b + k, b + k + 1); }
    const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setAttribute('haze', new THREE.Float32BufferAttribute(haze, 1));
    g.setIndex(idx); g.computeVertexNormals();
    group.add(new THREE.Mesh(g, ridgeMaterial(color, fogK, hz)));
  });
  return group;
}

/* ---------- 하늘: 풍경 사진 띠 + 그라데이션 + 해 ---------- */
/* 사진 띠마다 아래에서부터 쓰는 높이 비율. 0번(맑은 산) 사진은 위 가운데에 나뭇가지가 찍혀 있어(위에서 36%까지) 위 40%를 버린다 */
const PHOTO_KEEP = [0.6, 1, 1, 1, 1, 1];
/* 사진 띠 윗부분을 투명하게 녹이는 알파 무늬: 위 30%에서 0 → 1. 녹은 자리는 뒤의 하늘 돔이 채운다 */
let fadeTex = null;
function topFade() {
  if (fadeTex) return fadeTex;
  const c = document.createElement('canvas'); c.width = 4; c.height = 256;
  const g = c.getContext('2d'), grad = g.createLinearGradient(0, 0, 0, 256);
  grad.addColorStop(0, '#000'); grad.addColorStop(0.08, '#000'); grad.addColorStop(0.18, '#555'); grad.addColorStop(0.3, '#fff'); grad.addColorStop(1, '#fff');
  g.fillStyle = grad; g.fillRect(0, 0, 4, 256);
  return (fadeTex = new THREE.CanvasTexture(c));
}
function backdrop(tex, w, h, keep = 1) {
  const R = 3200; tex.wrapS = THREE.RepeatWrapping; tex.repeat.set(3, keep);
  const height = (2 * Math.PI * R / 3) * h * keep / w, geo = new THREE.CylinderGeometry(R, R, height, 96, 1, true);
  geo.translate(0, height / 2 - 60, 0);
  const mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ map: tex, alphaMap: topFade(), side: THREE.BackSide, fog: false, depthWrite: false, transparent: true }));
  mesh.renderOrder = -1; return mesh;
}
function hazeVeil() {
  const c = document.createElement('canvas'); c.width = 4; c.height = 256;
  const g = c.getContext('2d'), grad = g.createLinearGradient(0, 0, 0, 256);
  grad.addColorStop(0, '#000'); grad.addColorStop(0.45, '#000'); grad.addColorStop(0.75, '#333'); grad.addColorStop(1, '#777');
  g.fillStyle = grad; g.fillRect(0, 0, 4, 256);
  const R = 3100, height = 900, geo = new THREE.CylinderGeometry(R, R, height, 96, 1, true); geo.translate(0, height / 2 - 80, 0);
  const mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ color: '#c6d4e2', alphaMap: new THREE.CanvasTexture(c), side: THREE.BackSide, fog: false, depthWrite: false, transparent: true }));
  mesh.renderOrder = -0.5; return mesh;
}
function skyDome() {
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: { top: { value: new THREE.Color('#b3c5e5') }, zenith: { value: new THREE.Color('#3f7cc8') } },
    vertexShader: 'varying vec3 v; void main(){ v = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    /* 사진 띠와 같은 톤 매핑·색 공간을 거쳐야 프리셋의 top 색을 사진 윗부분 색과 같은 값으로 맞출 수 있다 */
    fragmentShader: 'uniform vec3 top; uniform vec3 zenith; varying vec3 v; void main(){ float h = clamp((v.y - 0.3) / 0.7, 0.0, 1.0); gl_FragColor = vec4(mix(top, zenith, pow(h, 0.7)), 1.0);\n#include <tonemapping_fragment>\n#include <colorspace_fragment>\n}',
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
/* 빛 번짐 무늬: 가운데에서 가장자리로 사라지는 둥근 점. stops = [[반지름 비율, 불투명도], ...]. 햇무리·렌즈 플레어·가로등 불빛에 쓴다 */
function glowTexture(stops) {
  const c = document.createElement('canvas'); c.width = c.height = 128;
  const g = c.getContext('2d'), grad = g.createRadialGradient(64, 64, 0, 64, 64, 64);
  stops.forEach(([at, a]) => grad.addColorStop(at, `rgba(255,255,255,${a})`));
  g.fillStyle = grad; g.fillRect(0, 0, 128, 128);
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; return t;
}
/* 햇무리: 해 둘레의 큰 반투명 광륜. 크기·색은 env.js 가 프리셋(halo)과 해 색으로 정한다 */
function sunHalo() {
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTexture([[0, 0.55], [0.2, 0.28], [0.5, 0.08], [1, 0]]), fog: false, depthWrite: false, transparent: true, opacity: 0.55, blending: THREE.AdditiveBlending, toneMapped: false }));
  sp.scale.set(2600, 2600, 1); return sp;
}
/* 렌즈 플레어: 해가 시야에 들어오면 해 → 화면 가운데 → 반대편 선 위에 작은 원 셋. [선 위 위치(해=0, 가운데=1), 크기(화면 높이 비율), 무늬, 색] */
function lensFlare() {
  const dot = glowTexture([[0, 0.7], [0.45, 0.25], [1, 0]]), ring = glowTexture([[0, 0], [0.6, 0.04], [0.82, 0.4], [0.92, 0.12], [1, 0]]);
  const group = new THREE.Group();
  [[1.35, 0.05, dot, '#ffe2b0'], [1.75, 0.09, ring, '#b9d2ff'], [2.2, 0.16, ring, '#ffc89a']].forEach(([t, size, map, color]) => {
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map, color, fog: false, depthTest: false, depthWrite: false, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, toneMapped: false }));
    sp.userData = { t, size, base: new THREE.Color(color) }; sp.renderOrder = 10; sp.frustumCulled = false; group.add(sp);
  });
  group.visible = false; return group;
}

/* 구름: 맑은 날 사진 띠 위(고도 21~33도) 하늘에 큰 반투명 구름 5장. 카메라를 따라다니며 천천히 돈다(env.js) */
function makeClouds() {
  const r = rng(83), group = new THREE.Group();
  for (let i = 0; i < 5; i++) {
    const c = document.createElement('canvas'); c.width = 512; c.height = 192;
    const g = c.getContext('2d');
    /* 부푼 뭉게: 흰 원 무늬를 겹친다. 아래쪽은 조금 눌러 평평하게 */
    for (let k = 0; k < 22; k++) {
      const x = 70 + r() * 372, y = 70 + r() * 60 - Math.sin(((x - 70) / 372) * Math.PI) * 30, rad = 30 + r() * 46;
      const grad = g.createRadialGradient(x, y, 0, x, y, rad); grad.addColorStop(0, 'rgba(255,255,255,0.55)'); grad.addColorStop(0.6, 'rgba(250,251,253,0.3)'); grad.addColorStop(1, 'rgba(245,247,250,0)');
      g.fillStyle = grad; g.fillRect(x - rad, y - rad, rad * 2, rad * 2);
    }
    const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace;
    const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: t, fog: false, depthWrite: false, transparent: true, opacity: 0.85 }));
    const a = (i / 5) * Math.PI * 2 + r() * 0.8, el = (21 + r() * 12) * Math.PI / 180, R = 2600, w = 900 + r() * 500;
    sp.position.set(Math.cos(a) * R * Math.cos(el), R * Math.sin(el), Math.sin(a) * R * Math.cos(el)); sp.scale.set(w, w * 0.375, 1);
    group.add(sp);
  }
  return group;
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
  const base = new THREE.Mesh(new THREE.PlaneGeometry(12000, 12000), new THREE.MeshLambertMaterial({ map: baseTex, color: GRASS.park[0] }));
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
  /* 주차된 차: 차체, 어두운 유리 칸, 차체 색 지붕, 바퀴 4개, 차 밑 그림자 */
  const body = new THREE.BoxGeometry(1.85, 0.75, 4.6); body.translate(0, 0.62, 0);
  const cabin = new THREE.BoxGeometry(1.62, 0.52, 2.5); cabin.translate(0, 1.26, -0.2);
  const roof = new THREE.BoxGeometry(1.5, 0.08, 2.1); roof.translate(0, 1.56, -0.25);
  const wheel = new THREE.CylinderGeometry(0.34, 0.34, 0.24, 16); wheel.rotateZ(Math.PI / 2);
  const bodies = new THREE.InstancedMesh(body, new THREE.MeshLambertMaterial(), spots.length);
  const cabins = new THREE.InstancedMesh(cabin, new THREE.MeshPhongMaterial({ color: '#14171b', specular: '#8a929c', shininess: 90 }), spots.length);
  const roofs = new THREE.InstancedMesh(roof, new THREE.MeshLambertMaterial(), spots.length);
  const wheels = new THREE.InstancedMesh(wheel, new THREE.MeshLambertMaterial({ color: '#18191b' }), spots.length * 4);
  /* 차 밑 그림자: 가장자리가 흐린 검은 판. 해 그림자가 닿지 않는 먼 차에도 바닥에 붙어 있는 느낌을 준다 */
  const bc = document.createElement('canvas'); bc.width = 64; bc.height = 128;
  const bg = bc.getContext('2d'); bg.filter = 'blur(10px)'; bg.fillStyle = '#000'; bg.fillRect(14, 16, 36, 96);
  const blobTex = new THREE.CanvasTexture(bc);
  const blobGeo = new THREE.PlaneGeometry(2.4, 5.2); blobGeo.rotateX(-Math.PI / 2); blobGeo.translate(0, 0.02, 0);
  const blobs = new THREE.InstancedMesh(blobGeo, new THREE.MeshBasicMaterial({ color: '#000000', alphaMap: blobTex, transparent: true, opacity: 0.75, depthWrite: false }), spots.length);
  const paints = ['#f2f2f0', '#1d1f22', '#9aa0a6', '#c8ccd0', '#2c4a74', '#7a1f24', '#e7e3da'], wm = new THREE.Matrix4();
  spots.forEach(([x, z, r], i) => {
    m.compose(new THREE.Vector3(x, 0, z), new THREE.Quaternion().setFromAxisAngle(up, r), new THREE.Vector3(1, 1, 1));
    bodies.setMatrixAt(i, m); cabins.setMatrixAt(i, m); roofs.setMatrixAt(i, m); blobs.setMatrixAt(i, m);
    col.set(paints[Math.floor(rand() * paints.length)]); bodies.setColorAt(i, col); roofs.setColorAt(i, col);
    [[-0.86, 1.45], [0.86, 1.45], [-0.86, -1.45], [0.86, -1.45]].forEach(([wx, wz], j) => wheels.setMatrixAt(i * 4 + j, wm.copy(m).multiply(new THREE.Matrix4().makeTranslation(wx, 0.34, wz))));
  });
  bodies.castShadow = cabins.castShadow = roofs.castShadow = true; scene.add(bodies, cabins, roofs, wheels, blobs);
  /* 주차장 건물: 창 격자 외벽. 상자 투영으로 15m 마다 무늬 한 장, 세로는 한 층 3.5m 쯤 되게 줄인다 */
  const bfacade = assets.mats.facade.clone(); bfacade.repeat.set(1, 0.4); bfacade.needsUpdate = true;
  const building = new THREE.Mesh(boxUV(new THREE.BoxGeometry(70, 14, 16), 1 / 15), new THREE.MeshLambertMaterial({ map: bfacade, color: '#eeeae4' }));
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
      trees.push({ x, z: zz, y: g.y, h: 7 + r() * 6, kind: Math.floor(r() * 5), seed: r(), d });
    }
  });
  /* 오른쪽 가로등 자리(경로 거리 90 + 40k, 6.8m). 가로수를 심을 때 기둥과 겹치지 않게 비켜 간다 */
  const nearPole = (d) => { const k = (((d - 90) % 40) + 40) % 40; return k < 2.5 || k > 37.5; };
  /* 길가에 줄지어 심는다: from~to 를 gap 간격으로, 양쪽 off m. 굽은 길 안쪽에서 다른 지점 도로에 더 가까운 자리는 뺀다 */
  const row = (from, to, gap, off, seed, make) => {
    const rr = rng(seed);
    for (let d = from; d <= to; d += gap) for (const side of [-1, 1]) {
      const dd = d + (rr() - 0.5) * gap * 0.15;
      if (side > 0 && nearPole(dd)) continue;
      const { p, r } = frame(dd), x = p.x + r.x * off * side, z = p.z + r.z * off * side;
      if (nearest(x, z).dist < off - 0.5) continue;
      trees.push({ x, z, y: groundAt(x, z).y, h: 10, seed: rr(), d: dd, ...make(rr, side, dd) });
    }
  };
  /* 단풍 터널(숲길 안 400m): 양쪽 6.5m 에 느티나무를 7m 간격으로. "도로 12m 이내 금지" 의 유일한 예외다.
     느티나무 모델은 줄기 1.8m 에서 갈라지고 잎 뭉치가 모델 높이 3.5m 부터, 폭 약 8m(반폭 4m).
     배율 1.45~1.6 이면 잎 아래가 5.1m 이상, 반폭이 5.8m 이상이다. 길 쪽으로 0.1 라디안(약 6도) 기울이면
     수관이 0.8m 쯤 더 길 쪽으로 나와 가운데서 만나고, 길 쪽 잎 끝은 0.6m 내려가도 노면 위 4.4m 이상이다 */
  const forest = zones.find((z) => z.mood === 'forest'), city = zones.find((z) => z.mood === 'city');
  const tunnel = [forest.from + 380, forest.from + 780];
  row(tunnel[0], tunnel[1], 7, 6.5, 31, (rr, side, d) => ({ kind: 0, s: 1.45 + rr() * 0.15, lean: -0.1 * side, axis: curve.getTangentAt(d / length) }));
  /* 메타세쿼이아 가로수길(도심 끝 450m ~ 강변 첫 50m, 담양 가로수길 느낌): 양쪽 7.5m 에 9m 간격으로 일렬. 높이 약 15m */
  const avenue = [city.to - 450, city.to + 50];
  row(avenue[0], avenue[1], 9, 7.5, 37, (rr) => ({ kind: 4, s: 1.3 + rr() * 0.12 }));
  /* 강 건너 둔치(강변 구간 오른쪽): 강은 오른쪽 12~700m 를 덮는다. 구간 가운데 700m 동안 건너편 기슭이 230m 까지 나와
     반도처럼 강을 좁히고, 그 위에 낮은 집 몇 채와 나무를 둔다(두물머리 건너 마을 느낌) */
  const river = zones.find((z) => z.mood === 'river'), bank = [river.from + 250, river.from + 950];
  const bankEdge = (d) => 700 - 470 * Math.pow(Math.sin(Math.PI * Math.min(1, Math.max(0, (d - bank[0]) / (bank[1] - bank[0])))), 0.6);
  const bankY = (d, o) => { const { p } = frame(d), e = bankEdge(d); return p.y - 4 + (o < e ? -3 + 4.2 * (o - (e - 25)) / 25 : 1.2 + 14 * smooth((o - e) / 300) + Math.sin(d * 0.02 + o * 0.03) * 1.5 * smooth((o - e) / 40)); };
  const bankAt = (d, o) => { const { p, r } = frame(d); return new THREE.Vector3(p.x + r.x * o, bankY(d, o), p.z + r.z * o); };
  const rk = rng(53);
  for (let i = 0; i < 40; i++) {
    const d = bank[0] + 150 + rk() * (bank[1] - bank[0] - 300), o = bankEdge(d) + 15 + Math.pow(rk(), 0.7) * 260, v = bankAt(d, o);
    trees.push({ x: v.x, z: v.z, y: v.y, h: 8 + rk() * 5, kind: [0, 0, 1, 3, 4][Math.floor(rk() * 5)], seed: rk(), d });
  }
  const treeGroups = plantTrees(scene, assets.trees, trees, assets.mats);
  recolor(treeGroups, 'autumn');
  /* 도심 건물 */
  const blocks = [], rb = rng(99);
  for (let i = 0; i < 420; i++) {
    const d = city.from + rb() * (city.to - city.from), side = rb() < 0.5 ? -1 : 1;
    if (side > 0 && rb() < 0.45) continue;
    const off = 30 + rb() * 140, { p, r } = frame(d), x = p.x + r.x * off * side, z = p.z + r.z * off * side;
    const g = groundAt(x, z);
    if (g.dist < 22) continue;
    blocks.push({ x, z, y: g.y, h: 12 + Math.pow(rb(), 2) * 70, w: 12 + rb() * 18, dd: 12 + rb() * 16, c: ['#ffffff', '#f2ece2', '#e4e9ef', '#efe8dc', '#dfe3e7'][Math.floor(rb() * 5)], d, i: blocks.length });
  }
  const box = new THREE.BoxGeometry(1, 1, 1); box.translate(0, 0.5, 0);
  const facade = assets.mats.facade.clone(); facade.repeat.set(2, 3); facade.needsUpdate = true;
  /* 건물도 경로 거리 3마디(약 430m)로 나눠 마디마다 경계를 맞춘다(나무와 같은 까닭) */
  const houseMat = new THREE.MeshLambertMaterial({ map: facade }), span = (city.to - city.from) / 3, houseChunks = [];
  for (let c = 0; c < 3; c++) {
    const part = blocks.filter((b) => Math.min(2, Math.floor((b.d - city.from) / span)) === c);
    if (!part.length) continue;
    const mesh = new THREE.InstancedMesh(box, houseMat, part.length); mesh.castShadow = true;
    part.forEach((b, i) => { m.compose(new THREE.Vector3(b.x, b.y - 0.5, b.z), new THREE.Quaternion().setFromAxisAngle(up, (b.i * 0.7) % 1.5), new THREE.Vector3(b.w, b.h, b.dd)); mesh.setMatrixAt(i, m); mesh.setColorAt(i, col.set(b.c)); });
    mesh.computeBoundingSphere(); mesh.computeBoundingBox(); scene.add(mesh);
    houseChunks.push({ mesh, box: mesh.boundingBox });
  }
  /* 강: 강변 구간 오른쪽. 해 쪽으로 반짝이게 반사광이 센 재질을 쓰고, 물결 대신 꼭짓점 노멀을 조금씩 흔든다.
     반사광이 고르게 갈라지도록 가로로도 여러 칸으로 나눈다 */
  const wcols = [12, 40, 90, 160, 260, 400, 550, 700], nc = wcols.length, wpos = [], widx = [], rw = rng(7);
  let wrows = 0;
  /* 물 폭(700m)이 굽이에서 다른 지점 도로 위로 접혀 덮인다(도심 끝 2.1~2.6km 와 강변 첫 굽이에서 물이 노면보다 최대 6m 위).
     다른 지점(경로 거리 60m 넘게 떨어진) 도로에서 230m(땅 띠 200m + 여유) 안에 들어가는 꼭짓점은
     그 줄에서 안전한 가장 먼 거리까지 도로 쪽으로 당긴다(이분 탐색) */
  const wS = curve.getSpacedPoints(Math.ceil(length / 8)), wSd = wS.map((_, i) => (i * length) / (wS.length - 1));
  const overOther = (x, z, d) => { for (let i = 0; i < wS.length; i++) if (Math.abs(wSd[i] - d) > 60 && (wS[i].x - x) ** 2 + (wS[i].z - z) ** 2 < 230 * 230) return true; return false; };
  for (let d = river.from - 100; d < river.to; d += 8, wrows++) {
    const { p, r } = frame(d), y = p.y - 4;
    let safe = 0, cut = false;
    wcols.forEach((o) => {
      if (!cut && overOther(p.x + r.x * o, p.z + r.z * o, d)) {
        cut = true; let a = safe, b = o;
        for (let k = 0; k < 8; k++) { const c = (a + b) / 2; if (overOther(p.x + r.x * c, p.z + r.z * c, d)) b = c; else a = c; }
        safe = a;
      } else if (!cut) safe = o;
      const oo = cut ? safe : o;
      wpos.push(p.x + r.x * oo, y, p.z + r.z * oo);
    });
  }
  for (let i = 0; i < wrows - 1; i++) for (let k = 0; k < nc - 1; k++) { const a = i * nc + k; widx.push(a, a + 1, a + nc, a + nc, a + 1, a + nc + 1); }
  const wg = new THREE.BufferGeometry(); wg.setAttribute('position', new THREE.Float32BufferAttribute(wpos, 3)); wg.setIndex(widx); wg.computeVertexNormals();
  /* 면의 앞쪽이 위를 보든 아래를 보든 위에서 볼 때 위로 향하게 흔든 노멀을 같은 쪽으로 맞춘다 */
  const wn = wg.attributes.normal, wsign = Math.sign(wn.getY(0)) || 1;
  for (let i = 0; i < wn.count; i++) wn.setXYZ(i, (rw() - 0.5) * 0.14 * wsign, wsign, (rw() - 0.5) * 0.14 * wsign);
  const water = new THREE.Mesh(wg, new THREE.MeshPhongMaterial({ color: '#6f8fa8', specular: '#fff4dc', shininess: 100, side: THREE.DoubleSide })); scene.add(water);
  /* 강 건너 둔치 땅: 물가(물 아래 3m)에서 올라와 760m 까지. 풀 무늬에 강변 풀색 */
  const bcols = [-25, 0, 12, 40, 90, 160, 260, 400, 560], bpos = [], bidx = [], buv = [];
  let brows = 0;
  for (let d = bank[0]; d <= bank[1]; d += 10, brows++) {
    const e = bankEdge(d);
    bcols.forEach((c) => { const o = Math.min(e + c, 760 + c * 0.01), v = bankAt(d, o); bpos.push(v.x, v.y, v.z); buv.push(o / 5, d / 5); });
  }
  for (let i = 0; i < brows - 1; i++) for (let k = 0; k < bcols.length - 1; k++) { const a = i * bcols.length + k; bidx.push(a, a + bcols.length, a + 1, a + 1, a + bcols.length, a + bcols.length + 1); }
  const bgeo = new THREE.BufferGeometry(); bgeo.setAttribute('position', new THREE.Float32BufferAttribute(bpos, 3)); bgeo.setAttribute('uv', new THREE.Float32BufferAttribute(buv, 2));
  bgeo.setIndex(bidx); bgeo.computeVertexNormals();
  const farBank = new THREE.Mesh(bgeo, new THREE.MeshLambertMaterial({ map: assets.mats.grass, color: GRASS.river[0], side: THREE.DoubleSide })); farBank.receiveShadow = true; scene.add(farBank);
  /* 강 건너 마을: 흰 벽·회색 박공지붕 낮은 집 7채. 둔치 가장 나온 곳 뒤편 */
  const village = new THREE.Group(), rv = rng(61), mid = (bank[0] + bank[1]) / 2;
  const wall = new THREE.BoxGeometry(1, 1, 1); wall.translate(0, 0.5, 0);
  /* 박공지붕: 폭·깊이 1 인 상자 위에 얹는 삼각 기둥. 처마가 벽 밖으로 8% 나온다 */
  const gable = new THREE.BufferGeometry();
  gable.setAttribute('position', new THREE.Float32BufferAttribute([-0.58, 0, -0.56, 0.58, 0, -0.56, 0, 0.42, -0.56, -0.58, 0, 0.56, 0.58, 0, 0.56, 0, 0.42, 0.56], 3));
  gable.setIndex([0, 2, 1, 3, 4, 5, 0, 3, 5, 0, 5, 2, 1, 2, 5, 1, 5, 4, 0, 1, 4, 0, 4, 3]); gable.computeVertexNormals();
  const NH = 7, walls = new THREE.InstancedMesh(wall, new THREE.MeshLambertMaterial({ color: '#f1eee6' }), NH), roofs2 = new THREE.InstancedMesh(gable, new THREE.MeshLambertMaterial({ color: '#5b6068', side: THREE.DoubleSide }), NH);
  for (let i = 0; i < NH; i++) {
    const d = mid - 110 + i * 34 + (rv() - 0.5) * 14, o = bankEdge(d) + 25 + rv() * 70, v = bankAt(d, o), w = 9 + rv() * 4, dd = 6 + rv() * 2, h = 3.2 + rv() * 0.6;
    const yaw = Math.atan2(frame(d).r.x, frame(d).r.z) + (rv() - 0.5) * 0.4;
    m.compose(v.clone().setY(v.y - 0.4), new THREE.Quaternion().setFromAxisAngle(up, yaw), new THREE.Vector3(w, h + 0.4, dd)); walls.setMatrixAt(i, m);
    m.compose(v.clone().setY(v.y + h), new THREE.Quaternion().setFromAxisAngle(up, yaw), new THREE.Vector3(w, h * 0.9, dd)); roofs2.setMatrixAt(i, m);
  }
  walls.castShadow = roofs2.castShadow = true; walls.computeBoundingSphere(); roofs2.computeBoundingSphere(); village.add(walls, roofs2); scene.add(village);
  /* 육각 정자: 강변 전망 자리(오른쪽 9.5m 둔덕, 가로등 사이). 돌 기단, 붉은 기둥 6개, 처마 끝이 들린 기와 지붕(단색) */
  const pd = mid + 20 - ((((mid + 20 - 90) % 40) + 40) % 40) + 20, pf = frame(pd), pv = new THREE.Vector3(pf.p.x + pf.r.x * 9.5, 0, pf.p.z + pf.r.z * 9.5);
  pv.y = groundAt(pv.x, pv.z).y;
  const pavilion = new THREE.Group(); pavilion.position.copy(pv); pavilion.rotation.y = Math.atan2(pf.r.x, pf.r.z);
  const plinth = new THREE.Mesh(new THREE.CylinderGeometry(2.7, 2.9, 0.6, 6), new THREE.MeshLambertMaterial({ color: '#b7b2a6' })); plinth.position.y = 0.2;
  const posts = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.13, 0.15, 2.7, 10), new THREE.MeshLambertMaterial({ color: '#8a3324' }), 6);
  for (let i = 0; i < 6; i++) { const a = (i / 6) * Math.PI * 2; m.makeTranslation(Math.cos(a) * 2.2, 1.85, Math.sin(a) * 2.2); posts.setMatrixAt(i, m); }
  /* 지붕: 꼭짓점 24개 원뿔을 육각형 둘레로 펴고, 모서리(60도마다)로 갈수록 처마를 들어 올린다 */
  const roofGeo = new THREE.ConeGeometry(1, 1, 24, 3, true), rp = roofGeo.attributes.position;
  for (let i = 0; i < rp.count; i++) {
    const x = rp.getX(i), y = rp.getY(i), z = rp.getZ(i), rad = Math.hypot(x, z);
    if (rad < 1e-4) { rp.setXYZ(i, 0, 1.75, 0); continue; }
    const a = Math.atan2(z, x), sec = ((a % (Math.PI / 3)) + Math.PI / 3) % (Math.PI / 3) - Math.PI / 6;
    const hex = Math.cos(Math.PI / 6) / Math.cos(sec), corner = Math.pow(Math.abs(sec) / (Math.PI / 6), 3), t = 0.5 - y;   /* t: 0 꼭대기 → 1 처마 */
    const R = 3.7 * hex * t * (1 + 0.08 * corner);
    rp.setXYZ(i, Math.cos(a) * R, 1.75 - 1.55 * Math.pow(t, 0.8) + 0.45 * corner * t * t, Math.sin(a) * R);
  }
  roofGeo.computeVertexNormals();
  const proof = new THREE.Mesh(roofGeo, new THREE.MeshLambertMaterial({ color: '#4a4e55', side: THREE.DoubleSide })); proof.position.y = 3.1;
  const finial = new THREE.Mesh(new THREE.SphereGeometry(0.18, 8, 6), new THREE.MeshLambertMaterial({ color: '#4a4e55' })); finial.position.y = 4.95;
  pavilion.add(plinth, posts, proof, finial); pavilion.traverse((o) => { o.castShadow = true; }); scene.add(pavilion);
  /* 강변 꽃풀: 도로 양쪽 5.6~14m(오른쪽은 물가 비탈 앞 11.5m 까지). 캔버스로 그린 알파 무늬를 두 장 교차 판에 입힌다.
     가을: 코스모스·억새, 봄·여름: 초록 풀, 겨울·눈: 숨김. env.js 가 프리셋 계절로 meadow.set 을 부른다 */
  const meadow = makeMeadow(river, frame, groundAt, pv); scene.add(meadow.group);
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
  /* 가로등 불빛 번짐: 밤에만 켠다(env.js). 점광원은 비싸서 둥근 빛 무늬 점으로 대신한다 */
  const glowGeo = new THREE.BufferGeometry(); glowGeo.setAttribute('position', new THREE.Float32BufferAttribute(poleSpots.flatMap(([x, y, z]) => [x, y + 7.9, z]), 3));
  const bulbGlow = new THREE.Points(glowGeo, new THREE.PointsMaterial({ map: glowTexture([[0, 0.9], [0.15, 0.5], [0.45, 0.12], [1, 0]]), color: '#ffd59a', size: 9, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, toneMapped: false }));
  bulbGlow.visible = false; scene.add(bulbGlow);
  /* 먼 산 겹겹 능선 */
  const ridges = makeRidges(curve, length, lowY); scene.add(ridges);
  /* 하늘과 빛 */
  const dome = skyDome(); scene.add(dome);
  const clouds = makeClouds(); scene.add(clouds);
  const backs = assets.skies.map((s, i) => backdrop(s.tex, s.w, s.h, PHOTO_KEEP[i])); backs.forEach((b) => scene.add(b));
  const veil = hazeVeil(); scene.add(veil);
  const sunDisc = sunSprite(), halo = sunHalo(), flare = lensFlare(); scene.add(halo, sunDisc, flare);
  const hemi = new THREE.HemisphereLight('#dde6f0', '#6b6a4a', 1.6); scene.add(hemi);
  const sun = new THREE.DirectionalLight('#fff4e0', 3.2);
  sun.castShadow = true; sun.shadow.mapSize.set(2048, 2048);
  Object.assign(sun.shadow.camera, { left: -90, right: 90, top: 90, bottom: -90, near: 10, far: 700 }); sun.shadow.bias = -0.0006;
  scene.add(sun, sun.target);
  scene.fog = new THREE.Fog('#c6d4e2', 250, 3000);
  /* 해 쪽 빛: env.js 가 매 프레임 안개 기본색(fog)·해 색(color)·따뜻함(warm)을 넣는다 */
  const glow = { fog: new THREE.Color('#c6d4e2'), color: new THREE.Color('#fff4e0'), warm: 0 };
  const eyeW = new THREE.Vector3(), camDir = new THREE.Vector3(), sd = new THREE.Vector3(), ndc = new THREE.Vector3(), q = new THREE.Vector3();
  /* 해 가림 검사: 차 지붕·기둥, 나무, 건물이 해를 가리면 플레어를 끈다. 광선 검사는 6 프레임에 한 번만 한다 */
  const sunRay = new THREE.Raycaster(); sunRay.far = 2500;
  const blockers = treeGroups.map((g) => g.leaf).concat(houseChunks.map((h) => h.mesh), ridges.children);
  let frameNo = 0, hidden = false;
  const solid = (h) => !(h.object.material.transparent && h.object.material.opacity < 0.5) && h.object.visible;
  /* 카메라 기준 빛 효과: 해 쪽을 볼수록 안개를 해 색으로 데우고, 해가 화면 안에 있으면 렌즈 플레어를 띄운다.
     sunDir 을 주지 않으면 해 스프라이트 위치로 구한다. 렌더 직전(scene.onBeforeRender)에 스스로 부른다 */
  function updateFlare(camera, sunDir) {
    camera.getWorldPosition(eyeW); camera.getWorldDirection(camDir);
    if (sunDir) sd.copy(sunDir).normalize(); else sd.copy(sunDisc.position).sub(eyeW).normalize();
    const facing = camDir.dot(sd);
    if (scene.fog) scene.fog.color.copy(glow.fog).lerp(glow.color, glow.warm * Math.pow(Math.max(facing, 0), 3));
    ndc.copy(eyeW).addScaledVector(sd, 1000).project(camera);
    const on = sunDisc.visible && facing > 0 && Math.abs(ndc.x) < 1.05 && Math.abs(ndc.y) < 1.05;
    if (on && frameNo++ % 6 === 0) {
      sunRay.set(eyeW, sd);
      hidden = (camera.parent ? sunRay.intersectObject(camera.parent, true).some(solid) : false) || sunRay.intersectObjects(blockers, false).length > 0;
    }
    flare.visible = on && !hidden;
    if (!flare.visible) return;
    const strength = Math.max(0, 1 - Math.hypot(ndc.x, ndc.y) / 1.5), viewH = 2 * 5 * Math.tan((camera.fov * Math.PI) / 360);
    flare.children.forEach((sp) => {
      const { t, size } = sp.userData;
      q.set(ndc.x * (1 - t), ndc.y * (1 - t), 0.5).unproject(camera).sub(eyeW).normalize();
      sp.position.copy(eyeW).addScaledVector(q, 5); sp.scale.set(size * viewH, size * viewH, 1);
      sp.material.opacity = 0.5 * strength; sp.material.color.copy(sp.userData.base).lerp(glow.color, 0.3);
    });
  }
  /* 마디 켜고 끄기: 카메라에서 SHADOW_R 안 마디만 그림자를 드리우고, WOOD_R 밖 마디는 잎만 그린다(멀면 줄기가 안 보인다).
     매 프레임 하지 않고 기준점이 MOVE(100m) 넘게 옮겨질 때만 다시 정한다. 그 사이 최대 100m 를 더 가므로 반경에 MOVE 를 더한다 */
  const SHADOW_R = 120, WOOD_R = 400, MOVE = 100, lastAt = new THREE.Vector3(Infinity, 0, 0), camW = new THREE.Vector3();
  function chunksAt(pos, force) {
    if (!force && lastAt.distanceTo(pos) < MOVE) return;
    lastAt.copy(pos);
    treeGroups.forEach((g) => {
      const dist = g.box.distanceToPoint(pos);
      g.leaf.castShadow = g.wood.castShadow = dist < SHADOW_R + MOVE; g.wood.visible = dist < WOOD_R + MOVE;
    });
    houseChunks.forEach((h) => { h.mesh.castShadow = h.box.distanceToPoint(pos) < SHADOW_R + MOVE; });
  }
  /* 경로 거리 d 로 부른다(sim.js 가 부를 수 있게). 부르지 않아도 렌더 직전에 카메라 위치로 스스로 맞춘다 */
  const updateChunks = (d) => chunksAt(frame(d).p);
  scene.onBeforeRender = (renderer, s, camera) => {
    /* 룸미러(렌더 타깃) 그리기에서는 건너뛴다. 본 화면 카메라 기준으로만 정한다 */
    if (renderer.getRenderTarget() === null) chunksAt(camera.getWorldPosition(camW));
    updateFlare(camera);
  };
  return { scene, curve, length, zones, sources, frame, treeGroups, trees, dome, backs, sunDisc, hemi, sun, water, ground, groundAt, lamps, bulbs, base, halo, flare, glow, bulbGlow, updateFlare, updateChunks, houseChunks, ridges, meadow, farBank, village, pavilion, clouds, veil };
}
