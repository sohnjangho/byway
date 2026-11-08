// 날씨·계절·시간에 따른 하늘과 빛. 기본은 구간 분위기(숲·도심 맑은 낮, 강변 노을)이고, 구간 경계 150m에서 서서히 바뀐다.
import * as THREE from 'three';
import { zoneAt } from './world.js';

/* photo: 풍경 띠 번호(0 맑은 하늘 산, 1 노을 산). aim: 구간 진행 방향 기준 [앞, 오른쪽, 위] */
export const ZONE_ENV = {
  park: { photo: 0, top: '#b3c5e5', zenith: '#3f7cc8', fog: '#c6d4e2', sun: '#fff4e0', light: 1.0, aim: [0.5, 0.45, 0.7] },
  forest: { photo: 0, top: '#b3c5e5', zenith: '#3f7cc8', fog: '#c6d4e2', sun: '#fff4e0', light: 1.0, aim: [0.55, 0.45, 0.7] },
  city: { photo: 0, top: '#b3c5e5', zenith: '#3f7cc8', fog: '#cbd7e4', sun: '#ffffff', light: 1.05, aim: [-0.4, 0.5, 0.75] },
  river: { photo: 1, top: '#c3dcf1', zenith: '#5d7fb0', fog: '#e6c39c', sun: '#ffc283', light: 1.25, aim: [0.95, -0.25, 0.13] },
};

export function createEnv(world) {
  const up = new THREE.Vector3(0, 1, 0);
  /* 구간마다 해 방향을 그 구간 도로가 처음 향하는 쪽 기준으로 정한다 */
  const sunDirs = world.zones.map((z) => {
    const a = world.curve.getPointAt(Math.min(z.from + 5, world.length) / world.length), b = world.curve.getPointAt(Math.min(z.from + 300, world.length - 1) / world.length);
    const fwd = new THREE.Vector3(b.x - a.x, 0, b.z - a.z).normalize(), right = new THREE.Vector3().crossVectors(fwd, up).normalize(), [f, r, u] = ZONE_ENV[z.mood].aim;
    return fwd.multiplyScalar(f).add(right.multiplyScalar(r)).add(new THREE.Vector3(0, u, 0)).normalize();
  });
  const c1 = new THREE.Color(), c2 = new THREE.Color();
  const mix = (a, b, f) => c1.set(a).lerp(c2.set(b), f).clone();
  return {
    /* d: 차 위치(m), eye: 카메라 위치 */
    apply(d, eye) {
      const z = zoneAt(world.zones, d), i = world.zones.indexOf(z), next = world.zones[i + 1];
      const f = next ? Math.min(1, Math.max(0, (d - (z.to - 150)) / 150)) : 0, A = ZONE_ENV[z.mood], B = next ? ZONE_ENV[next.mood] : A;
      const dir = sunDirs[i].clone().lerp(next ? sunDirs[i + 1] : sunDirs[i], f).normalize();
      world.dome.material.uniforms.top.value = mix(A.top, B.top, f);
      world.dome.material.uniforms.zenith.value = mix(A.zenith, B.zenith, f);
      world.dome.position.copy(eye);
      world.backs.forEach((b, k) => { b.position.set(eye.x, eye.y - 1.2, eye.z); b.material.opacity = (A.photo === k ? 1 - f : 0) + (B.photo === k ? f : 0); b.visible = b.material.opacity > 0.01; });
      world.scene.fog.color = mix(A.fog, B.fog, f);
      world.sun.color = mix(A.sun, B.sun, f);
      world.sun.intensity = 3.2 * (A.light + (B.light - A.light) * f);
      world.hemi.intensity = 1.6 * (A.light + (B.light - A.light) * f);
      world.sunDisc.material.color = mix(A.sun, B.sun, f);
      world.sunDisc.position.copy(eye).addScaledVector(dir, 2600);
      world.sun.target.position.copy(eye); world.sun.position.copy(eye).addScaledVector(dir, 300);
      return { zone: z, next, f };
    },
  };
}
