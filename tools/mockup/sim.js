/* 에움길 목업의 주행 규칙. 앱의 screenFor, CourseEngine, AmbienceFeature 와 같은 규칙을 화면 없이 흉내 낸다. */
const SCENES = {
  forest: { window: 2, fan: 3, light: 40 },
  city: { window: 0, fan: 3, light: 30 },
  river: { window: 2, fan: 2, light: 70 },
};
const NAV_SECONDS = 4;
const STRAY_FIXES = 3;
const FIX_SECONDS = 1;
const RETURN_SECONDS = 3;
const STRAY_METERS = 2400;

function metersBetween(a, b) {
  const rad = Math.PI / 180;
  const dLat = (b[0] - a[0]) * rad;
  const dLon = (b[1] - a[1]) * rad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a[0] * rad) * Math.cos(b[0] * rad) * Math.sin(dLon / 2) ** 2;
  return 2 * 6371000 * Math.asin(Math.sqrt(h));
}

function travelled(path) {
  const sums = [0];
  for (let i = 1; i < path.length; i++) sums.push(sums[i - 1] + metersBetween(path[i - 1], path[i]));
  return sums;
}

function legsOf(stops) {
  const legs = [];
  for (let i = 0; i < stops.length; i += 3) legs.push(stops.slice(i, i + 3));
  return legs;
}

function createSim(courses, { secondsPerCourse = 70 } = {}) {
  const state = { gear: 'P', selected: null, showAbout: false, run: null, finished: null, nav: null, rate: 1 };
  const log = [];
  const say = (text) => { state.spoken = text; log.push({ kind: 'say', text }); };
  const course = (id) => courses.find((c) => c.id === id);

  function showRoute(stops, label) {
    const waypoints = Math.max(stops.length - 1, 0);
    state.nav = { left: NAV_SECONDS, label, waypoints, stops };
    log.push({ kind: 'route', waypoints, label });
  }

  function enter(run, index) {
    const section = run.course.sections.find((s) => index >= s.startIndex && index <= s.endIndex);
    if (section === run.section) return;
    const first = run.section === null;
    run.section = section;
    if (!first) say(`${section.name} 구간입니다.`);
    const scene = SCENES[section.mood];
    if (scene) {
      run.cabin = scene;
      log.push({ kind: 'cabin', scene });
    }
  }

  function indexAt(run) {
    let index = 0;
    while (index < run.sums.length - 1 && run.sums[index + 1] <= run.meters) index++;
    return index;
  }

  function remainingStops(run, index) {
    return run.legs[run.leg].filter((stop) => stop > index);
  }

  function advance(run, seconds) {
    run.meters = Math.min(run.total, run.meters + (run.total / secondsPerCourse) * seconds * state.rate);
    const index = indexAt(run);
    enter(run, index);
    if (run.meters >= run.total) return finish(run);
    const leg = run.legs[run.leg];
    if (run.leg < run.legs.length - 1 && index >= leg[leg.length - 1]) {
      run.leg++;
      showRoute(run.legs[run.leg], `구간 ${run.leg + 1}/${run.legs.length}`);
      say('다음 구간으로 이어갑니다.');
    }
  }

  function finish(run) {
    say('코스를 끝까지 달렸습니다.');
    state.finished = run.course;
    state.run = null;
  }

  function strayStep(run, seconds) {
    const s = run.stray;
    if (s.phase === 'out') {
      run.offset = Math.min(STRAY_METERS, run.offset + (STRAY_METERS / (STRAY_FIXES * FIX_SECONDS)) * seconds);
      s.clock += seconds;
      if (s.clock >= STRAY_FIXES * FIX_SECONDS) {
        s.phase = 'back';
        s.clock = 0;
        showRoute([indexAt(run)], '코스 복귀');
        say('코스를 벗어났습니다. 코스로 돌아가는 길을 안내합니다.');
      }
      return;
    }
    run.offset = Math.max(0, run.offset - (STRAY_METERS / RETURN_SECONDS) * seconds);
    if (run.offset === 0) {
      run.stray = null;
      showRoute(remainingStops(run, indexAt(run)), `구간 ${run.leg + 1}/${run.legs.length}`);
      say('코스로 돌아왔습니다.');
    }
  }

  return {
    state,
    log,
    screen() {
      if (state.run) return 'drive';
      if (state.gear !== 'P') return 'waiting';
      if (state.finished) return 'summary';
      if (state.showAbout) return 'about';
      return state.selected ? 'preview' : 'home';
    },
    select(id) { state.selected = id; },
    about(show) { state.showAbout = show; },
    shift(gear) { state.gear = gear; },
    setRate(rate) { state.rate = rate; },
    home() { state.finished = null; state.selected = null; state.spoken = ''; },
    start() {
      if (state.gear !== 'P' || !state.selected) return;
      const picked = course(state.selected);
      const sums = travelled(picked.path);
      const legs = legsOf(picked.stops);
      state.run = {
        course: picked, sums, total: sums[sums.length - 1], legs, leg: 0, meters: 0,
        offset: 0, stray: null, section: null, cabin: null,
      };
      state.finished = null;
      enter(state.run, 0);
      showRoute(legs[0], `구간 1/${legs.length}`);
      say(`${picked.name} 코스를 시작합니다.`);
    },
    stop() { state.run = null; state.nav = null; state.spoken = ''; },
    stray() {
      const run = state.run;
      if (run && state.gear === 'D' && !run.stray) run.stray = { phase: 'out', clock: 0 };
    },
    tick(seconds) {
      if (state.nav) {
        state.nav.left -= seconds;
        if (state.nav.left <= 0) state.nav = null;
      }
      const run = state.run;
      if (!run || state.gear !== 'D') return;
      if (run.stray) strayStep(run, seconds);
      else advance(run, seconds);
    },
  };
}

if (typeof module !== 'undefined') module.exports = { createSim, metersBetween };
