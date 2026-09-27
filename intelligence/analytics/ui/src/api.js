async function getJson(path, params) {
  const qs = new URLSearchParams(params).toString();
  const res = await fetch(`${path}?${qs}`);
  if (res.status === 401) {
    window.location.href = '/login';
    throw new Error('Session expired — redirecting to sign in.');
  }
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`${res.status} ${body.slice(0, 160)}`);
  }
  return res.json();
}

function wrap(promise) {
  return promise.then(
    (data) => ({ ok: true, data }),
    (error) => ({ ok: false, error: String(error?.message ?? error) }),
  );
}

// One result per dashboard section; a single failing endpoint must not
// break the other panels, so results are settled independently.
export function fetchAll({ start_time, end_time, interval, limit, source }) {
  const range = { start_time, end_time };
  return Promise.all([
    wrap(getJson('/usage/activity', { ...range, interval })),
    wrap(getJson('/users/active', range)),
    wrap(getJson('/events/breakdown', { ...range, interval })),
    wrap(getJson('/users/engagement', { ...range, interval })),
    wrap(getJson('/usage/peak-hours', range)),
    wrap(
      getJson('/engagement/viewing-time', {
        ...range,
        interval,
        ...(source ? { source } : {}),
      }),
    ),
    wrap(getJson('/users/most-active', { ...range, limit })),
  ]).then(([activity, activeLegacy, breakdown, engagement, peak, viewing, mostActive]) => ({
    activity,
    activeLegacy,
    breakdown,
    engagement,
    peak,
    viewing,
    mostActive,
  }));
}
