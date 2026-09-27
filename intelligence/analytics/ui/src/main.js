import 'bootstrap/dist/css/bootstrap.min.css';
import './style.css';
import { DEFAULTS, applyPreset, toApiIso, setControlValues } from './state.js';
import { fetchAll } from './api.js';
import { renderAll } from './charts/index.js';
import { setHealth } from './health.js';

const form = document.querySelector('#controls');

async function refresh() {
  const start = document.querySelector('#start-time').value;
  const end = document.querySelector('#end-time').value;
  const interval = document.querySelector('#interval').value;
  const limit = Number(document.querySelector('#limit').value || DEFAULTS.limit);
  const source = document.querySelector('#source').value || undefined;
  const params = {
    start_time: toApiIso(start),
    end_time: toApiIso(end),
    interval,
    limit,
    source,
  };
  document.querySelector('#query-stamp').textContent =
    `RANGE ${params.start_time} → ${params.end_time} · ${interval}`;
  const results = await fetchAll(params);
  setHealth(results);
  renderAll(results, params);
}

form.addEventListener('submit', (e) => {
  e.preventDefault();
  refresh();
});

document.querySelectorAll('[data-preset]').forEach((btn) => {
  btn.addEventListener('click', () => {
    setControlValues(applyPreset(btn.dataset.preset));
    refresh();
  });
});

document.querySelector('#source').addEventListener('change', refresh);

setControlValues(applyPreset('7d'));
refresh();
