// 앞 유리 밖 3D 도로. 코스 파일의 실제 경로와 고도에서 구간마다 굽이가 가장 많은 약 1km를 떼어 그린다.
// 시간이 아니라 주행 거리(m)로 그리므로 같은 거리는 늘 같은 그림이 나온다. 나무·건물 배치도 고정 난수로 만든다.
import * as THREE from 'three';
import { mergeGeometries } from 'three/addons/utils/BufferGeometryUtils.js';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

const LANE = 1.75;          // 오른쪽 차로 가운데(도로 중심에서 m)
const EYE = 1.25;           // 운전자 눈높이(m)
const SNIPPET = 1100;       // 구간마다 떼어 쓰는 길이(m)
const STEP = 2;             // 도로 단면 간격(m)

function rng(seed) { let s = seed >>> 0; return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296); }

/* 위경도를 원점 기준 m 좌표로. 북쪽이 -z, 동쪽이 +x */
function toLocal(path, elev) {
  const [lat0, lon0] = path[0], k = Math.cos(lat0 * Math.PI / 180);
  return path.map(([lat, lon], i) => {
    const f = (i / (path.length - 1)) * (elev.length - 1), a = Math.floor(f), b = Math.min(a + 1, elev.length - 1);
    const h = elev[a] + (elev[b] - elev[a]) * (f - a);
    return new THREE.Vector3((lon - lon0) * 111320 * k, h, -(lat - lat0) * 110540);
  });
}

/* 구간 안에서 SNIPPET 길이 창 가운데 꺾임 합이 가장 큰 곳. 머리핀은 뺀다 */
function pickWindow(pts, start, end) {
  let best = [start, Math.min(end, start + 30)], score = -1;
  for (let i = start; i < end; i++) {
    let len = 0, turn = 0, sharp = 0, j = i;
    while (j + 1 <= end && len < SNIPPET) {
      const a = pts[j], b = pts[j + 1]; len += Math.hypot(b.x - a.x, b.z - a.z);
      if (j > i) {
        const p = pts[j - 1], h1 = Math.atan2(a.z - p.z, a.x - p.x), h2 = Math.atan2(b.z - a.z, b.x - a.x);
        let d = Math.abs(h2 - h1); if (d > Math.PI) d = 2 * Math.PI - d; turn += d; sharp = Math.max(sharp, d);
      }
      j++;
    }
    /* 머리핀(한 점에서 35도 넘게 꺾임)은 앞 유리로 보면 비탈만 보이므로 뺀다 */
    if (len >= SNIPPET * 0.9 && sharp < 0.6 && turn > score) { score = turn; best = [i, j]; }
  }
  return best;
}

/* 하늘과 먼 산은 풍경 사진 띠(sky/*.jpg)를 카메라를 둘러싼 원통에 붙여 보인다. top 은 사진 맨 위 하늘색, fog 는 산 밑동과 어울리는 안개색 */
const MOODS = {
  forest: { photo: 'sky/city.jpg', top: '#b3c5e5', zenith: '#3f7cc8', fog: '#c6d4e2', grass: ['#7f9c44', '#a39a48'], sun: [0.55, 0.62, -0.55], sunColor: '#fff4e0', hills: [10, 7], light: 1.0 },
  city: { photo: 'sky/city.jpg', top: '#b3c5e5', zenith: '#3f7cc8', fog: '#cbd7e4', grass: ['#86a056', '#93a062'], sun: [-0.5, 0.6, -0.6], sunColor: '#ffffff', hills: [2, 2], light: 1.05 },
  river: { photo: 'sky/river.jpg', top: '#c3dcf1', zenith: '#5d7fb0', fog: '#e6c39c', grass: ['#a3a352', '#b39a4e'], sun: [0.25, 0.1, -1], sunColor: '#ffc283', hills: [10, -1], light: 1.25 },
};
const loader = new THREE.TextureLoader();
const textures = {};
const texture = (path) => textures[path];
/* 사진은 장면을 만들기 전에 다 받아 둔다. 덜 받은 채 찍으면 하늘이 빈다 */
async function loadPhotos() {
  for (const M of Object.values(MOODS)) {
    const tx = await loader.loadAsync(new URL(M.photo, import.meta.url).href);
    tx.colorSpace = THREE.SRGBColorSpace; textures[M.photo] = tx;
  }
}

