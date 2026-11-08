// 앞 유리 밖 3D 도로. 코스 파일의 실제 경로와 고도에서 구간마다 굽이가 가장 많은 약 1km를 떼어 그린다.
// 시간이 아니라 주행 거리(m)로 그리므로 같은 거리는 늘 같은 그림이 나온다. 나무·건물 배치도 고정 난수로 만든다.
import * as THREE from 'three';

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

const MOODS = {
  forest: { sky: ['#5b8fcc', '#cfe2f1'], fog: '#c9dbe9', grass: ['#6b7d3a', '#8a7a3c'], sun: [0.4, 0.8, -0.3], sunColor: '#fff3df', hills: [10, 7] },
  city: { sky: ['#6b9bd0', '#dbe7f1'], fog: '#d3dee8', grass: ['#7c8a5a', '#8b9168'], sun: [0.5, 0.7, 0.2], sunColor: '#ffffff', hills: [2, 2] },
  river: { sky: ['#2f3f6e', '#f5a15b'], fog: '#e8a476', grass: ['#5d6a3a', '#6f6a3e'], sun: [-0.2, 0.12, -1], sunColor: '#ffb070', hills: [10, -1] },
};

function skyDome(top, bottom, sunDir, sunColor) {
  const geo = new THREE.SphereGeometry(4000, 32, 16);
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: { top: { value: new THREE.Color(top) }, bottom: { value: new THREE.Color(bottom) }, sun: { value: new THREE.Vector3(...sunDir).normalize() }, sunColor: { value: new THREE.Color(sunColor) } },
    vertexShader: 'varying vec3 v; void main(){ v = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position,1.0); }',
    fragmentShader: 'uniform vec3 top; uniform vec3 bottom; uniform vec3 sun; uniform vec3 sunColor; varying vec3 v; void main(){ float h = clamp(v.y*1.6, 0.0, 1.0); vec3 c = mix(bottom, top, pow(h, 0.6)); float s = max(dot(v, sun), 0.0); c += sunColor * (pow(s, 600.0) * 1.2 + pow(s, 12.0) * 0.25); gl_FragColor = vec4(c, 1.0); }',
  });
  return new THREE.Mesh(geo, mat);
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
  return new THREE.Mesh(g, new THREE.MeshLambertMaterial({ vertexColors: true, side: THREE.DoubleSide }));
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

