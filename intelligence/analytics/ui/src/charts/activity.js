import { mountChart, fmtInt, shortTime, setError } from './theme.js';

export function renderActivity(result, legacyResult) {
  if (!result.ok) {
    setError('error-activity', `Could not load activity — ${result.error}`);
    return;
  }
  setError('error-activity', null);
  const buckets = result.data.buckets ?? [];
  mountChart('chart-activity', {
    type: 'line',
    data: {
      labels: buckets.map((b) => shortTime(b.period_start)),
      datasets: [
        {
          label: 'Active users',
          data: buckets.map((b) => b.active_users),
          borderColor: '#0d9488',
          backgroundColor: 'rgba(13, 148, 136, 0.12)',
          fill: true,
          tension: 0.3,
          pointRadius: 0,
          pointHitRadius: 8,
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

  const note = document.querySelector('#note-activity');
  if (buckets.length === 0) {
    note.textContent = 'No activity in this range.';
  } else if (legacyResult?.ok) {
    const hours = legacyResult.data;
    const peak = hours.reduce((a, b) => (b.active_users > a.active_users ? b : a), hours[0]);
    note.textContent = peak
      ? `Hourly detail: ${hours.length} active hours · busiest ${shortTime(peak.period_start)} with ${fmtInt.format(peak.active_users)} users.`
      : 'Hourly detail: no active hours.';
  } else {
    note.textContent = '';
  }
  document.querySelector('#kpi-active').textContent = buckets.length
    ? fmtInt.format(buckets[buckets.length - 1].active_users)
    : '0';
}