/* 풍경 사진 원통. 둘레에 사진을 세 번 두르고 밑단은 지평선 조금 아래에 둔다 */
function backdrop(mood) {
  const M = MOODS[mood], R = 3200, tex = texture(M.photo);
  tex.wrapS = THREE.RepeatWrapping; tex.repeat.set(3, 1);
  const img = { w: mood === 'city' ? 2220 : 2560, h: mood === 'forest' ? 452 : mood === 'city' ? 500 : 440 };
  const height = (2 * Math.PI * R / 3) * img.h / img.w;
  const geo = new THREE.CylinderGeometry(R, R, height, 96, 1, true);
  geo.translate(0, height / 2 - 60, 0);
  const mesh = new THREE.Mesh(geo, new THREE.MeshBasicMaterial({ map: tex, side: THREE.BackSide, fog: false, depthWrite: false }));
  mesh.renderOrder = -1;
  return mesh;
}

/* 하늘: 사진 띠 위는 띠 맨 위 색에서 천정의 파랑으로 이어지는 그라데이션 */
function skyDome(top, zenith) {
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: { top: { value: new THREE.Color(top) }, zenith: { value: new THREE.Color(zenith) } },
    vertexShader: 'varying vec3 v; void main(){ v = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: 'uniform vec3 top; uniform vec3 zenith; varying vec3 v; void main(){ float h = clamp((v.y - 0.3) / 0.7, 0.0, 1.0); gl_FragColor = vec4(mix(top, zenith, pow(h, 0.7)), 1.0); }',
  });
  const dome = new THREE.Mesh(new THREE.SphereGeometry(4200, 32, 16), mat); dome.renderOrder = -2; return dome;
}
/* 해: 밝은 원판과 넓은 빛무리. 카메라에서 늘 같은 방향·거리에 둔다 */
function sunSprite(color) {
  const c = document.createElement('canvas'); c.width = c.height = 256;
  const g = c.getContext('2d'), grad = g.createRadialGradient(128, 128, 0, 128, 128, 128);
  grad.addColorStop(0, 'rgba(255,255,250,1)'); grad.addColorStop(0.08, 'rgba(255,252,235,1)'); grad.addColorStop(0.16, 'rgba(255,240,200,0.55)');
  grad.addColorStop(0.45, 'rgba(255,220,160,0.16)'); grad.addColorStop(1, 'rgba(255,210,150,0)');
  g.fillStyle = grad; g.fillRect(0, 0, 256, 256);
  const tx = new THREE.CanvasTexture(c); tx.colorSpace = THREE.SRGBColorSpace;
  const sp = new THREE.Sprite(new THREE.SpriteMaterial({ map: tx, color, fog: false, depthWrite: false, transparent: true, toneMapped: false }));
  sp.scale.set(900, 900, 1); sp.renderOrder = -0.5; return sp;
}
/* 해 방향을 구간 도로가 향하는 쪽 기준 [앞, 오른쪽, 위] 비율로 잡는다. 강변은 앞 하늘에 노을 해가 보인다 */
const SUN_AIM = { forest: [0.55, 0.45, 0.7], city: [-0.4, 0.5, 0.75], river: [0.95, -0.25, 0.13] };
function aimSun(sky, curve, mood) {
  const a = curve.getPointAt(0), b = curve.getPointAt(0.35), fwd = new THREE.Vector3(b.x - a.x, 0, b.z - a.z).normalize();
  const right = new THREE.Vector3().crossVectors(fwd, new THREE.Vector3(0, 1, 0)).normalize(), [f, r, u] = SUN_AIM[mood];
  sky.dir.copy(fwd.multiplyScalar(f).add(right.multiplyScalar(r)).add(new THREE.Vector3(0, u, 0))).normalize();
}
function dress(scene, mood) {
  const M = MOODS[mood];
  scene.background = new THREE.Color(M.top);
  scene.fog = new THREE.Fog(M.fog, 250, 3000);
  scene.add(skyDome(M.top, M.zenith));
  scene.add(new THREE.HemisphereLight('#dde6f0', mood === 'river' ? '#a08060' : '#6b6a4a', 1.6 * M.light));
  const sun = new THREE.DirectionalLight(M.sunColor, 3.2 * M.light);
  sun.castShadow = true; sun.shadow.mapSize.set(2048, 2048);
  Object.assign(sun.shadow.camera, { left: -90, right: 90, top: 90, bottom: -90, near: 10, far: 700 });
  sun.shadow.bias = -0.0006; scene.add(sun, sun.target);
  const sky = backdrop(mood); scene.add(sky);
  const disc = sunSprite(M.sunColor); scene.add(disc);
  return { sky, sun, disc, dir: new THREE.Vector3(...M.sun).normalize() };
}

