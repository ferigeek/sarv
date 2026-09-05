<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { follow, getFollowing, unfollow } from '@/api/follows'
import { getMediaBlob } from '@/api/media'
import { useAuthStore } from '@/stores/auth'
import type { UserSummaryResponse } from '@/types/api'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ users: UserSummaryResponse[] }>()

const router = useRouter()
const auth = useAuthStore()

/* User ids the current user follows, so each row shows the right action. There
   is no isFollowing field on the API, so it is derived from the authenticated
   user's following list. */
const followingIds = ref<number[]>([])
const busyId = ref<number | null>(null)

const avatarUrls = ref<Record<number, string | null>>({})
const objectUrls = new Map<number, string>()

const canManage = computed(() => Boolean(auth.user))
const isSelf = (id: number) => Boolean(auth.user && auth.user.id === id)
const isFollowing = (id: number) => followingIds.value.includes(id)

function clearAvatars() {
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
  objectUrls.clear()
  avatarUrls.value = {}
}

async function loadAvatars() {
  clearAvatars()
  for (const u of props.users) {
    if (!u.profilePictureId) continue
    try {
      const blob = await getMediaBlob(u.profilePictureId)
      const url = URL.createObjectURL(blob)
      objectUrls.set(u.id, url)
      avatarUrls.value = { ...avatarUrls.value, [u.id]: url }
    } catch {
      avatarUrls.value = { ...avatarUrls.value, [u.id]: null }
    }
  }
}

async function loadFollowingIds() {
  if (!auth.user) {
    followingIds.value = []
    return
  }
  try {
    const page = await getFollowing(auth.user.id, { page: 0, size: 100 })
    followingIds.value = page.content.map((u) => u.id)
  } catch {
    followingIds.value = []
  }
}

async function toggleFollow(id: number) {
  if (busyId.value !== null) return
  busyId.value = id
  try {
    if (isFollowing(id)) {
      await unfollow(id)
    } else {
      await follow(id)
    }
    await loadFollowingIds()
  } finally {
    busyId.value = null
  }
}

function openProfile(id: number) {
  void router.push({ name: 'profile', params: { id: String(id) } })
}

watch(
  () => props.users,
  () => {
    void loadAvatars()
  },
  { immediate: true },
)

onMounted(() => {
  void loadFollowingIds()
})

onBeforeUnmount(() => {
  clearAvatars()
})
</script>

<template>
  <div class="user-summary-list" data-testid="user-summary-list">
    <div v-for="u in users" :key="u.id" class="user-row panel" :data-testid="`user-row-${u.id}`">
      <button
        class="user-row__identity"
        type="button"
        :data-testid="`user-profile-${u.id}`"
        :aria-label="`View profile of ${u.displayName}`"
        @click="openProfile(u.id)"
      >
        <span class="user-row__avatar" aria-hidden="true">
          <img v-if="avatarUrls[u.id]" :src="avatarUrls[u.id] ?? undefined" alt="" class="user-row__img" />
          <AppIcon v-else name="user" :size="20" />
        </span>

        <span class="user-row__text">
          <span class="user-row__name">{{ u.displayName }}</span>
          <span class="user-row__username">@{{ u.username }}</span>
        </span>
      </button>

      <div class="user-row__actions">
        <button
          v-if="canManage && !isSelf(u.id)"
          class="btn"
          type="button"
          :class="{ 'btn-primary': !isFollowing(u.id) }"
          :disabled="busyId === u.id"
          :data-testid="`user-follow-${u.id}`"
          @click="toggleFollow(u.id)"
        >
          {{ busyId === u.id ? '…' : isFollowing(u.id) ? 'unfollow' : 'follow' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-summary-list {
  display: grid;
  gap: 1px;
}

.user-row {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-3);
  padding: var(--sarv-space-3) var(--sarv-space-4);
}

.user-row__identity {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-3);
  min-width: 0;
  flex: 1;
  padding: 0;
  background: transparent;
  border: none;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.user-row__identity:hover .user-row__name {
  text-decoration: underline;
  text-decoration-color: var(--sarv-green-dim);
  text-underline-offset: 2px;
}

.user-row__identity:focus-visible {
  outline: 1px solid var(--sarv-green);
  outline-offset: 2px;
}

.user-row__avatar {
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

.user-row__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-row__text {
  display: grid;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.user-row__name {
  font-size: 13px;
  font-weight: 700;
  color: var(--sarv-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-row__username {
  font-size: 11px;
  color: var(--sarv-text-dim);
}

.user-row__actions {
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}
</style>