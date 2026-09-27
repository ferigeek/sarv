import { mountChart, fmtInt, fmtMs, shortTime, setError } from './theme.js';

export function renderViewing(result) {
  if (!result.ok) {
    setError('error-viewing', `Could not load viewing time — ${result.error}`);
    return;
  }
  setError('error-viewing', null);
  const { overall, by_source, buckets } = result.data;
  document.querySelector('#kpi-dwell').textContent = fmtMs(overall.average_duration_ms);
  const sources = Object.entries(by_source)
    .map(([s, v]) => `${s}: ${fmtMs(v.average_duration_ms)} (${fmtInt.format(v.samples)})`)
    .join(' · ');
  document.querySelector('#stat-viewing').textContent =
    `Overall ${fmtMs(overall.average_duration_ms)} across ${fmtInt.format(overall.samples)} samples${sources ? ` — ${sources}` : ''}.`;
  mountChart('chart-viewing', {
    type: 'line',
    data: {
      labels: (buckets ?? []).map((b) => shortTime(b.period_start)),
      datasets: [
        {
          label: 'Avg dwell (ms)',
          data: (buckets ?? []).map((b) => b.average_duration_ms),
          borderColor: '#0d9488',
          backgroundColor: 'rgba(13, 148, 136, 0.12)',
          fill: true,
          tension: 0.3,
          spanGaps: true,
          pointRadius: 0,
          pointHitRadius: 8,
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: { y: { beginAtZero: true } },
    },
  });
}
