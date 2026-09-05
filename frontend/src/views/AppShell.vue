<script setup lang="ts">
import { computed, onBeforeUnmount, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import LeftSidebar from '@/components/LeftSidebar.vue'
import MobileBottomNav from '@/components/MobileBottomNav.vue'
import MobileTopBar from '@/components/MobileTopBar.vue'
import PostCreateModal from '@/components/PostCreateModal.vue'
import RightSidebar from '@/components/RightSidebar.vue'
import SearchModal, { type SearchTab } from '@/components/SearchModal.vue'

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
const showSearch = ref(false)
const searchQuery = ref('')
const searchTab = ref<SearchTab>('username')
const leftSearchOpen = ref(false)

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

const anyDrawerOpen = computed(
  () => leftOpen.value || rightOpen.value || showCreate.value || showSearch.value || leftSearchOpen.value,
)

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
  // Standalone search: never open the left drawer behind the modal, otherwise
  // the drawer's scrollbar/border paints above the search window on mobile.
  closeDrawers()
  showCreate.value = false
  showSearch.value = true
}

function closeSearch() {
  showSearch.value = false
}

function selectSearchUser(id: number) {
  showSearch.value = false
  void router.push({ name: 'profile', params: { id: String(id) } })
}

function selectSearchPost(id: number) {
  showSearch.value = false
  void router.push({ name: 'post-detail', params: { id: String(id) } })
}

function onLeftSearchOpened() {
  // Sidebar-initiated search also ends up alone: close the drawer, the
  // teleported modal stays visible and keeps the body scroll locked.
  leftSearchOpen.value = true
  closeDrawers()
}

function onLeftSearchClosed() {
  leftSearchOpen.value = false
}

function onCreateOpened() {
  closeDrawers()
  showSearch.value = false
  showCreate.value = true
}

function onMobileNavigate(name: string) {
  closeDrawers()
  showSearch.value = false
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
    showSearch.value = false
  }
}

watch(anyDrawerOpen, (open) => {
  document.body.classList.toggle('no-scroll', open)
})

watch(
  () => route.fullPath,
  () => {
    closeDrawers()
    showSearch.value = false
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
      @search-opened="onLeftSearchOpened"
      @search-closed="onLeftSearchClosed"
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

    <SearchModal
      v-if="showSearch"
      :query="searchQuery"
      :active-tab="searchTab"
      @update:query="searchQuery = $event"
      @update:active-tab="searchTab = $event"
      @close="closeSearch"
      @select-user="selectSearchUser"
      @select-post="selectSearchPost"
    />
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
