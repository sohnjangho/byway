// 하늘·빛·날씨. '자동'은 구간 분위기(숲·도심 맑은 낮, 강변 노을)를 따르고 경계 150m에서 서서히 바뀐다.
// 계절·날씨를 고르면 그 장면이 경로 전체에 걸린다.
import * as THREE from 'three';
import { zoneAt } from './world.js';

/* photo: 풍경 띠(0 맑은 산, 1 노을 산, 2 흐린 산, 3 눈 덮인 산). tint: 사진에 곱할 색. aim: 진행 방향 기준 해 [앞, 오른쪽, 위] */
const BASE = { photo: 0, tint: '#ffffff', top: '#b3c5e5', zenith: '#3f7cc8', fog: '#c6d4e2', near: 250, far: 3000, sun: '#fff4e0', light: 1.0, aim: [0.55, 0.45, 0.7] };
export const ZONE_ENV = {
  park: { ...BASE, aim: [0.5, 0.45, 0.7] },
  forest: BASE,
  city: { ...BASE, fog: '#cbd7e4', sun: '#ffffff', light: 1.05, aim: [-0.4, 0.5, 0.75] },
  river: { ...BASE, photo: 1, top: '#c3dcf1', zenith: '#5d7fb0', fog: '#e6c39c', sun: '#ffc283', light: 1.25, aim: [0.95, -0.25, 0.13] },
};
/* season: 나무 잎, ground: 땅 칠, mood: 에움길 화면 분위기 */
export const PRESETS = {
  auto: { label: '자동', season: 'autumn', ground: null, mood: null },
  spring: { label: '봄', env: { ...BASE, top: '#bcd0ea', zenith: '#4a86cc', fog: '#d3dde8', sun: '#fff6e8', light: 1.05 }, season: 'spring', ground: 'spring', mood: 'spring' },
  summer: { label: '여름', env: { ...BASE, top: '#a9c4e8', zenith: '#2f72c8', sun: '#ffffff', light: 1.15, aim: [0.3, 0.3, 0.9] }, season: 'summer', ground: 'summer', mood: 'summer' },
  autumn: { label: '가을', env: BASE, season: 'autumn', ground: null, mood: 'autumn' },
  winter: { label: '겨울', env: { ...BASE, photo: 3, top: '#2b4f6d', zenith: '#1d3f60', fog: '#d6dde6', sun: '#fff8f0', light: 1.1, aim: [0.5, 0.4, 0.45] }, season: 'winter', ground: 'snow', mood: 'winter' },
  rain: { label: '비', env: { ...BASE, photo: 2, tint: '#c9ced6', top: '#9ba2b0', zenith: '#7b8393', fog: '#98a0ab', near: 40, far: 900, sun: '#d8dde4', light: 0.55, aim: [0.3, 0.3, 0.8] }, season: 'summer', ground: 'wet', mood: 'rain', rain: true, wipers: true, lights: true },
  snow: { label: '눈', env: { ...BASE, photo: 3, tint: '#cfd5dc', top: '#aeb6c1', zenith: '#8f99a6', fog: '#dde3ea', near: 30, far: 700, sun: '#eef2f6', light: 0.85, aim: [0.3, 0.3, 0.8] }, season: 'winter', ground: 'snow', mood: 'snow', snow: true, wipers: true, lights: true },
  sunset: { label: '노을', env: ZONE_ENV.river, season: 'autumn', ground: null, mood: 'sunset' },
  night: { label: '밤', env: { ...BASE, tint: '#2a3246', top: '#16223a', zenith: '#04070f', fog: '#0d1424', near: 30, far: 700, sun: '#56689a', light: 0.16, aim: [0.3, -0.2, 0.6] }, season: 'autumn', ground: null, mood: 'night', night: true, lights: true },
};

export function createEnv(world) {
  const up = new THREE.Vector3(0, 1, 0);
  const fwds = world.zones.map((z) => {
    const a = world.curve.getPointAt(Math.min(z.from + 5, world.length) / world.length), b = world.curve.getPointAt(Math.min(z.from + 300, world.length - 1) / world.length);
    const fwd = new THREE.Vector3(b.x - a.x, 0, b.z - a.z).normalize();
    return { fwd, right: new THREE.Vector3().crossVectors(fwd, up).normalize() };
  });
  const aimDir = (i, aim) => fwds[i].fwd.clone().multiplyScalar(aim[0]).add(fwds[i].right.clone().multiplyScalar(aim[1])).add(new THREE.Vector3(0, aim[2], 0)).normalize();
  const c1 = new THREE.Color(), c2 = new THREE.Color();
  const mix = (a, b, f) => c1.set(a).lerp(c2.set(b), f).clone();
  let preset = PRESETS.auto;
  return {
    get preset() { return preset; },
    setPreset(name) { preset = PRESETS[name]; },
    apply(d, eye) {
      const z = zoneAt(world.zones, d), i = world.zones.indexOf(z), next = world.zones[i + 1];
      let A, B, f;
      if (preset.env) { A = B = preset.env; f = 0; }
      else { f = next ? Math.min(1, Math.max(0, (d - (z.to - 150)) / 150)) : 0; A = ZONE_ENV[z.mood]; B = next ? ZONE_ENV[next.mood] : A; }
      const dir = aimDir(i, A.aim).lerp(next && !preset.env ? aimDir(i + 1, B.aim) : aimDir(i, A.aim), f).normalize();
      world.dome.material.uniforms.top.value = mix(A.top, B.top, f);
      world.dome.material.uniforms.zenith.value = mix(A.zenith, B.zenith, f);
      world.dome.position.copy(eye);
      world.backs.forEach((b, k) => {
        b.position.set(eye.x, eye.y - 1.2, eye.z);
        b.material.opacity = (A.photo === k ? 1 - f : 0) + (B.photo === k ? f : 0); b.visible = b.material.opacity > 0.01;
        b.material.color = mix(A.tint, B.tint, f);
      });
      world.scene.fog.color = mix(A.fog, B.fog, f);
      world.scene.fog.near = A.near + (B.near - A.near) * f; world.scene.fog.far = A.far + (B.far - A.far) * f;
      const light = A.light + (B.light - A.light) * f;
      world.sun.color = mix(A.sun, B.sun, f); world.sun.intensity = 3.2 * light; world.hemi.intensity = 1.6 * light;
      world.sunDisc.material.color = mix(A.sun, B.sun, f); world.sunDisc.visible = !preset.night && !preset.rain && !preset.snow;
      world.sunDisc.position.copy(eye).addScaledVector(dir, 2600);
      world.sun.target.position.copy(eye); world.sun.position.copy(eye).addScaledVector(dir, 300);
      world.bulbs.material.color.set(preset.night ? '#fff1c8' : '#4a4a44');
      return { zone: z, next, f };
    },
  };
}
