<script setup lang="ts">
import { inject, onMounted, ref, watch } from 'vue'

import { getChronologicalFeed, getRecommendedFeed } from '@/api/feed'
import type { PostResponse } from '@/types/api'
import PostCard from '@/components/PostCard.vue'

type FeedTab = 'forYou' | 'latest'

const posts = ref<PostResponse[]>([])
const page = ref(0)
const size = 20
const loading = ref(false)
const error = ref('')
const hasMore = ref(true)
const initialLoading = ref(true)
const activeTab = ref<FeedTab>('forYou')

let fetchSeq = 0

const refreshKey = inject<import('vue').Ref<number>>('feedRefreshKey', ref(0))

watch(refreshKey, () => {
  void fetchPage(0, false)
})

watch(activeTab, () => {
  void fetchPage(0, false)
})

function switchTab(tab: FeedTab) {
  if (tab === activeTab.value) return
  activeTab.value = tab
}

async function fetchPage(pageNum: number, append: boolean) {
  // Allow tab switches / retries (non-append) to interrupt current load via seq check.
  if (loading.value && append) return
  const seq = ++fetchSeq
  loading.value = true
  error.value = ''
  if (!append) initialLoading.value = true

  try {
    let data

    if (activeTab.value === 'latest') {
      data = await getChronologicalFeed({ page: pageNum, size })
    } else {
      // For You → recommended with fallback to chronological
      try {
        data = await getRecommendedFeed({ page: pageNum, size })
        // Fallback if recommended is empty on first page and no error
        if (data.content.length === 0 && pageNum === 0) {
          const chrono = await getChronologicalFeed({ page: pageNum, size })
          if (chrono.content.length > 0) {
            data = chrono
          }
        }
      } catch {
        // Recommended failed → fallback to chronological
        data = await getChronologicalFeed({ page: pageNum, size })
      }
    }

    // Ignore stale responses if a newer fetch started (e.g. rapid tab switch)
    if (seq !== fetchSeq) return

    if (append) {
      posts.value = [...posts.value, ...data.content]
    } else {
      posts.value = data.content
    }

    // hasMore from page metadata; content length check would break mocked pages where size > content
    hasMore.value = data.page.number + 1 < data.page.totalPages
    if (data.content.length === 0) hasMore.value = false

    page.value = pageNum
  } catch (e) {
    if (seq !== fetchSeq) return
    const msg = (e as { detail?: string })?.detail ?? (e instanceof Error ? e.message : 'Failed to load feed.')
    error.value = msg
    if (!append) posts.value = []
  } finally {
    if (seq === fetchSeq) {
      loading.value = false
      initialLoading.value = false
    }
  }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  void fetchPage(page.value + 1, true)
}

function onReposted() {
  void fetchPage(0, false)
}

onMounted(() => {
  void fetchPage(0, false)
})
</script>

<template>
  <section class="feed-view" data-testid="feed-view">
    <header class="feed-header">
      <span class="feed-header__title">FEED // {{ activeTab === 'forYou' ? 'FOR YOU' : 'LATEST' }}</span>
      <span class="feed-header__meta">SYS.FEED</span>
    </header>

    <nav class="feed-tabs" role="tablist" aria-label="Feed type" data-testid="feed-tabs">
      <button
        class="feed-tab"
        :class="{ 'feed-tab--active': activeTab === 'forYou' }"
        role="tab"
        :aria-selected="activeTab === 'forYou'"
        type="button"
        data-testid="feed-tab-for-you"
        @click="switchTab('forYou')"
      >
        <span class="feed-tab__label">For You</span>
        <span class="feed-tab__indicator" aria-hidden="true"></span>
      </button>
      <button
        class="feed-tab"
        :class="{ 'feed-tab--active': activeTab === 'latest' }"
        role="tab"
        :aria-selected="activeTab === 'latest'"
        type="button"
        data-testid="feed-tab-latest"
        @click="switchTab('latest')"
      >
        <span class="feed-tab__label">Latest</span>
        <span class="feed-tab__indicator" aria-hidden="true"></span>
      </button>
    </nav>

    <div v-if="initialLoading" class="feed-state" data-testid="feed-loading">loading feed…</div>

    <div v-else-if="error && posts.length === 0" class="feed-state feed-state--error" data-testid="feed-error">
      {{ error }}
      <button class="btn feed-retry" type="button" data-testid="feed-retry" @click="fetchPage(0, false)">retry</button>
    </div>

    <div v-else-if="posts.length === 0" class="feed-state" data-testid="feed-empty">no posts yet — be the first to post</div>

    <template v-else>
      <div class="feed-list" data-testid="feed-list">
        <PostCard
          v-for="p in posts"
          :key="p.id"
          :post="p"
          @reposted="onReposted"
          @quoted="onReposted"
        />
      </div>

      <footer class="feed-footer">
        <div v-if="error" class="feed-state feed-state--error" data-testid="feed-error-more">{{ error }}</div>
        <button
          v-if="hasMore"
          class="btn feed-load-more"
          type="button"
          data-testid="feed-load-more"
          :disabled="loading"
          @click="loadMore"
        >
          {{ loading ? 'loading…' : 'load more' }}
        </button>
        <span v-else class="feed-end" data-testid="feed-end">— end of feed —</span>
      </footer>
    </template>
  </section>
