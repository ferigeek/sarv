<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

import AppIcon from './AppIcon.vue'
import NavigationMenu from './NavigationMenu.vue'
import PostCreateModal from './PostCreateModal.vue'
import SarvLogo from './SarvLogo.vue'
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
</script>

<template>
  <aside class="left-sidebar" data-testid="left-sidebar">
    <div class="left-sidebar__brand">
      <SarvLogo />
      <p class="left-sidebar__tagline">cypress / matrix / linux / hacker</p>
    </div>

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
  background: var(--sarv-panel);
  padding: var(--sarv-space-5) var(--sarv-space-4);
  border-bottom: 1px solid var(--sarv-border);
  flex-shrink: 0;
}

.left-sidebar__tagline {
  margin-top: var(--sarv-space-2);
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--sarv-text-faint);
}

.left-sidebar__logout {
  margin-top: auto;
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
