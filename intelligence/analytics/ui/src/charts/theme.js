import { Chart } from 'chart.js/auto';

Chart.defaults.font.family = "'Manrope', system-ui, sans-serif";
Chart.defaults.color = '#64756f';
Chart.defaults.borderColor = '#e7eeec';

// Emerald-led categorical palette; extra series cycle from index 1 on.
export const PALETTE = [
  '#0d9488',
  '#2f6fed',
  '#8b5cf6',
  '#f59e0b',
  '#ef4444',
  '#06b6d4',
  '#84cc16',
  '#f472b6',
  '#64748b',
];

const live = new Map();

export function mountChart(id, config) {
  const canvas = document.querySelector(`#${id}`);
  live.get(id)?.destroy();
  const chart = new Chart(canvas, config);
  live.set(id, chart);
  return chart;
}

export const fmtInt = new Intl.NumberFormat('en-US');

export function fmtMs(ms) {
  if (ms === null || ms === undefined) return '—';
  if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.round(ms)}ms`;
}

export function shortTime(iso) {
  const d = new Date(iso);
  const date = d.toISOString().slice(5, 10).replace('-', '/');
  const time = d.toISOString().slice(11, 16);
  return `${date} ${time}`;
}

export function setError(slotId, message) {
  const slot = document.querySelector(`#${slotId}`);
  slot.classList.toggle('d-none', !message);
  slot.textContent = message ?? '';
}