</template>

<style scoped>
.feed-view {
  min-height: 100%;
  display: grid;
  gap: 1px;
  background: var(--sarv-border);
  align-content: start;
}

.feed-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sarv-space-3) var(--sarv-space-4);
  background: var(--sarv-panel);
}

.feed-header__title {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
}

.feed-header__meta {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--sarv-text-faint);
}

.feed-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: var(--sarv-border);
  position: sticky;
  top: 0;
  z-index: 2;
}

.feed-tab {
  position: relative;
  display: grid;
  place-items: center;
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
  transition:
    background 140ms ease,
    color 140ms ease,
    border-color 140ms ease,
    box-shadow 140ms ease;
}

.feed-tab:hover {
  background: var(--sarv-panel-alt);
  color: var(--sarv-text);
  border-bottom-color: var(--sarv-border-bright);
}

.feed-tab--active {
  background: var(--sarv-panel-alt);
  color: var(--sarv-green);
  border-bottom-color: var(--sarv-green);
  box-shadow: inset 0 -1px 0 var(--sarv-green), var(--sarv-glow);
}

.feed-tab--active .feed-tab__indicator {
  opacity: 1;
  transform: scaleX(1);
}

.feed-tab__label {
  position: relative;
}

.feed-tab__indicator {
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--sarv-green);
  opacity: 0;
  transform: scaleX(0.6);
  transition:
    opacity 160ms ease,
    transform 160ms ease;
}

.feed-tab:focus-visible {
  outline-offset: -2px;
}

.feed-state {
  padding: var(--sarv-space-6);
  text-align: center;
  font-size: 12px;
  color: var(--sarv-text-dim);
  background: var(--sarv-panel);
}

.feed-state--error {
  color: #ff8fa3;
  display: grid;
  gap: var(--sarv-space-3);
  justify-items: center;
}

.feed-retry {
  margin-top: var(--sarv-space-2);
}

.feed-list {
  display: grid;
  gap: 1px;
}

.feed-footer {
  display: grid;
  gap: var(--sarv-space-3);
  padding: var(--sarv-space-4);
  background: var(--sarv-panel);
  justify-items: center;
}

.feed-end {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--sarv-text-faint);
}

.feed-load-more {
  min-width: 160px;
  justify-content: center;
}

@media (max-width: 640px) {
  .feed-header {
    padding: var(--sarv-space-2) var(--sarv-space-3);
  }

  .feed-tabs {
    /* The mobile top bar is a separate grid row above the scroll container,
       not an overlay — so the tabs must pin flush to the scrollport top. */
    top: 0;
  }

  .feed-tab {
    padding: var(--sarv-space-2) var(--sarv-space-3);
  }

  .feed-footer {
    padding: var(--sarv-space-3);
  }

  .feed-load-more {
    width: 100%;
    min-height: 48px;
  }
}
</style>
