// Section renderers land in the next task; this stub keeps the shell
// buildable and shows per-panel status until real charts arrive.
const SLOTS = {
  activity: 'error-activity',
  breakdown: 'error-breakdown',
  engagement: 'error-engagement',
  peak: 'error-peak',
  viewing: 'error-viewing',
  mostActive: 'error-most-active',
};

export function renderAll(results) {
  for (const [key, slotId] of Object.entries(SLOTS)) {
    const slot = document.querySelector(`#${slotId}`);
    const result = results[key];
    slot.classList.toggle('d-none', result.ok);
    slot.textContent = result.ok ? '' : `LOAD FAILED — ${result.error}`;
  }
  document.querySelector('#note-activity').textContent =
    'Chart renderers land next — data fetch layer is live.';
}
