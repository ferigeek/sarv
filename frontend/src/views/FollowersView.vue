<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { getFollowers } from '@/api/follows'
import { useAuthStore } from '@/stores/auth'
import type { UserSummaryResponse } from '@/types/api'
import UserSummaryList from '@/components/UserSummaryList.vue'

const route = useRoute()
const auth = useAuthStore()

const users = ref<UserSummaryResponse[]>([])
const loading = ref(true)
const error = ref('')
const hasMore = ref(true)
const page = ref(0)
const size = 20

const targetId = computed(() => {
  const raw = route.params.id
  if (raw === undefined || raw === '' || (Array.isArray(raw) && raw.length === 0)) {
    return auth.user?.id ?? null
  }
  return Number(raw)
})

async function fetchPage(pageNum: number, append: boolean) {
  const id = targetId.value
  if (id === null) return
  loading.value = true
  error.value = ''
  try {
    const data = await getFollowers(id, { page: pageNum, size })
    users.value = append ? [...users.value, ...data.content] : data.content
    hasMore.value = data.page.number + 1 < data.page.totalPages
    if (data.content.length === 0) hasMore.value = false
    page.value = pageNum
  } catch (e) {
    const msg = (e as { detail?: string })?.detail ?? (e instanceof Error ? e.message : 'Failed to load followers.')
    error.value = msg
    if (!append) users.value = []
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  void fetchPage(page.value + 1, true)
}

watch(targetId, () => {
  void fetchPage(0, false)
})

onMounted(() => {
  void fetchPage(0, false)
})
</script>

<template>
  <section class="users-view" data-testid="followers-view">
    <header class="users-view__header">
      <span class="users-view__title">FOLLOWERS</span>
      <span class="users-view__meta">SYS.LIST</span>
    </header>

    <div v-if="loading && users.length === 0" class="users-view__state" data-testid="followers-loading">
      loading followers…
    </div>

    <div v-else-if="error && users.length === 0" class="users-view__state users-view__state--error" data-testid="followers-error">
      {{ error }}
      <button class="btn" type="button" data-testid="followers-retry" @click="fetchPage(0, false)">retry</button>
    </div>

    <div v-else-if="users.length === 0" class="users-view__state" data-testid="followers-empty">
      no followers yet
    </div>

    <template v-else>
      <UserSummaryList :users="users" />

      <footer class="users-view__footer">
        <div v-if="error" class="users-view__state users-view__state--error" data-testid="followers-error-more">{{ error }}</div>
        <button
          v-if="hasMore"
          class="btn"
          type="button"
          data-testid="followers-load-more"
          :disabled="loading"
          @click="loadMore"
        >
          {{ loading ? 'loading…' : 'load more' }}
        </button>
        <span v-else class="users-view__end" data-testid="followers-end">— end of list —</span>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.users-view {
  min-height: 100%;
  display: grid;
  gap: 1px;
  background: var(--sarv-border);
  align-content: start;
}

.users-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sarv-space-3) var(--sarv-space-4);
  background: var(--sarv-panel);
}

.users-view__title {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
}

.users-view__meta {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--sarv-text-faint);
}

.users-view__state {
  padding: var(--sarv-space-6);
  text-align: center;
  font-size: 12px;
  color: var(--sarv-text-dim);
  background: var(--sarv-panel);
}

.users-view__state--error {
  color: #ff8fa3;
  display: grid;
  gap: var(--sarv-space-3);
  justify-items: center;
}

.users-view__footer {
  display: grid;
  gap: var(--sarv-space-3);
  padding: var(--sarv-space-4);
  background: var(--sarv-panel);
  justify-items: center;
}

.users-view__end {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--sarv-text-faint);
}
</style>