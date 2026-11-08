// 하늘·빛·날씨. '자동'은 구간 분위기(숲·도심 맑은 낮, 강변 노을)를 따르고 경계 150m에서 서서히 바뀐다.
// 계절·날씨를 고르면 그 장면이 경로 전체에 걸린다.
import * as THREE from 'three';
import { zoneAt } from './world.js';

/* photo: 풍경 띠(0 맑은 산, 1 노을 산, 2 흐린 산, 3 눈 덮인 산, 4 맑은 산 설악·내장, 5 가을 능선). alt: 그 사진이 아직 없을 때 대신 쓸 띠. tint: 사진에 곱할 색. aim: 진행 방향 기준 해 [앞, 오른쪽, 위]
   top: 하늘 돔의 지평선 색. 사진 띠 윗부분이 투명하게 녹아 돔으로 이어지므로 '사진 윗부분 평균색 × tint' 에 맞춘다
   (0 #c6d3e6, 1 #c6def2, 2 #a5aabb, 3 #2a4f6d, 4 #7bb9e3, 5 #6cafdc). near·far: 3D 땅·나무에 걸리는 안개. 사진 띠와 돔에는 걸리지 않는다
   halo: 햇무리 크기 배율. warm: 해 쪽을 볼 때 안개를 해 색으로 데우는 정도(0~1) */
