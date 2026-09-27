export const DEFAULTS = {
  interval: '1h',
  limit: 10,
};

const pad = (n) => String(n).padStart(2, '0');

function toControlFormat(date) {
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

export function applyPreset(preset) {
  const end = new Date();
  const start = new Date(end);
  if (preset === '24h') start.setUTCHours(start.getUTCHours() - 24);
  else if (preset === '30d') start.setUTCDate(start.getUTCDate() - 30);
  else start.setUTCDate(start.getUTCDate() - 7);
  return {
    start: toControlFormat(start),
    end: toControlFormat(end),
    interval: preset === '24h' ? '15m' : preset === '30d' ? '1d' : DEFAULTS.interval,
    limit: DEFAULTS.limit,
  };
}

export function setControlValues({ start, end, interval, limit }) {
  document.querySelector('#start-time').value = start;
  document.querySelector('#end-time').value = end;
  document.querySelector('#interval').value = interval;
  document.querySelector('#limit').value = limit;
}

// datetime-local gives "YYYY-MM-DDTHH:MM" (local wall time treated as UTC here);
// APIs accept ISO 8601, so append seconds + UTC designator.
export function toApiIso(value) {
  return `${value}:00Z`;
}
