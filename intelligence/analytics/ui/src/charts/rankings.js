import { fmtInt, setError } from './theme.js';

function esc(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

export function renderMostActive(result) {
  if (!result.ok) {
    setError('error-most-active', `Could not load top users — ${result.error}`);
    return;
  }
  setError('error-most-active', null);
  const users = result.data.users;
  document.querySelector('#table-most-active').innerHTML =
    `<thead><tr><th scope="col">#</th><th scope="col">User</th><th scope="col" class="text-end">Events</th></tr></thead><tbody>` +
    (users.length
      ? users.map((u, i) =>
          `<tr><td>${i + 1}</td><td><strong>${esc(u.username)}</strong> <span class="text-muted">${esc(u.display_name)}</span></td><td class="text-end">${fmtInt.format(u.event_count)}</td></tr>`,
        ).join('')
      : `<tr><td colspan="3" class="text-muted">No events in this range.</td></tr>`) +
    `</tbody>`;
}
