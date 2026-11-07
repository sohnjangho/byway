const test = require('node:test');
const assert = require('node:assert');
const { createSim } = require('./sim.js');

// 동쪽으로 곧게 가는 길. 지점 간격 약 888m, 전체 약 8.9km.
const path = Array.from({ length: 11 }, (_, i) => [37, 127 + i * 0.01]);
const course = {
  id: 'c', name: '시험', path,
  sections: [
    { name: '첫 구간', startIndex: 0, endIndex: 4, mood: 'forest' },
    { name: '강변', startIndex: 5, endIndex: 10, mood: 'river' },
  ],
  stops: [2, 4, 6, 8, 10],
};
const started = () => {
  const sim = createSim([course], { secondsPerCourse: 10 });
  sim.select('c');
  sim.start();
  sim.tick(9);
  return sim;
};
const drive = (sim, seconds) => { for (let t = 0; t < seconds; t += 0.1) sim.tick(0.1); };
const spoken = (sim) => sim.log.filter((e) => e.kind === 'say').map((e) => e.text);
const routes = (sim) => sim.log.filter((e) => e.kind === 'route');

test('정차 중 시작 화면은 홈이다', () => {
  assert.equal(createSim([course]).screen(), 'home');
});
test('주행 중에는 코스를 골라 둔 상태여도 대기 화면이다', () => {
  const sim = createSim([course]);
  sim.select('c');
  sim.shift('D');
  assert.equal(sim.screen(), 'waiting');
});
test('주행 중에는 앱 정보도 열리지 않는다', () => {
  const sim = createSim([course]);
  sim.about(true);
  sim.shift('D');
  assert.equal(sim.screen(), 'waiting');
});
test('주행 중에는 코스를 시작할 수 없다', () => {
  const sim = createSim([course]);
  sim.select('c');
  sim.shift('D');
  sim.start();
  assert.equal(sim.state.run, null);
});
test('시작하면 첫 구간을 내비에 올리고 안내한다', () => {
  const sim = createSim([course]);
  sim.select('c');
  sim.start();
  assert.deepEqual(routes(sim)[0].waypoints, 2);
  assert.ok(spoken(sim).includes('시험 코스를 시작합니다.'));
  assert.equal(sim.screen(), 'drive');
});
test('시작 구간의 연출이 바로 적용된다', () => {
  assert.deepEqual(started().state.run.cabin, { window: 2, fan: 3, light: 40 });
});
test('P단이면 차가 움직이지 않는다', () => {
  const sim = started();
  drive(sim, 2);
  assert.equal(sim.state.run.meters, 0);
});
test('D단이면 차가 앞으로 간다', () => {
  const sim = started();
  sim.shift('D');
  drive(sim, 2);
  assert.ok(sim.state.run.meters > 0);
});
test('구간 끝 지점을 지나면 다음 구간을 내비에 올린다', () => {
  const sim = started();
  sim.shift('D');
  drive(sim, 7);
  assert.equal(routes(sim).length, 2);
  assert.ok(spoken(sim).includes('다음 구간으로 이어갑니다.'));
});
test('코스 구간에 들어서면 연출이 바뀐다', () => {
  const sim = started();
  sim.shift('D');
  drive(sim, 6);
  assert.ok(spoken(sim).includes('강변 구간입니다.'));
  assert.deepEqual(sim.state.run.cabin, { window: 2, fan: 2, light: 70 });
});
test('이탈하면 세 번째 위치 확인에서 복귀 안내가 나온다', () => {
  const sim = started();
  sim.shift('D');
  drive(sim, 2);
  sim.stray();
  drive(sim, 2);
  assert.ok(!spoken(sim).some((t) => t.includes('벗어났습니다')));
  drive(sim, 1.5);
  assert.ok(spoken(sim).includes('코스를 벗어났습니다. 코스로 돌아가는 길을 안내합니다.'));
});
test('이탈 중에는 차가 코스를 따라 앞서가지 않는다', () => {
  const sim = started();
  sim.shift('D');
  drive(sim, 2);
  const at = sim.state.run.meters;
  sim.stray();
  drive(sim, 3);
  assert.equal(sim.state.run.meters, at);
});
test('복귀하면 남은 구간을 다시 올린다', () => {
  const sim = started();
  sim.shift('D');
  drive(sim, 2);
  sim.stray();
  const before = routes(sim).length;
  drive(sim, 8);
  assert.ok(spoken(sim).includes('코스로 돌아왔습니다.'));
  assert.equal(routes(sim).length, before + 2);
  assert.equal(sim.state.run.offset, 0);
});
test('끝까지 가면 완주하고 D단이면 대기, P단이면 요약이다', () => {
  const sim = started();
  sim.shift('D');
  drive(sim, 12);
  assert.ok(spoken(sim).includes('코스를 끝까지 달렸습니다.'));
  assert.equal(sim.screen(), 'waiting');
  sim.shift('P');
  assert.equal(sim.screen(), 'summary');
  sim.home();
  assert.equal(sim.screen(), 'home');
});
test('코스를 끝내면 주행이 멈춘다', () => {
  const sim = started();
  sim.stop();
  assert.equal(sim.state.run, null);
});
test('내비 화면은 잠시 뒤 앱으로 돌아온다', () => {
  const sim = createSim([course]);
  sim.select('c');
  sim.start();
  assert.ok(sim.state.nav);
  sim.tick(9);
  assert.equal(sim.state.nav, null);
});
