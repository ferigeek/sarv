<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { getMediaBlob } from '@/api/media'
import { useAuthStore } from '@/stores/auth'
import AppIcon from './AppIcon.vue'

const router = useRouter()
const auth = useAuthStore()

const avatarUrl = ref<string | null>(null)
let objectUrl: string | null = null

function clearAvatar() {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
    objectUrl = null
  }
  avatarUrl.value = null
}

watch(
  () => auth.user?.profilePictureId,
  async (id) => {
    clearAvatar()
    if (!id) return
    try {
      const blob = await getMediaBlob(id)
      objectUrl = URL.createObjectURL(blob)
      avatarUrl.value = objectUrl
    } catch {
      // Keep placeholder on error (e.g. missing media)
      avatarUrl.value = null
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  clearAvatar()
})

function goProfile() {
  if (!auth.user) return
  void router.push({ name: 'profile', params: { id: String(auth.user.id) } })
}
</script>

<template>
  <div
    class="user-summary"
    data-testid="user-summary"
    :class="{ 'user-summary--clickable': auth.user }"
    role="button"
    tabindex="0"
    @click="goProfile"
    @keydown.enter="goProfile"
  >
    <div class="user-summary__avatar" data-testid="user-summary-avatar" aria-hidden="true">
      <img
        v-if="avatarUrl"
        :src="avatarUrl"
        alt=""
        class="user-summary__img"
        data-testid="user-summary-img"
      />
      <AppIcon v-else name="user" :size="22" />
    </div>
    <div class="user-summary__text">
      <template v-if="auth.user">
        <span class="user-summary__name" data-testid="user-summary-name">{{ auth.user.displayName }}</span>
        <span class="user-summary__username" data-testid="user-summary-username">@{{ auth.user.username }}</span>
      </template>
      <span v-else class="user-summary__loading">loading…</span>
    </div>
  </div>
</template>

<style scoped>
.user-summary {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-3);
  padding: 8px;
  border: 1px solid transparent;
}

.user-summary--clickable {
  cursor: pointer;
}

.user-summary--clickable:hover {
  background: var(--sarv-panel-alt);
  border-color: var(--sarv-border);
}

.user-summary__avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  background: var(--sarv-bg);
  border: 1px solid var(--sarv-border);
  color: var(--sarv-text-dim);
  overflow: hidden;
}

.user-summary__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-summary__text {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.user-summary__name {
  font-size: 13px;
  font-weight: 700;
  color: var(--sarv-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-summary__username {
  font-size: 11px;
  color: var(--sarv-text-dim);
}

.user-summary__loading {
  font-size: 12px;
  color: var(--sarv-text-dim);
}
</style>