const BASE = { photo: 0, tint: '#ffffff', top: '#c6d3e6', zenith: '#3f7cc8', fog: '#c6d4e2', near: 90, far: 1300, sun: '#fff4e0', light: 1.0, aim: [0.55, 0.45, 0.7], halo: 1, warm: 0.2 };
export const ZONE_ENV = {
  park: { ...BASE, photo: 4, alt: 0, top: '#7bb9e3', aim: [0.5, 0.45, 0.7] },
  forest: { ...BASE, photo: 4, alt: 0, top: '#7bb9e3' },
  city: { ...BASE, fog: '#cbd7e4', sun: '#ffffff', light: 1.05, aim: [-0.4, 0.5, 0.75] },
  /* 강변은 노을: 5번(가을 능선)은 한낮의 파란 하늘이라 낮은 주황 해와 어긋나 1번(노을 산)을 그대로 쓴다 */
  river: { ...BASE, photo: 1, top: '#c6def2', zenith: '#5d7fb0', fog: '#e6c39c', sun: '#ffc283', light: 1.25, aim: [0.95, -0.25, 0.13], halo: 1.8, warm: 0.55 },
};
/* season: 나무 잎(spring·summer·autumn·winter, 눈 내리는 날은 snow), ground: 땅 칠, mood: 에움길 화면 분위기 */
export const PRESETS = {
  auto: { label: '자동', season: 'autumn', ground: null, mood: null },
  spring: { label: '봄', env: { ...BASE, zenith: '#4a86cc', fog: '#d3dde8', sun: '#fff6e8', light: 1.05 }, season: 'spring', ground: 'spring', mood: 'spring' },
  summer: { label: '여름', env: { ...BASE, zenith: '#2f72c8', sun: '#ffffff', light: 1.15, aim: [0.3, 0.3, 0.9] }, season: 'summer', ground: 'summer', mood: 'summer' },
  autumn: { label: '가을', env: BASE, season: 'autumn', ground: null, mood: 'autumn' },
  winter: { label: '겨울', env: { ...BASE, photo: 3, top: '#2a4f6d', zenith: '#1d3f60', fog: '#d6dde6', sun: '#fff8f0', light: 1.1, aim: [0.5, 0.4, 0.45] }, season: 'winter', ground: 'snow', mood: 'winter' },
  rain: { label: '비', env: { ...BASE, photo: 2, tint: '#c9ced6', top: '#82899d', zenith: '#7b8393', fog: '#98a0ab', near: 40, far: 900, sun: '#d8dde4', light: 0.55, aim: [0.3, 0.3, 0.8], warm: 0 }, season: 'summer', ground: 'wet', mood: 'rain', rain: true, wipers: true, lights: true },
  snow: { label: '눈', env: { ...BASE, photo: 3, tint: '#cfd5dc', top: '#22425e', zenith: '#1a3550', fog: '#dde3ea', near: 30, far: 700, sun: '#eef2f6', light: 0.85, aim: [0.3, 0.3, 0.8], warm: 0 }, season: 'snow', ground: 'snow', mood: 'snow', snow: true, wipers: true, lights: true },
  sunset: { label: '노을', env: ZONE_ENV.river, season: 'autumn', ground: null, mood: 'sunset' },
  night: { label: '밤', env: { ...BASE, tint: '#2a3246', top: '#21293f', zenith: '#04070f', fog: '#0d1424', near: 30, far: 700, sun: '#56689a', light: 0.16, aim: [0.3, -0.2, 0.6], warm: 0 }, season: 'autumn', ground: null, mood: 'night', night: true, lights: true },
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
      const pa = world.backs[A.photo] ? A.photo : A.alt, pb = world.backs[B.photo] ? B.photo : B.alt;
      world.backs.forEach((b, k) => {
        b.position.set(eye.x, eye.y - 1.2, eye.z);
        b.material.opacity = (pa === k ? 1 - f : 0) + (pb === k ? f : 0); b.visible = b.material.opacity > 0.01;
        b.material.color = mix(A.tint, B.tint, f);
      });
      world.scene.fog.color = mix(A.fog, B.fog, f);
      if (world.veil) { world.veil.material.color = world.scene.fog.color; world.veil.position.set(eye.x, eye.y - 1.2, eye.z); world.veil.visible = !preset.night; }
      world.scene.fog.near = A.near + (B.near - A.near) * f; world.scene.fog.far = A.far + (B.far - A.far) * f;
      const light = A.light + (B.light - A.light) * f;
      world.sun.color = mix(A.sun, B.sun, f); world.sun.intensity = 3.2 * light; world.hemi.intensity = 1.6 * light;
      world.sunDisc.material.color = mix(A.sun, B.sun, f); world.sunDisc.visible = !preset.night && !preset.rain && !preset.snow;
      world.sunDisc.position.copy(eye).addScaledVector(dir, 2600);
      /* 햇무리와 해 쪽 안개 데우기(world.updateFlare 가 카메라 방향을 보고 쓴다), 밤 가로등 불빛 번짐 */
      const hs = 2600 * (A.halo + (B.halo - A.halo) * f);
      world.halo.position.copy(world.sunDisc.position); world.halo.visible = world.sunDisc.visible;
      world.halo.material.color = mix(A.sun, B.sun, f); world.halo.scale.set(hs, hs, 1);
      world.glow.fog.copy(world.scene.fog.color); world.glow.color.copy(world.sunDisc.material.color); world.glow.warm = A.warm + (B.warm - A.warm) * f;
      world.bulbGlow.visible = !!preset.night;
      /* 강변 꽃풀: 가을 코스모스·억새, 봄·여름 초록 풀, 겨울·눈 숨김(바뀔 때만 실제로 바꾼다) */
      if (world.meadow) world.meadow.set(preset.season);
      /* 구름: 비·눈·밤에는 끈다. 카메라를 따라다니며 아주 천천히 돌고(약 50분에 한 바퀴), 해 색을 반쯤 받는다 */
      if (world.clouds) {
        world.clouds.visible = !preset.night && !preset.rain && !preset.snow;
        world.clouds.position.copy(eye); world.clouds.rotation.y = performance.now() * 2e-6;
        const cc = mix('#ffffff', world.sun.color, 0.45);
        world.clouds.children.forEach((sp) => sp.material.color.copy(cc));
      }
      world.sun.target.position.copy(eye); world.sun.position.copy(eye).addScaledVector(dir, 300);
      world.bulbs.material.color.set(preset.night ? '#fff1c8' : '#4a4a44');
      return { zone: z, next, f };
    },
  };
}
