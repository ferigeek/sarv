<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import LeftSidebar from '@/components/LeftSidebar.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import MobileTopBar from '@/components/MobileTopBar.vue'
import PostCreateModal from '@/components/PostCreateModal.vue'
import RightSidebar from '@/components/RightSidebar.vue'

const refreshKey = ref(0)
function onPostCreated(_id?: number) {
  // Bump so FeedView re-fetches when a new post is published.
  void _id
  refreshKey.value += 1
}

provide('feedRefreshKey', refreshKey)

const route = useRoute()
const router = useRouter()

const leftOpen = ref(false)
const rightOpen = ref(false)
const showCreate = ref(false)

const topTitle = computed(() => {
  const name = String(route.name ?? '')
  if (name === 'profile') return 'PROFILE'
  if (name === 'post-detail') return 'POST'
  if (name === 'following') return 'FOLLOWING'
  if (name === 'followers') return 'FOLLOWERS'
  if (name === 'liked') return 'LIKED'
  return 'FEED'
})

const activeRoute = computed(() => {
  const name = String(route.name ?? '')
  if (name === 'profile' || name === 'following' || name === 'followers' || name === 'liked') {
    return 'profile'
  }
  return 'feed'
})

const anyDrawerOpen = computed(() => leftOpen.value || rightOpen.value || showCreate.value)

function openLeft() {
  rightOpen.value = false
  leftOpen.value = true
}

function openRight() {
  leftOpen.value = false
  rightOpen.value = true
}

function closeDrawers() {
  leftOpen.value = false
  rightOpen.value = false
}

function openSearch() {
  openLeft()
  void nextTick(() => {
    const input = document.querySelector<HTMLElement>("[data-testid='search-input']")
    input?.focus()
  })
}

function onCreateOpened() {
  closeDrawers()
  showCreate.value = true
}

function onMobileNavigate(name: string) {
  closeDrawers()
  if (name === 'feed') {
    void router.push({ name: 'feed' })
  }
}

function onCreated(id: number) {
  showCreate.value = false
  onPostCreated(id)
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    closeDrawers()
    showCreate.value = false
  }
}

watch(anyDrawerOpen, (open) => {
  document.body.classList.toggle('no-scroll', open)
})

watch(
  () => route.fullPath,
  () => {
    closeDrawers()
  },
)

if (typeof window !== 'undefined') {
  window.addEventListener('keydown', onKeydown)
}

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('keydown', onKeydown)
  }
  document.body.classList.remove('no-scroll')
})
</script>

<template>
  <div class="app-shell" data-testid="app-shell">
    <MobileTopBar
      :title="topTitle"
      @open-left="openLeft"
      @open-right="openRight"
      @open-search="openSearch"
    />
    <LeftSidebar
      :class="{ 'drawer-open': leftOpen }"
      @created="onPostCreated"
    />
    <main class="app-center" data-testid="app-center">
      <router-view />
    </main>
    <RightSidebar :class="{ 'drawer-open': rightOpen }" />
    <MobileBottomNav
      :active-route="activeRoute"
      @navigate="onMobileNavigate"
      @create="onCreateOpened"
      @open-left="openLeft"
      @open-right="openRight"
      @open-search="openSearch"
    />

    <div
      v-if="leftOpen || rightOpen"
      class="drawer-scrim"
      data-testid="drawer-scrim"
      @click="closeDrawers"
    />

    <PostCreateModal v-if="showCreate" @close="showCreate = false" @created="onCreated" />
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

.drawer-scrim {
  display: none;
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

  /* Right sidebar becomes a right drawer on tablet and below. */
  .app-shell :deep([data-testid='right-sidebar']) {
    position: fixed;
    top: 0;
    bottom: 0;
    right: 0;
    width: min(84vw, 320px);
    z-index: 45;
    transform: translateX(102%);
    transition: transform 0.22s ease;
    border-left: 1px solid var(--sarv-green-dark);
    box-shadow: -8px 0 24px rgb(0 0 0 / 0.5);
  }

  .app-shell :deep([data-testid='right-sidebar'].drawer-open) {
    transform: none;
  }
}

@media (max-width: 640px) {
  .app-shell {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr) auto;
    height: 100dvh;
  }

  .app-center {
    min-height: 0;
  }

  /* Left sidebar becomes a left drawer on mobile instead of disappearing. */
  .app-shell :deep([data-testid='left-sidebar']) {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    width: min(84vw, 320px);
    z-index: 45;
    transform: translateX(-102%);
    transition: transform 0.22s ease;
    border-right: 1px solid var(--sarv-green-dark);
    box-shadow: 8px 0 24px rgb(0 0 0 / 0.5);
  }

  .app-shell :deep([data-testid='left-sidebar'].drawer-open) {
    transform: none;
  }

  .drawer-scrim {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 44;
    background: rgb(0 0 0 / 0.6);
  }
}

@media (prefers-reduced-motion: reduce) {
  .app-shell :deep([data-testid='left-sidebar']),
  .app-shell :deep([data-testid='right-sidebar']) {
    transition: none;
  }
}
</style>