function scatter(curve, length, count, seed, place) {
  const rand = rng(seed), up = new THREE.Vector3(0, 1, 0), out = [];
  for (let i = 0; i < count; i++) {
    const u = rand(), p = curve.getPointAt(u), t = curve.getTangentAt(u), r = new THREE.Vector3().crossVectors(t, up).normalize();
    const side = rand() < 0.5 ? -1 : 1, item = place(rand, side);
    if (!item) continue;
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
  scene.background = new THREE.Color(M.fog);
  scene.fog = new THREE.FogExp2(M.fog, mood === 'river' ? 0.0011 : 0.0016);
  scene.add(skyDome(M.sky[0], M.sky[1], M.sun, M.sunColor));
  scene.add(new THREE.HemisphereLight(M.sky[1], '#4a4a3a', 1.1));
  const sun = new THREE.DirectionalLight(M.sunColor, 1.4); sun.position.set(...M.sun).multiplyScalar(100); scene.add(sun);
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
    if (Math.abs(off) <= 4.2) return (i % 7) ? '#3b3d42' : '#383a3f';
    if (Math.abs(off) <= 5.2) return '#8d8a80';
    return (i + k) % 3 ? M.grass[0] : M.grass[1];
  }));
  scene.add(stripe(curve, length, -0.12, 0.12, '#f2c230'), stripe(curve, length, 0.12, 0.12, '#f2c230'));
  scene.add(stripe(curve, length, 4.0, 0.15, '#f4f4f0'), stripe(curve, length, -4.0, 0.15, '#f4f4f0'));
  if (water) {
    const lowest = Math.min(...local.map((p) => p.y));
    const plane = new THREE.Mesh(new THREE.PlaneGeometry(6000, 6000), new THREE.MeshBasicMaterial({ color: mood === 'river' ? '#d9926a' : '#7d98ab' }));
    plane.rotation.x = -Math.PI / 2; plane.position.y = lowest - 3.5; scene.add(plane);
  }
  /* 먼 산 */
  const ring = [], ringIdx = [], R = 2600, seg = 180;
  for (let i = 0; i <= seg; i++) {
    const th = (i / seg) * Math.PI * 2, h = 220 + 180 * Math.sin(th * 5 + si) + 120 * Math.sin(th * 13 + si * 2) + 60 * Math.sin(th * 31);
    ring.push(Math.cos(th) * R, -30, Math.sin(th) * R, Math.cos(th) * R, Math.max(h, 60), Math.sin(th) * R);
    if (i < seg) { const q = i * 2; ringIdx.push(q, q + 2, q + 1, q + 1, q + 2, q + 3); }
  }
  const rg = new THREE.BufferGeometry(); rg.setAttribute('position', new THREE.Float32BufferAttribute(ring, 3)); rg.setIndex(ringIdx); rg.computeVertexNormals();
  scene.add(new THREE.Mesh(rg, new THREE.MeshLambertMaterial({ color: mood === 'river' ? '#6a5a70' : '#5d7088', side: THREE.DoubleSide })));
  /* 나무: 숲은 단풍 숲, 강변은 듬성듬성 */
  const treeCount = mood === 'forest' ? 2600 : mood === 'river' ? 500 : 260;
  const trees = scatter(curve, length, treeCount, si * 31 + 7, (r, side) => {
    if (mood === 'river' && side > 0) return null;
    const off = 11 + Math.pow(r(), 1.3) * 110; return { off, h: 6 + r() * 7, c: ['#3f6b2e', '#5a7d32', '#b5651d', '#c9452a', '#d9a03a', '#4d6a2a'][Math.floor(r() * 6)] };
  });
  const cone = new THREE.ConeGeometry(1, 1, 7); cone.translate(0, 0.5, 0);
  const crowns = new THREE.InstancedMesh(cone, new THREE.MeshLambertMaterial(), trees.length);
  const trunkGeo = new THREE.CylinderGeometry(0.18, 0.25, 1, 5); trunkGeo.translate(0, 0.5, 0);
  const trunks = new THREE.InstancedMesh(trunkGeo, new THREE.MeshLambertMaterial({ color: '#4a3a2a' }), trees.length);
  const m = new THREE.Matrix4(), col = new THREE.Color();
  trees.forEach((tr, i) => {
    const ground = heightAt(tr.d, tr.base, tr.off * tr.side) - 0.2;
    m.compose(new THREE.Vector3(tr.x, ground + tr.h * 0.25, tr.z), new THREE.Quaternion(), new THREE.Vector3(tr.h * 0.2, tr.h * 0.8, tr.h * 0.2)); crowns.setMatrixAt(i, m); crowns.setColorAt(i, col.set(tr.c));
    m.compose(new THREE.Vector3(tr.x, ground, tr.z), new THREE.Quaternion(), new THREE.Vector3(1, tr.h * 0.3, 1)); trunks.setMatrixAt(i, m);
  });
  scene.add(crowns, trunks);
  /* 도심: 건물과 가로등 */
  if (mood === 'city') {
    const blocks = scatter(curve, length, 420, 99, (r, side) => (side > 0 && r() < 0.45 ? null : { off: 30 + r() * 140, h: 12 + Math.pow(r(), 2) * 70, w: 12 + r() * 18, d: 12 + r() * 16, c: ['#b9bcc2', '#d6d0c4', '#9ea7b3', '#c9c2b6', '#8f969f'][Math.floor(r() * 5)] }));
    const box = new THREE.BoxGeometry(1, 1, 1); box.translate(0, 0.5, 0);
    const houses = new THREE.InstancedMesh(box, new THREE.MeshLambertMaterial(), blocks.length);
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
  return { scene, curve, length };
}

export function createRoad(canvas, course, moods) {
  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, preserveDrawingBuffer: true });
  renderer.setPixelRatio(1);
  renderer.setSize(canvas.width, canvas.height, false);
  const camera = new THREE.PerspectiveCamera(58, canvas.width / canvas.height, 0.5, 9000);
  const worlds = moods.map((mood, i) => build(course, i, mood));
  const up = new THREE.Vector3(0, 1, 0);
  return {
    lengths: worlds.map((w) => w.length),
    draw(si, dist) {
      const w = worlds[si], d = Math.min(Math.max(dist, 0), w.length - 40);
      const at = (m) => { const u = Math.min(m, w.length) / w.length, p = w.curve.getPointAt(u), r = new THREE.Vector3().crossVectors(w.curve.getTangentAt(u), up).normalize(); return new THREE.Vector3(p.x + r.x * LANE, p.y, p.z + r.z * LANE); };
      const p = at(d);
      /* 운전자는 한 점이 아니라 앞길을 넓게 본다. 앞 8~32m 점을 평균해 급커브에서 시선이 튀지 않게 한다 */
      const look = [8, 14, 20, 26, 32].map((m) => at(d + m)).reduce((s, q) => s.add(q), new THREE.Vector3()).multiplyScalar(1 / 5);
      camera.position.set(p.x, p.y + EYE, p.z);
      camera.lookAt(look.x, look.y + EYE * 0.85, look.z);
      renderer.render(w.scene, camera);
    },
  };
}
