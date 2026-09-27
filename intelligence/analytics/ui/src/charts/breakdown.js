import { mountChart, fmtInt, shortTime, setError, PALETTE } from './theme.js';

const STACK_LIMIT = 6;

export function renderBreakdown(result) {
  if (!result.ok) {
    setError('error-breakdown', `Could not load events — ${result.error}`);
    return;
  }
  setError('error-breakdown', null);
  const { totals, buckets } = result.data;
  const ranked = [...totals].sort((a, b) => b.count - a.count);
  const head = ranked.slice(0, STACK_LIMIT);
  const tail = ranked.slice(STACK_LIMIT);
  const tailTotal = tail.reduce((n, t) => n + t.count, 0);

  const labels = (buckets ?? []).map((b) => shortTime(b.period_start));
  const datasets = head.map((t, i) => ({
    label: t.event_type,
    data: (buckets ?? []).map((b) => b.counts[t.event_type] ?? 0),
    backgroundColor: PALETTE[i % PALETTE.length],
    stack: 'events',
  }));
  if (tail.length > 0) {
    datasets.push({
      label: `Other (${tail.length})`,
      data: (buckets ?? []).map((b) =>
        tail.reduce((n, t) => n + (b.counts[t.event_type] ?? 0), 0),
      ),
      backgroundColor: '#cbd5d1',
      stack: 'events',
    });
  }
  mountChart('chart-breakdown', {
    type: 'bar',
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom', labels: { boxWidth: 12 } } },
      scales: { x: { stacked: true }, y: { stacked: true, beginAtZero: true, ticks: { precision: 0 } } },
    },
  });

  const total = totals.reduce((n, t) => n + t.count, 0);
  document.querySelector('#kpi-events').textContent = fmtInt.format(total);
  document.querySelector('#table-breakdown').innerHTML =
    `<thead><tr><th scope="col">Event</th><th scope="col" class="text-end">Count</th></tr></thead><tbody>` +
    ranked.map((t) => `<tr><td>${t.event_type}</td><td class="text-end">${fmtInt.format(t.count)}</td></tr>`).join('') +
    (tailTotal ? `<tr><td>Other (${tail.length})</td><td class="text-end">${fmtInt.format(tailTotal)}</td></tr>` : '') +
    `</tbody>`;
}
