<script setup lang="ts">
import HotTopicsPanel from './HotTopicsPanel.vue'
import PlatformNewsPanel from './PlatformNewsPanel.vue'
</script>

<template>
  <aside class="right-sidebar" data-testid="right-sidebar">
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

/* RightSidebar itself is the scroll container; panels sit on a shared border background. */
.right-sidebar :deep(.right-panel) {
  flex-shrink: 0;
}
</style>
