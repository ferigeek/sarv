<script setup lang="ts">
import { provide, ref } from 'vue'

import LeftSidebar from '@/components/LeftSidebar.vue'
import RightSidebar from '@/components/RightSidebar.vue'

const refreshKey = ref(0)
function onPostCreated() {
  // Bump so FeedView re-fetches when a new post is published.

  refreshKey.value += 1
}

provide('feedRefreshKey', refreshKey)
</script>

<template>
  <div class="app-shell" data-testid="app-shell">
    <LeftSidebar @created="onPostCreated" />
    <main class="app-center" data-testid="app-center">
      <router-view />
    </main>
    <RightSidebar />
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr) 320px;
  height: 100%;
  gap: 1px;
  background: var(--sarv-border);
  overflow: hidden;
}

.app-center {
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
  background: var(--sarv-bg);
}

/* Preserve the center-is-largest hierarchy on narrower viewports. */
@media (max-width: 1100px) {
  .app-shell {
    grid-template-columns: 260px minmax(0, 1fr) 280px;
  }
}

@media (max-width: 900px) {
  .app-shell {
    grid-template-columns: 240px minmax(0, 1fr);
  }

  /* Right sidebar collapses on tablet; hot topics / news remain reachable via future mobile nav. */
  .app-shell :deep([data-testid='right-sidebar']) {
    display: none;
  }
}

@media (max-width: 640px) {
  .app-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .app-shell :deep([data-testid='left-sidebar']) {
    display: none;
  }
}
</style>