function ribbon(curve, length, cross, colorAt) {
  /* cross: [가로 위치(m), 높이 함수(dist, base) ] 목록. 도로 단면을 따라 띠 메시를 만든다 */
  const n = Math.floor(length / STEP), pos = [], col = [], idx = [], up = new THREE.Vector3(0, 1, 0), c = new THREE.Color();
  for (let i = 0; i <= n; i++) {
    const u = (i * STEP) / length, p = curve.getPointAt(u), t = curve.getTangentAt(u), r = new THREE.Vector3().crossVectors(t, up).normalize();
    cross.forEach(([off, height], k) => {
      pos.push(p.x + r.x * off, height(i * STEP, p.y, off), p.z + r.z * off);
      c.set(colorAt(i, k, off)); col.push(c.r, c.g, c.b);
    });
  }
  const w = cross.length;
  for (let i = 0; i < n; i++) for (let k = 0; k < w - 1; k++) {
    const a = i * w + k, b = a + 1, d = a + w, e = d + 1;
    idx.push(a, d, b, b, d, e);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
  g.setAttribute('color', new THREE.Float32BufferAttribute(col, 3));
  g.setIndex(idx); g.computeVertexNormals();
  const mesh = new THREE.Mesh(g, new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide })); mesh.receiveShadow = true; return mesh;
}

function stripe(curve, length, off, width, color, dash) {
  const n = Math.floor(length / 1), pos = [], idx = [], up = new THREE.Vector3(0, 1, 0);
  let q = 0;
  for (let i = 0; i < n; i++) {
    if (dash && Math.floor(i / dash) % 2) continue;
    const u0 = i / length, u1 = Math.min((i + 1) / length, 1);
    [u0, u1].forEach((u) => {
      const p = curve.getPointAt(u), t = curve.getTangentAt(u), r = new THREE.Vector3().crossVectors(t, up).normalize();
      [-width / 2, width / 2].forEach((w) => pos.push(p.x + r.x * (off + w), p.y + 0.03, p.z + r.z * (off + w)));
    });
    idx.push(q, q + 2, q + 1, q + 1, q + 2, q + 3); q += 4;
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3)); g.setIndex(idx);
  return new THREE.Mesh(g, new THREE.MeshBasicMaterial({ color, side: THREE.DoubleSide }));
}

