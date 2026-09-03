<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import SarvLogo from './SarvLogo.vue'
import SarvMark from './SarvMark.vue'

defineProps<{ title: string }>()

const emit = defineEmits<{
  'open-left': []
  'open-right': []
  'open-search': []
}>()
</script>

<template>
  <header class="mobile-topbar" data-testid="mobile-topbar">
    <button
      class="mobile-topbar__icon-btn"
      type="button"
      data-testid="mobile-topbar-menu"
      aria-label="Open navigation menu"
      @click="emit('open-left')"
    >
      <AppIcon name="menu" :size="20" />
    </button>

    <div class="mobile-topbar__brand">
      <SarvMark :size="24" />
      <SarvLogo class="mobile-topbar__logo" />
      <span class="mobile-topbar__title" data-testid="mobile-topbar-title">{{ title }}</span>
    </div>

    <div class="mobile-topbar__actions">
      <button
        class="mobile-topbar__icon-btn"
        type="button"
        data-testid="mobile-topbar-search"
        aria-label="Open search"
        @click="emit('open-search')"
      >
        <AppIcon name="search" :size="20" />
      </button>
      <button
        class="mobile-topbar__icon-btn"
        type="button"
        data-testid="mobile-topbar-topics"
        aria-label="Open hot topics and news"
        @click="emit('open-right')"
      >
        <AppIcon name="bookmark" :size="20" />
      </button>
    </div>
  </header>
</template>

<style scoped>
.mobile-topbar {
  display: none;
}

@media (max-width: 640px) {
  .mobile-topbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sarv-space-2);
    min-height: var(--sarv-topbar-h);
    padding: 0 var(--sarv-space-2);
    padding-top: env(safe-area-inset-top);
    background: var(--sarv-panel);
    border-bottom: 1px solid var(--sarv-border-bright);
    position: sticky;
    top: 0;
    z-index: 30;
    flex-shrink: 0;
  }

  .mobile-topbar__brand {
    display: flex;
    align-items: center;
    gap: var(--sarv-space-2);
    min-width: 0;
    flex: 1;
  }

  .mobile-topbar__logo {
    font-size: 18px;
    letter-spacing: 0.22em;
  }

  .mobile-topbar__logo :deep(.sarv-logo__cursor) {
    font-size: 14px;
  }

  .mobile-topbar__title {
    font-size: 10px;
    letter-spacing: 0.14em;
    color: var(--sarv-text-dim);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .mobile-topbar__actions {
    display: flex;
    align-items: center;
    gap: 2px;
    flex-shrink: 0;
  }

  .mobile-topbar__icon-btn {
    display: grid;
    place-items: center;
    width: 44px;
    height: 44px;
    background: transparent;
    border: 1px solid transparent;
    color: var(--sarv-text-dim);
    cursor: pointer;
  }

  .mobile-topbar__icon-btn:hover,
  .mobile-topbar__icon-btn:active {
    background: var(--sarv-panel-alt);
    border-color: var(--sarv-border);
    color: var(--sarv-green);
  }
}
</style>
