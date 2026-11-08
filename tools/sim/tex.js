// 실사 재질. 텍스처는 Poly Haven CC0(tex/SOURCES.md). 블렌더 모델에는 무늬 좌표(UV)가 없어 여기서 투영해 만든다.
import * as THREE from 'three';

const loader = new THREE.TextureLoader();
const cache = {};
/* gray: 회색조로 바꿔 꼭짓점·인스턴스 색(계절 색)을 곱할 수 있게 한다. 밝기는 평균 0.8 쯤으로 맞춘다 */
export async function texture(name, { gray = false, color = true, repeat = 1 } = {}) {
  const key = name + gray;
  if (!cache[key]) {
    const t = await loader.loadAsync(new URL(`tex/${name}.jpg`, import.meta.url).href);
    let out = t;
    if (gray) {
      const c = document.createElement('canvas'); c.width = t.image.width; c.height = t.image.height;
      const g = c.getContext('2d'); g.drawImage(t.image, 0, 0);
      const px = g.getImageData(0, 0, c.width, c.height), d = px.data; let sum = 0;
      for (let i = 0; i < d.length; i += 4) sum += 0.3 * d[i] + 0.59 * d[i + 1] + 0.11 * d[i + 2];
      const k = (0.8 * 255) / (sum / (d.length / 4));
      for (let i = 0; i < d.length; i += 4) { const v = Math.min(255, (0.3 * d[i] + 0.59 * d[i + 1] + 0.11 * d[i + 2]) * k); d[i] = d[i + 1] = d[i + 2] = v; }
      g.putImageData(px, 0, 0); out = new THREE.CanvasTexture(c);
    }
    if (color) out.colorSpace = THREE.SRGBColorSpace;
    out.wrapS = out.wrapT = THREE.RepeatWrapping; out.anisotropy = 8;
    cache[key] = out;
  }
  const t = cache[key].clone(); t.repeat.set(repeat, repeat); t.needsUpdate = true; return t;
}
/* 상자 투영: 면이 향하는 축에 따라 위치 두 성분을 무늬 좌표로 쓴다(m 단위 × scale) */
export function boxUV(geo, scale = 1) {
  const g = geo.index ? geo.toNonIndexed() : geo;
  g.computeVertexNormals();
  const p = g.attributes.position, n = g.attributes.normal, uv = new Float32Array(p.count * 2);
  for (let i = 0; i < p.count; i++) {
    const ax = Math.abs(n.getX(i)), ay = Math.abs(n.getY(i)), az = Math.abs(n.getZ(i));
    const [u, v] = ax >= ay && ax >= az ? [p.getZ(i), p.getY(i)] : ay >= az ? [p.getX(i), p.getZ(i)] : [p.getX(i), p.getY(i)];
    uv[i * 2] = u * scale; uv[i * 2 + 1] = v * scale;
  }
  g.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  return g;
}
/* 원기둥 투영: 나무 줄기·잎 뭉치처럼 세로로 선 형체 */
export function cylinderUV(geo, around = 2, up = 0.5) {
  const p = geo.attributes.position, uv = new Float32Array(p.count * 2);
  for (let i = 0; i < p.count; i++) { uv[i * 2] = ((Math.atan2(p.getZ(i), p.getX(i)) / (Math.PI * 2)) + 0.5) * around; uv[i * 2 + 1] = p.getY(i) * up; }
  geo.setAttribute('uv', new THREE.BufferAttribute(uv, 2));
  return geo;
}
/* 건물 외벽: 밝은 콘크리트 결 위에 유리창 격자를 그린다(받은 외벽 사진은 창이 없고 어둡다) */
function windows(concrete) {
  const c = document.createElement('canvas'); c.width = 512; c.height = 1024;
  const g = c.getContext('2d');
  g.fillStyle = '#d9d6cf'; g.fillRect(0, 0, 512, 1024);
  g.globalAlpha = 0.35; g.drawImage(concrete.image, 0, 0, 512, 1024); g.globalAlpha = 1;
  for (let y = 40; y < 1024; y += 96) for (let x = 28; x < 512; x += 96) {
    const lit = (x * 7 + y * 13) % 5 === 0;
    g.fillStyle = lit ? '#e8dcb8' : '#5d7389'; g.fillRect(x, y, 60, 58);
    g.fillStyle = 'rgba(255,255,255,0.18)'; g.fillRect(x, y, 60, 8);
  }
  const t = new THREE.CanvasTexture(c); t.colorSpace = THREE.SRGBColorSpace; t.wrapS = t.wrapT = THREE.RepeatWrapping; t.anisotropy = 8;
  return t;
}
export async function loadMaterials() {
  const [grass, grassN, road, roadN, roadSnow, bark, barkN, leaves, facade] = await Promise.all([
    texture('leafy_grass_diff', { gray: true, repeat: 1 }), texture('leafy_grass_nor', { color: false }),
    texture('asphalt_02_diff'), texture('asphalt_02_nor', { color: false }), texture('asphalt_snow_diff'),
    texture('japanese_zelkova_bark_diff'), texture('japanese_zelkova_bark_nor', { color: false }),
    texture('forest_leaves_02_diff', { gray: true }), texture('concrete_tile_facade_diff'),
  ]);
  return { grass, grassN, road, roadN, roadSnow, bark, barkN, leaves, facade: windows(facade) };
}
export async function cabinMaterials() {
  const [leather, leatherN, dash, liner] = await Promise.all([
    texture('brown_leather_diff', { repeat: 3 }), texture('brown_leather_nor', { color: false, repeat: 3 }),
    texture('fabric_leather_02_diff', { repeat: 4 }), texture('fabric_pattern_05_diff', { repeat: 3 }),
  ]);
  return {
    leather: new THREE.MeshStandardMaterial({ map: leather, normalMap: leatherN, roughness: 0.6, color: '#d9cfc6' }),
    trim_dark: new THREE.MeshStandardMaterial({ map: dash, roughness: 0.75, color: '#2b2c2f' }),
    trim_mid: new THREE.MeshStandardMaterial({ map: dash, roughness: 0.6, color: '#77787c' }),
    headliner: new THREE.MeshStandardMaterial({ map: liner, roughness: 0.95, color: '#e6e1d8' }),
  };
}
