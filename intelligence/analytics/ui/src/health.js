const dot = document.querySelector('#health-dot');
const text = document.querySelector('#health-text');
const clock = document.querySelector('#console-clock');

function tick() {
  clock.textContent = new Date().toISOString().replace('T', ' ').slice(0, 19) + 'Z';
}

export function setHealth(results) {
  const entries = Object.values(results);
  const failed = entries.filter((r) => !r.ok).length;
  dot.classList.remove('health-unknown', 'health-live', 'health-degraded', 'health-down');
  if (failed === 0) {
    dot.classList.add('health-live');
    text.textContent = 'LIVE';
  } else if (failed < entries.length) {
    dot.classList.add('health-degraded');
    text.textContent = `DEGRADED ${entries.length - failed}/${entries.length}`;
  } else {
    dot.classList.add('health-down');
    text.textContent = 'DOWN';
  }
  tick();
}
