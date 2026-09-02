<script setup lang="ts">
import HotTopicsPanel from './HotTopicsPanel.vue'
import PlatformNewsPanel from './PlatformNewsPanel.vue'
import SarvLogo from './SarvLogo.vue'
</script>

<template>
  <aside class="right-sidebar" data-testid="right-sidebar">
    <div class="right-sidebar__brand">
      <SarvLogo />
      <p class="right-sidebar__tagline">cypress / matrix / linux / hacker</p>
    </div>
    <HotTopicsPanel />
    <PlatformNewsPanel />
  </aside>
</template>

<style scoped>
.right-sidebar {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-height: 0;
  overflow-y: auto;
  background: var(--sarv-border);
}

.right-sidebar__brand {
  background: var(--sarv-panel);
  padding: var(--sarv-space-5) var(--sarv-space-4);
  border-bottom: 1px solid var(--sarv-border);
}

/* RightSidebar itself is the scroll container; panels sit on a shared border background. */
.right-sidebar :deep(.right-panel) {
  flex-shrink: 0;
}

.right-sidebar__tagline {
  margin-top: var(--sarv-space-2);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--sarv-text-faint);
}
</style>
