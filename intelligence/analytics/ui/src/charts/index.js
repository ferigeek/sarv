import { renderActivity } from './activity.js';
import { renderEngagement } from './engagement.js';
import { renderBreakdown } from './breakdown.js';
import { renderPeak } from './peak.js';
import { renderViewing } from './viewing.js';
import { renderMostActive } from './rankings.js';

export function renderAll(results) {
  renderActivity(results.activity, results.activeLegacy);
  renderEngagement(results.engagement);
  renderBreakdown(results.breakdown);
  renderPeak(results.peak);
  renderViewing(results.viewing);
  renderMostActive(results.mostActive);
}