/* 나무 모델 */
function deciduous(seed) {
  const r = rng(seed), parts = [], leaf = [];
  const trunk = new THREE.CylinderGeometry(0.16, 0.26, 3.2, 7); trunk.translate(0, 1.6, 0); parts.push(trunk);
  for (let i = 0; i < 3; i++) {
    const b = new THREE.CylinderGeometry(0.06, 0.12, 1.8, 5); b.translate(0, 0.9, 0);
    b.rotateZ(0.6 + r() * 0.3); b.rotateY(i * 2.1 + r()); b.translate(0, 2.4 + r() * 0.6, 0); parts.push(b);
  }
  const n = 7 + Math.floor(r() * 4);
  for (let i = 0; i < n; i++) {
    const s = new THREE.IcosahedronGeometry(0.9 + r() * 0.7, 1), a = r() * Math.PI * 2, d = r() * 1.4;
    s.translate(Math.cos(a) * d, 3.6 + r() * 2.2, Math.sin(a) * d); leaf.push(s);
  }
  return { wood: mergeGeometries(parts), leaf: shade(mergeGeometries(leaf), r) };
}
function conifer(seed) {
  const r = rng(seed), leaf = [];
  const trunk = new THREE.CylinderGeometry(0.12, 0.22, 2.2, 6); trunk.translate(0, 1.1, 0);
  for (let i = 0; i < 4; i++) { const c = new THREE.ConeGeometry(1.9 - i * 0.38, 2.4, 8); c.translate(0, 2.2 + i * 1.35, 0); c.rotateY(r()); leaf.push(c); }
  return { wood: trunk, leaf: shade(mergeGeometries(leaf), r) };
}
/* 잎 뭉치에 위아래 밝기 차이와 얼룩을 꼭짓점 색으로 준다. 인스턴스 색(단풍색)이 여기에 곱해진다 */
function shade(geo, r) {
  const pos = geo.attributes.position, col = [];
  let lo = Infinity, hi = -Infinity; for (let i = 0; i < pos.count; i++) { lo = Math.min(lo, pos.getY(i)); hi = Math.max(hi, pos.getY(i)); }
  for (let i = 0; i < pos.count; i++) { const k = 0.62 + 0.38 * (pos.getY(i) - lo) / (hi - lo) + (r() - 0.5) * 0.12; col.push(k, k, k); }
  geo.setAttribute('color', new THREE.Float32BufferAttribute(col, 3)); return geo;
}
let TREE_KINDS = [deciduous(1), deciduous(2), deciduous(3), conifer(4), conifer(5)];
/* 블렌더로 만든 나무(models/trees.glb, models/make-trees.py). 이름이 <종류>_<번호>_wood / _leaf 인 메시 쌍이다 */
async function loadTrees() {
  const gltf = await new GLTFLoader().loadAsync(new URL('models/trees.glb', import.meta.url).href);
  const mesh = (name) => { const o = gltf.scene.getObjectByName(name); const g = o.geometry.clone(); g.translate(-o.position.x, -o.position.y, -o.position.z); return g; };
  const kinds = ['deciduous_0', 'deciduous_1', 'deciduous_2', 'conifer_0', 'conifer_1'].map((n) => ({ wood: mesh(n + '_wood'), leaf: mesh(n + '_leaf') }));
  kinds.forEach((k) => { if (!k.leaf.attributes.color) throw new Error('잎에 꼭짓점 색이 없습니다'); });
  return kinds;
}
function plant(scene, trees, heightAt) {
  const m = new THREE.Matrix4(), col = new THREE.Color(), q = new THREE.Quaternion(), Y = new THREE.Vector3(0, 1, 0);
  TREE_KINDS.forEach((kind, k) => {
    const list = trees.filter((tr, i) => (tr.kind ?? i) % TREE_KINDS.length === k);
    if (!list.length) return;
    const leaf = new THREE.InstancedMesh(kind.leaf, new THREE.MeshLambertMaterial({ vertexColors: true, flatShading: true }), list.length);
    const wood = new THREE.InstancedMesh(kind.wood, new THREE.MeshLambertMaterial({ color: '#5a4632' }), list.length);
    leaf.castShadow = wood.castShadow = true;
    list.forEach((tr, i) => {
      const s = tr.h / 7, ground = heightAt(tr) - 0.1;
      m.compose(new THREE.Vector3(tr.x, ground, tr.z), q.setFromAxisAngle(Y, (i * 2.399) % 6.28), new THREE.Vector3(s, s, s));
      leaf.setMatrixAt(i, m); wood.setMatrixAt(i, m);
      leaf.setColorAt(i, col.set(k >= 3 ? ['#2f5a2a', '#3a6630', '#2a4f27'][i % 3] : tr.c));
    });
    scene.add(leaf, wood);
  });
}

