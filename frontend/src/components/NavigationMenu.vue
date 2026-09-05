<script setup lang="ts">
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

import AppIcon from './AppIcon.vue'

const router = useRouter()
const auth = useAuthStore()

function goHome() {
  void router.push({ name: 'feed' })
}

function goProfile() {
  if (!auth.user) return
  void router.push({ name: 'profile', params: { id: String(auth.user.id) } })
}

function goFollowing() {
  void router.push({ name: 'following' })
}

function goFollowers() {
  void router.push({ name: 'followers' })
}

// reacted-posts history is backed by GET /users/{id}/reacted-posts
function goLiked() {
  void router.push({ name: 'liked' })
}
</script>

<template>
  <nav class="left-nav" data-testid="left-navigation" aria-label="User navigation">
    <header class="left-block__header">NAVIGATION</header>
    <ul class="left-nav__list">
      <li>
        <button
          class="left-nav__item left-nav__item--home"
          type="button"
          data-testid="left-nav-home"
          @click="goHome"
        >
          <AppIcon name="home" :size="16" class="left-nav__icon" aria-hidden="true" />
          home
        </button>
      </li>
      <li>
        <button
          class="left-nav__item"
          type="button"
          data-testid="left-nav-profile"
          @click="goProfile"
        >
          view profile
        </button>
      </li>
      <li>
        <button
          class="left-nav__item"
          type="button"
          data-testid="left-nav-liked"
          @click="goLiked"
        >
          recent reactions
        </button>
      </li>
      <li>
        <button class="left-nav__item" type="button" data-testid="left-nav-following" @click="goFollowing">
          following
        </button>
      </li>
      <li>
        <button class="left-nav__item" type="button" data-testid="left-nav-followers" @click="goFollowers">
          followers
        </button>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.left-block__header {
  font-size: 11px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--sarv-green);
  padding-bottom: var(--sarv-space-3);
  border-bottom: 1px solid var(--sarv-border);
  margin-bottom: var(--sarv-space-3);
}

.left-nav__list {
  display: grid;
  gap: 2px;
}

.left-nav__item {
  width: 100%;
  text-align: left;
  padding: 8px 8px;
  font-size: 12px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--sarv-text-dim);
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
}

.left-nav__item:hover {
  background: var(--sarv-panel-alt);
  border-color: var(--sarv-border);
  color: var(--sarv-text);
}

.left-nav__item--home {
  color: var(--sarv-text);
  font-weight: 700;
}

.left-nav__item--home:hover {
  color: #fff;
}

.left-nav__icon {
  color: var(--sarv-green);
  flex-shrink: 0;
}

.left-nav__item--muted {
  opacity: 0.9;
}
</style>
