<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

import AmbientNetwork from './AmbientNetwork.vue'
import AppIcon from './AppIcon.vue'
import NavigationMenu from './NavigationMenu.vue'
import PostCreateModal from './PostCreateModal.vue'
import SarvLogo from './SarvLogo.vue'
import SarvMark from './SarvMark.vue'
import SearchSection from './SearchSection.vue'
import UserSummary from './UserSummary.vue'

const showCreate = ref(false)
const emit = defineEmits<{ created: [id: number] }>()

const auth = useAuthStore()
const router = useRouter()

function onLogout() {
  auth.logout()
  void router.push({ name: 'login' })
}

function goHome() {
  void router.push({ name: 'feed' })
}
</script>

<template>
  <aside class="left-sidebar" data-testid="left-sidebar">
    <button
      class="left-sidebar__brand"
      type="button"
      data-testid="left-brand-home"
      aria-label="Sarv — go to home feed"
      @click="goHome"
    >
      <span class="left-sidebar__brand-row">
        <SarvMark :size="32" />
        <SarvLogo />
      </span>
      <span class="left-sidebar__tagline">A twitter like social media platform by ferigeek</span>
    </button>

    <section class="panel left-block" data-testid="left-search">
      <SearchSection />
    </section>

    <section class="panel left-block" data-testid="left-user-summary">
      <UserSummary />
    </section>

    <section class="panel left-block" data-testid="left-create-post">
      <button
        class="btn btn-primary left-create-btn"
        type="button"
        data-testid="left-create-post-btn"
        @click="showCreate = true"
      >
        + create post
      </button>
    </section>

    <section class="panel left-block">
      <NavigationMenu />
    </section>

    <div class="left-ambient-wrap" data-testid="left-ambient">
      <AmbientNetwork />
    </div>

    <div class="left-sidebar__logout">
      <button
        class="btn left-sidebar__logout-btn"
        type="button"
        data-testid="left-logout"
        @click="onLogout"
      >
        <AppIcon name="logout" :size="16" />
        log out
      </button>
    </div>

    <PostCreateModal v-if="showCreate" @close="showCreate = false" @created="(id) => emit('created', id)" />
  </aside>
</template>

<style scoped>
.left-sidebar {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-height: 0;
  overflow-y: auto;
  background: var(--sarv-border);
}

.left-block {
  padding: var(--sarv-space-4);
  flex-shrink: 0;
}

.left-create-btn {
  width: 100%;
  justify-content: center;
}

.left-sidebar__brand {
  display: block;
  width: 100%;
  text-align: left;
  background: var(--sarv-panel);
  padding: var(--sarv-space-5) var(--sarv-space-4);
  border: none;
  border-bottom: 1px solid var(--sarv-border);
  flex-shrink: 0;
  font: inherit;
  color: inherit;
  cursor: pointer;
}

.left-sidebar__brand:hover {
  background: var(--sarv-panel-alt);
}

.left-sidebar__brand:focus-visible {
  outline: 1px solid var(--sarv-green);
  outline-offset: -1px;
}

.left-sidebar__brand-row {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-3);
}

.left-sidebar__tagline {
  display: block;
  margin-top: var(--sarv-space-2);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--sarv-text-faint);
}

.left-ambient-wrap {
  flex: 1 1 auto;
  min-height: 160px;
  display: flex;
  background: var(--sarv-panel);
  border-top: 1px solid var(--sarv-border);
  border-bottom: 1px solid var(--sarv-border);
  overflow: hidden;
  flex-shrink: 0;
}

/* The ambient canvas only lives in the mobile drawer. On tablet/desktop the
   left sidebar is a plain column and the animation stays right-side only. */
@media (min-width: 641px) {
  .left-ambient-wrap {
    display: none;
  }
}

.left-sidebar__logout {
  background: var(--sarv-panel);
  padding: var(--sarv-space-3) var(--sarv-space-4);
  border-top: 1px solid var(--sarv-border);
  flex-shrink: 0;
}

.left-sidebar__logout-btn {
  width: 100%;
  justify-content: center;
}
</style>
