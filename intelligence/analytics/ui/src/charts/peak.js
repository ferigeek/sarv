import { mountChart, fmtInt, setError } from './theme.js';

export function renderPeak(result) {
  if (!result.ok) {
    setError('error-peak', `Could not load peak hours — ${result.error}`);
    return;
  }
  setError('error-peak', null);
  const { buckets, peak_hour } = result.data;
  document.querySelector('#peak-badge').textContent =
    `Peak: ${String(peak_hour.hour).padStart(2, '0')}:00 UTC · ${fmtInt.format(peak_hour.event_count)} events · ${fmtInt.format(peak_hour.active_users)} users`;
  mountChart('chart-peak', {
    type: 'bar',
    data: {
      labels: buckets.map((b) => String(b.hour).padStart(2, '0')),
      datasets: [
        {
          label: 'Events',
          data: buckets.map((b) => b.event_count),
          backgroundColor: buckets.map((b) => (b.hour === peak_hour.hour ? '#0d9488' : '#bfe3dd')),
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
    },
  });
}