/* 굽은 길에서는 다른 지점 기준으로 놓은 것이 도로에 붙을 수 있다. 도로 선과의 실제 거리로 걸러 낸다 */
function scatter(curve, length, count, seed, place, clear = 9) {
  const rand = rng(seed), up = new THREE.Vector3(0, 1, 0), out = [];
  const samples = curve.getSpacedPoints(Math.ceil(length / 4));
  const near = (x, z) => samples.some((s) => (s.x - x) ** 2 + (s.z - z) ** 2 < clear * clear);
  for (let i = 0; i < count; i++) {
    const u = rand(), p = curve.getPointAt(u), t = curve.getTangentAt(u), r = new THREE.Vector3().crossVectors(t, up).normalize();
    const side = rand() < 0.5 ? -1 : 1, item = place(rand, side);
    if (!item) continue;
    if (near(p.x + r.x * item.off * side, p.z + r.z * item.off * side)) continue;
    out.push({ x: p.x + r.x * item.off * side, z: p.z + r.z * item.off * side, base: p.y, side, d: u * length, ...item });
  }
  return out;
}

function build(course, si, mood) {
  const pts = toLocal(course.path, course.elevation), sec = course.sections[si];
  const [a, b] = pickWindow(pts, sec.startIndex, sec.endIndex);
  const o = pts[a].clone();
  /* 좌표 점 간격이 30m 남짓이라 꺾인 곳이 모나다. 이웃 점과 평균해 한 번 다듬는다 */
  const raw = pts.slice(Math.max(0, a - 2), b + 3).map((p) => p.clone().sub(o));
  const local = raw.slice(1, -1).map((p, i) => new THREE.Vector3((raw[i].x + 2 * p.x + raw[i + 2].x) / 4, (raw[i].y + 2 * p.y + raw[i + 2].y) / 4, (raw[i].z + 2 * p.z + raw[i + 2].z) / 4));
  const curve = new THREE.CatmullRomCurve3(local, false, 'centripetal');
  const length = curve.getLength(), M = MOODS[mood], scene = new THREE.Scene();
  const sky = dress(scene, mood);
  aimSun(sky, curve, mood);
  const rand = rng(si * 977 + 13);
  const water = mood === 'river' || mood === 'city';
  const hill = (side, off) => {
    const [l, r] = M.hills, k = off < 0 ? l : r;
    if (water && off > 0 && mood === 'river') return -6;
    if (mood === 'city' && off > 60) return -5;
    return k * Math.pow(Math.max(Math.abs(off) - 14, 0) / 45, 1.4);
  };
  /* 도로 밖 땅 높이. 나무도 같은 식으로 땅에 놓는다 */
  const heightAt = (d, base, off) => {
    if (Math.abs(off) <= 4.2) return base;
    const n = Math.sin(d * 0.013 + off) * 0.8 + Math.sin(d * 0.004 + off * 0.3) * 2.5;
    return base - 0.15 + hill(0, off) + (Math.abs(off) > 12 ? n * Math.min(1, (Math.abs(off) - 12) / 20) : 0);
  };
  const cross = [-400, -160, -70, -30, -12, -5.2, -4.2, 4.2, 5.2, 12, 30, 70, 160, 400].map((off) => [off, (d, base) => heightAt(d, base, off)]);
  scene.add(ribbon(curve, length, cross, (i, k, off) => {
    if (Math.abs(off) <= 4.2) return (i % 7) ? '#55585e' : '#505359';
    if (Math.abs(off) <= 5.2) return '#8d8a80';
    return (i + k) % 3 ? M.grass[0] : M.grass[1];
  }));
  scene.add(stripe(curve, length, -0.12, 0.12, '#f2c230'), stripe(curve, length, 0.12, 0.12, '#f2c230'));
  scene.add(stripe(curve, length, 4.0, 0.15, '#f4f4f0'), stripe(curve, length, -4.0, 0.15, '#f4f4f0'));
  if (water) {
    const lowest = Math.min(...local.map((p) => p.y));
    const plane = new THREE.Mesh(new THREE.PlaneGeometry(6000, 6000), new THREE.MeshBasicMaterial({ color: mood === 'river' ? '#8a7f8c' : '#7d98ab' }));
    plane.rotation.x = -Math.PI / 2; plane.position.y = lowest - 3.5; scene.add(plane);
  }
  /* 나무: 숲은 단풍 숲, 강변은 듬성듬성 */
  const treeCount = mood === 'forest' ? 2600 : mood === 'river' ? 500 : 260;
  const trees = scatter(curve, length, treeCount, si * 31 + 7, (r, side) => {
    if (mood === 'river' && side > 0) return null;
    const off = 11 + Math.pow(r(), 1.3) * 110; return { off, h: 7 + r() * 6, kind: Math.floor(r() * 5), c: ['#e0702a', '#d8452e', '#f2b33a', '#e8902f', '#9bbf48', '#c8562a'][Math.floor(r() * 6)] };
  });
  const m = new THREE.Matrix4(), col = new THREE.Color();
  plant(scene, trees, (tr) => heightAt(tr.d, tr.base, tr.off * tr.side));

  /* 도심: 건물과 가로등 */
  if (mood === 'city') {
    const blocks = scatter(curve, length, 420, 99, (r, side) => (side > 0 && r() < 0.45 ? null : { off: 30 + r() * 140, h: 12 + Math.pow(r(), 2) * 70, w: 12 + r() * 18, d: 12 + r() * 16, c: ['#b9bcc2', '#d6d0c4', '#9ea7b3', '#c9c2b6', '#8f969f'][Math.floor(r() * 5)] }));
    const box = new THREE.BoxGeometry(1, 1, 1); box.translate(0, 0.5, 0);
    const houses = new THREE.InstancedMesh(box, new THREE.MeshLambertMaterial(), blocks.length); houses.castShadow = true;
    blocks.forEach((bl, i) => { m.compose(new THREE.Vector3(bl.x, bl.base - 0.5, bl.z), new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), (i * 0.7) % 1.5), new THREE.Vector3(bl.w, bl.h, bl.d)); houses.setMatrixAt(i, m); houses.setColorAt(i, col.set(bl.c)); });
    scene.add(houses);
  }
  const poles = Math.floor(length / 35), pole = new THREE.BoxGeometry(0.15, 8, 0.15); pole.translate(0, 4, 0);
  const lamps = new THREE.InstancedMesh(pole, new THREE.MeshLambertMaterial({ color: '#6c7075' }), poles);
  for (let i = 0; i < poles; i++) {
    const u = (i + 0.5) / poles, p = curve.getPointAt(u), t = curve.getTangentAt(u), r = new THREE.Vector3().crossVectors(t, new THREE.Vector3(0, 1, 0)).normalize();
    m.makeTranslation(p.x + r.x * 6, p.y, p.z + r.z * 6); lamps.setMatrixAt(i, m);
  }
  scene.add(lamps);
  return { scene, curve, length, sky, lane: LANE, start: a };
}

