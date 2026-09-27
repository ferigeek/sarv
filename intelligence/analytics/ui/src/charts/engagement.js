import { mountChart, fmtInt, shortTime, setError } from './theme.js';

export function renderEngagement(result) {
  if (!result.ok) {
    setError('error-engagement', `Could not load engagement — ${result.error}`);
    return;
  }
  setError('error-engagement', null);
  const { totals, buckets } = result.data;
  document.querySelector('#kpi-new').textContent = fmtInt.format(totals.new_users);
  mountChart('chart-engagement', {
    type: 'line',
    data: {
      labels: (buckets ?? []).map((b) => shortTime(b.period_start)),
      datasets: [
        { label: 'Active', data: (buckets ?? []).map((b) => b.active_users), borderColor: '#0d9488', tension: 0.3, pointRadius: 0, pointHitRadius: 8 },
        { label: 'New', data: (buckets ?? []).map((b) => b.new_users), borderColor: '#2f6fed', tension: 0.3, pointRadius: 0, pointHitRadius: 8 },
        { label: 'Returning', data: (buckets ?? []).map((b) => b.returning_users), borderColor: '#f59e0b', tension: 0.3, pointRadius: 0, pointHitRadius: 8 },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom', labels: { boxWidth: 12 } } },
      scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
    },
  });
}
