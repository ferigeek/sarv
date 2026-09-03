<script setup lang="ts">
import AmbientNetwork from './AmbientNetwork.vue'
import HotTopicsPanel from './HotTopicsPanel.vue'
import PlatformNewsPanel from './PlatformNewsPanel.vue'
</script>

<template>
  <aside class="right-sidebar" data-testid="right-sidebar">
    <HotTopicsPanel />
    <PlatformNewsPanel />
    <div class="right-ambient-wrap" data-testid="right-ambient">
      <AmbientNetwork />
    </div>
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

/* RightSidebar itself is the scroll container; panels sit on a shared border background. */
.right-sidebar :deep(.right-panel) {
  flex-shrink: 0;
}

.right-ambient-wrap {
  flex: 1 1 auto;
  min-height: 160px;
  display: flex;
  background: var(--sarv-panel);
  border-top: 1px solid var(--sarv-border);
  border-bottom: 1px solid var(--sarv-border);
  overflow: hidden;
  flex-shrink: 0;
}

/* Skip the ambient canvas inside the tablet/mobile drawer to save space and GPU. */
@media (max-width: 900px) {
  .right-ambient-wrap {
    display: none;
  }
}
</style>