/* 출발 주차장: 칸에서 앞으로 나와 오른쪽 통로를 지나 출구 길로 나간다. 차들은 고정 난수로 놓는다 */
function buildPark() {
  const scene = new THREE.Scene(), sky = dress(scene, 'forest'), rand = rng(4242), aim = sky.dir.set(0.5, 0.65, -0.55).normalize(), m = new THREE.Matrix4(), col = new THREE.Color();
  const lot = new THREE.Mesh(new THREE.PlaneGeometry(140, 70), new THREE.MeshLambertMaterial({ color: '#6a6d72' })); lot.receiveShadow = true;
  lot.rotation.x = -Math.PI / 2; lot.position.set(20, 0, -14); scene.add(lot);
  const grass = new THREE.Mesh(new THREE.PlaneGeometry(4000, 4000), new THREE.MeshLambertMaterial({ color: '#7f9c44' })); grass.receiveShadow = true;
  grass.rotation.x = -Math.PI / 2; grass.position.y = -0.05; scene.add(grass);
  const line = (x, z, w, d) => { const g = new THREE.Mesh(new THREE.PlaneGeometry(w, d), new THREE.MeshBasicMaterial({ color: '#e9e9e4' })); g.rotation.x = -Math.PI / 2; g.position.set(x, 0.02, z); scene.add(g); };
  for (let x = -30; x <= 56; x += 2.7) { line(x - 1.35, 0, 0.12, 5.4); line(x - 1.35, -28, 0.12, 5.4); }
  line(13, -2.7, 86, 0.12); line(13, -25.3, 86, 0.12);
  /* 주차된 차: 차체와 지붕 두 상자 */
  const spots = [];
  for (let x = -30; x <= 56; x += 2.7) {
    if (Math.abs(x) > 6 && rand() < 0.8) spots.push([x, 0, 0]);
    if (rand() < 0.75) spots.push([x, -28, Math.PI]);
  }
  const body = new THREE.BoxGeometry(1.85, 0.75, 4.6); body.translate(0, 0.62, 0);
  const cabin = new THREE.BoxGeometry(1.6, 0.6, 2.5); cabin.translate(0, 1.3, -0.2);
  const bodies = new THREE.InstancedMesh(body, new THREE.MeshLambertMaterial(), spots.length), cabins = new THREE.InstancedMesh(cabin, new THREE.MeshLambertMaterial({ color: '#23262b' }), spots.length);
  const paints = ['#f2f2f0', '#1d1f22', '#9aa0a6', '#c8ccd0', '#2c4a74', '#7a1f24', '#e7e3da'];
  spots.forEach(([x, z, r], i) => {
    m.compose(new THREE.Vector3(x, 0, z), new THREE.Quaternion().setFromAxisAngle(new THREE.Vector3(0, 1, 0), r), new THREE.Vector3(1, 1, 1));
    bodies.setMatrixAt(i, m); cabins.setMatrixAt(i, m); bodies.setColorAt(i, col.set(paints[Math.floor(rand() * paints.length)]));
  });
  bodies.castShadow = cabins.castShadow = true; scene.add(bodies, cabins);
  /* 뒤쪽 건물, 가로등, 둘레 나무 */
  const building = new THREE.Mesh(new THREE.BoxGeometry(70, 14, 16), new THREE.MeshLambertMaterial({ color: '#c9c6bf' }));
  building.position.set(10, 7, 16); building.castShadow = true; scene.add(building);
  const pole = new THREE.BoxGeometry(0.18, 7, 0.18); pole.translate(0, 3.5, 0);
  const poles = new THREE.InstancedMesh(pole, new THREE.MeshLambertMaterial({ color: '#70747a' }), 8);
  for (let i = 0; i < 8; i++) { m.makeTranslation(-26 + i * 12, 0, -14); poles.setMatrixAt(i, m); }
  scene.add(poles);
  const parkTrees = [];
  for (let i = 0; i < 160; i++) { const a = rand() * Math.PI * 2, d = 60 + rand() * 120; parkTrees.push({ x: 20 + Math.cos(a) * d, z: -14 + Math.sin(a) * d, h: 7 + rand() * 6, kind: Math.floor(rand() * 5), c: ['#e0702a', '#d8452e', '#f2b33a', '#e8902f', '#9bbf48'][Math.floor(rand() * 5)] }); }
  plant(scene, parkTrees, () => 0);

  /* 출구 길 */
  const exit = new THREE.CatmullRomCurve3([[60, -40], [60, -80], [64, -140], [74, -220], [80, -320]].map(([x, z]) => new THREE.Vector3(x, 0, z)));
  const exitLen = exit.getLength();
  scene.add(ribbon(exit, exitLen, [[-4.2, () => 0.01], [4.2, () => 0.01]], () => '#3b3d42'));
  scene.add(stripe(exit, exitLen, -0.12, 0.12, '#f2c230'), stripe(exit, exitLen, 0.12, 0.12, '#f2c230'));
  const path = [[0, 0.8], [0, -6], [1.5, -11], [6, -14], [20, -14], [44, -14], [56, -15], [60, -20], [60, -40], [60, -80], [64, -140], [74, -220]];
  const curve = new THREE.CatmullRomCurve3(path.map(([x, z]) => new THREE.Vector3(x, 0, z)), false, 'centripetal');
  return { scene, curve, length: curve.getLength(), sky, lane: 0, start: 0 };
}

