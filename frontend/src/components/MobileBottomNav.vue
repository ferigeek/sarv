<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(defineProps<{ activeRoute: string }>(), { activeRoute: 'feed' })

const emit = defineEmits<{
  navigate: [name: string]
  create: []
  'open-left': []
  'open-right': []
  'open-search': []
}>()
</script>

<template>
  <nav class="mobile-bottomnav" data-testid="mobile-bottom-nav" aria-label="Primary">
    <button
      class="mobile-bottomnav__item"
      :class="{ 'mobile-bottomnav__item--active': activeRoute === 'feed' }"
      type="button"
      data-testid="mobile-nav-feed"
      aria-label="Feed"
      @click="emit('navigate', 'feed')"
    >
      <AppIcon name="home" :size="20" />
      <span class="mobile-bottomnav__label">feed</span>
    </button>

    <button
      class="mobile-bottomnav__item"
      type="button"
      data-testid="mobile-nav-search"
      aria-label="Search"
      @click="emit('open-search')"
    >
      <AppIcon name="search" :size="20" />
      <span class="mobile-bottomnav__label">search</span>
    </button>

    <button
      class="mobile-bottomnav__item mobile-bottomnav__item--create"
      type="button"
      data-testid="mobile-nav-create"
      aria-label="Create post"
      @click="emit('create')"
    >
      <AppIcon name="plus" :size="22" />
      <span class="mobile-bottomnav__label">post</span>
    </button>

    <button
      class="mobile-bottomnav__item"
      type="button"
      data-testid="mobile-nav-topics"
      aria-label="Hot topics and news"
      @click="emit('open-right')"
    >
      <AppIcon name="bookmark" :size="20" />
      <span class="mobile-bottomnav__label">topics</span>
    </button>

    <button
      class="mobile-bottomnav__item"
      :class="{ 'mobile-bottomnav__item--active': activeRoute === 'profile' }"
      type="button"
      data-testid="mobile-nav-menu"
      aria-label="Profile and menu"
      @click="emit('open-left')"
    >
      <AppIcon name="user" :size="20" />
      <span class="mobile-bottomnav__label">menu</span>
    </button>
  </nav>
</template>

<style scoped>
.mobile-bottomnav {
  display: none;
}

@media (max-width: 640px) {
  .mobile-bottomnav {
    display: grid;
    grid-template-columns: repeat(5, 1fr);
    gap: 1px;
    background: var(--sarv-border);
    border-top: 1px solid var(--sarv-border-bright);
    padding-bottom: env(safe-area-inset-bottom);
    position: sticky;
    bottom: 0;
    z-index: 30;
    flex-shrink: 0;
  }

  .mobile-bottomnav__item {
    display: grid;
    justify-items: center;
    align-content: center;
    gap: 2px;
    min-height: var(--sarv-bottomnav-h);
    padding: 6px 2px;
    background: var(--sarv-panel);
    border: none;
    border-top: 2px solid transparent;
    color: var(--sarv-text-dim);
    font-size: 9px;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    cursor: pointer;
  }

  .mobile-bottomnav__item:hover,
  .mobile-bottomnav__item:active {
    background: var(--sarv-panel-alt);
    color: var(--sarv-text);
  }

  .mobile-bottomnav__item--active {
    color: var(--sarv-green);
    border-top-color: var(--sarv-green);
  }

  .mobile-bottomnav__item--create {
    background: var(--sarv-green-faint);
    color: var(--sarv-green);
  }

  .mobile-bottomnav__item--create:hover,
  .mobile-bottomnav__item--create:active {
    background: var(--sarv-green-dark);
    color: var(--sarv-green-bright);
  }

  .mobile-bottomnav__label {
    line-height: 1;
  }
}
</style>
