<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

import { getReactedPosts } from '@/api/users'
import { useAuthStore } from '@/stores/auth'
import type { PostResponse, ReactionFilter } from '@/types/api'
import PostCard from '@/components/PostCard.vue'

type Tab = 'liked' | 'disliked' | 'all'

const auth = useAuthStore()

const posts = ref<PostResponse[]>([])
const loading = ref(true)
const error = ref('')
const hasMore = ref(true)
const page = ref(0)
const size = 20
const activeTab = ref<Tab>('liked')

function filterFor(tab: Tab): ReactionFilter {
  if (tab === 'liked') return 'LIKE'
  if (tab === 'disliked') return 'DISLIKE'
  return 'ALL'
}

function emptyLabel(tab: Tab): string {
  if (tab === 'liked') return 'no liked posts yet'
  if (tab === 'disliked') return 'no disliked posts yet'
  return 'no reactions yet'
}

async function fetchPage(pageNum: number, append: boolean) {
  if (!auth.user) return
  if (loading.value && append) return
  loading.value = true
  error.value = ''
  try {
    const data = await getReactedPosts(auth.user.id, filterFor(activeTab.value), {
      page: pageNum,
      size,
    })
    posts.value = append ? [...posts.value, ...data.content] : data.content
    hasMore.value = data.page.number + 1 < data.page.totalPages
    if (data.content.length === 0) hasMore.value = false
    page.value = pageNum
  } catch (e) {
    const msg = (e as { detail?: string })?.detail ?? (e instanceof Error ? e.message : 'Failed to load posts.')
    error.value = msg
    if (!append) posts.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(tab: Tab) {
  if (tab === activeTab.value) return
  activeTab.value = tab
  void fetchPage(0, false)
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  void fetchPage(page.value + 1, true)
}

watch(
  () => auth.user?.id,
  () => {
    void fetchPage(0, false)
  },
)

onMounted(() => {
  void fetchPage(0, false)
})
</script>

<template>
  <section class="liked-view" data-testid="liked-view">
    <header class="liked-view__header">
      <span class="liked-view__title">REACTED POSTS</span>
      <span class="liked-view__meta">SYS.HISTORY</span>
    </header>

    <nav class="liked-tabs" role="tablist" aria-label="Reaction filter" data-testid="liked-tabs">
      <button
        class="liked-tab"
        :class="{ 'liked-tab--active': activeTab === 'liked' }"
        role="tab"
        :aria-selected="activeTab === 'liked'"
        type="button"
        data-testid="liked-tab-liked"
        @click="switchTab('liked')"
      >
        liked
      </button>
      <button
        class="liked-tab"
        :class="{ 'liked-tab--active': activeTab === 'disliked' }"
        role="tab"
        :aria-selected="activeTab === 'disliked'"
        type="button"
        data-testid="liked-tab-disliked"
        @click="switchTab('disliked')"
      >
        disliked
      </button>
      <button
        class="liked-tab"
        :class="{ 'liked-tab--active': activeTab === 'all' }"
        role="tab"
        :aria-selected="activeTab === 'all'"
        type="button"
        data-testid="liked-tab-all"
        @click="switchTab('all')"
      >
        all
      </button>
    </nav>

    <div v-if="loading && posts.length === 0" class="liked-view__state" data-testid="liked-loading">
      loading posts…
    </div>

    <div
      v-else-if="error && posts.length === 0"
      class="liked-view__state liked-view__state--error"
      data-testid="liked-error"
    >
      {{ error }}
      <button class="btn" type="button" data-testid="liked-retry" @click="fetchPage(0, false)">retry</button>
    </div>

    <div v-else-if="posts.length === 0" class="liked-view__state" data-testid="liked-empty">
      {{ emptyLabel(activeTab) }}
    </div>

    <template v-else>
      <div class="liked-view__list" data-testid="liked-list">
        <PostCard v-for="p in posts" :key="p.id" :post="p" />
      </div>

      <footer class="liked-view__footer">
        <div v-if="error" class="liked-view__state liked-view__state--error" data-testid="liked-error-more">
          {{ error }}
        </div>
        <button
          v-if="hasMore"
          class="btn"
          type="button"
          data-testid="liked-load-more"
          :disabled="loading"
          @click="loadMore"
        >
          {{ loading ? 'loading…' : 'load more' }}
        </button>
        <span v-else class="liked-view__end" data-testid="liked-end">— end of list —</span>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.liked-view {
  min-height: 100%;
  display: grid;
  gap: 1px;
  background: var(--sarv-border);
  align-content: start;
}

.liked-view__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sarv-space-3) var(--sarv-space-4);
  background: var(--sarv-panel);
}

.liked-view__title {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
}

.liked-view__meta {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--sarv-text-faint);
}

.liked-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--sarv-border);
  position: sticky;
  top: 0;
  z-index: 2;
}

.liked-tab {
  padding: var(--sarv-space-3) var(--sarv-space-4);
  background: var(--sarv-panel);
  border: 1px solid transparent;
  border-bottom-color: var(--sarv-border);
  color: var(--sarv-text-dim);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  cursor: pointer;
}

.liked-tab:hover {
  background: var(--sarv-panel-alt);
  color: var(--sarv-text);
}

.liked-tab--active {
  background: var(--sarv-panel-alt);
  color: var(--sarv-green);
  border-bottom-color: var(--sarv-green);
  box-shadow: inset 0 -1px 0 var(--sarv-green), var(--sarv-glow);
}

.liked-view__state {
  padding: var(--sarv-space-6);
  text-align: center;
  font-size: 12px;
  color: var(--sarv-text-dim);
  background: var(--sarv-panel);
  display: grid;
  gap: var(--sarv-space-3);
  justify-items: center;
}

.liked-view__state--error {
  color: #ff8fa3;
}

.liked-view__list {
  display: grid;
  gap: 1px;
}

.liked-view__footer {
  display: grid;
  gap: var(--sarv-space-3);
  padding: var(--sarv-space-4);
  background: var(--sarv-panel);
  justify-items: center;
}

.liked-view__end {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--sarv-text-faint);
}
</style>