export async function createRoad(canvas, course, moods) {
  await loadPhotos();
  try { TREE_KINDS = await loadTrees(); } catch (e) { console.warn('블렌더 나무를 못 읽어 코드 나무를 씁니다', e); }
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(1);
  renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.toneMappingExposure = 1.25;
  renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.setSize(canvas.width, canvas.height, false);
  const camera = new THREE.PerspectiveCamera(50, canvas.width / canvas.height, 0.5, 9000);
  const worlds = [buildPark(), ...moods.map((mood, i) => build(course, i, mood))];
  const up = new THREE.Vector3(0, 1, 0);
  return {
    lengths: worlds.map((w) => w.length),
    /* 각 장면이 코스 경로의 몇 번째 점에서 시작하는지. 내비 칸이 같은 자리를 따라간다 */
    starts: worlds.map((w) => w.start),
    /* si: 0 주차장, 1~ 코스 구간 */
    draw(si, dist) {
      const w = worlds[si], d = Math.min(Math.max(dist, 0), w.length - 40);
      const at = (m) => { const u = Math.min(m, w.length) / w.length, p = w.curve.getPointAt(u), r = new THREE.Vector3().crossVectors(w.curve.getTangentAt(u), up).normalize(); return new THREE.Vector3(p.x + r.x * w.lane, p.y, p.z + r.z * w.lane); };
      const p = at(d);
      /* 운전자는 한 점이 아니라 앞길을 넓게 본다. 앞 8~32m 점을 평균해 급커브에서 시선이 튀지 않게 한다 */
      const look = [8, 14, 20, 26, 32].map((m) => at(d + m)).reduce((s, q) => s.add(q), new THREE.Vector3()).multiplyScalar(1 / 5);
      camera.position.set(p.x, p.y + EYE, p.z);
      camera.lookAt(look.x, look.y + EYE * 0.85, look.z);
      const { sky, sun, disc, dir } = w.sky;
      sky.position.set(p.x, p.y, p.z);
      disc.position.set(p.x + dir.x * 2600, p.y + dir.y * 2600, p.z + dir.z * 2600);
      /* 그림자 상자를 차 주변에 맞춘다 */
      sun.target.position.set(p.x, p.y, p.z); sun.position.set(p.x + dir.x * 300, p.y + dir.y * 300, p.z + dir.z * 300);
      renderer.render(w.scene, camera);
    },
  };
}
