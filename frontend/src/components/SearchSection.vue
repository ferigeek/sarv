<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { searchUsers } from '@/api/users'
import type { UserSummaryResponse } from '@/types/api'
import AppIcon from './AppIcon.vue'

type Tab = 'general' | 'username' | 'post'

const router = useRouter()

const query = ref('')
const activeTab = ref<Tab>('username')
const results = ref<UserSummaryResponse[]>([])
const loading = ref(false)
const error = ref('')
const showPanel = ref(false)

let debounce: ReturnType<typeof setTimeout> | null = null

function onFocus() {
  showPanel.value = true
}

function onBlur() {
  // Delay hide to allow clicking results
  setTimeout(() => {
    showPanel.value = false
  }, 150)
}

watch([query, activeTab], () => {
  error.value = ''
  if (debounce) clearTimeout(debounce)

  if (activeTab.value !== 'username') {
    results.value = []
    loading.value = false
    return
  }

  const q = query.value.trim()
  if (!q) {
    results.value = []
    loading.value = false
    return
  }

  loading.value = true
  debounce = setTimeout(async () => {
    try {
      const page = await searchUsers(q, { size: 8 })
      results.value = page.content
      error.value = ''
    } catch (e) {
      results.value = []
      error.value = e instanceof Error ? e.message : 'Search failed.'
      // ApiError has detail
      const detail = (e as { detail?: string })?.detail
      if (detail) error.value = detail
    } finally {
      loading.value = false
    }
  }, 300)
})

function selectUser(user: UserSummaryResponse) {
  showPanel.value = false
  query.value = ''
  void router.push({ name: 'profile', params: { id: String(user.id) } })
}

function onTab(tab: Tab) {
  activeTab.value = tab
  showPanel.value = true
}
</script>

<template>
  <div class="search-section" data-testid="search-section">
    <div class="search-input-wrap">
      <AppIcon name="search" :size="18" class="search-icon" />
      <input
        v-model="query"
        class="search-input"
        type="text"
        placeholder="SEARCH //"
        data-testid="search-input"
        @focus="onFocus"
        @blur="onBlur"
      />
    </div>

    <div class="search-tabs" role="tablist" aria-label="Search tabs">
      <button
        role="tab"
        :aria-selected="activeTab === 'general'"
        class="search-tab"
        :class="{ 'search-tab--active': activeTab === 'general' }"
        data-testid="search-tab-general"
        type="button"
        @click="onTab('general')"
      >
        general
      </button>
      <button
        role="tab"
        :aria-selected="activeTab === 'username'"
        class="search-tab"
        :class="{ 'search-tab--active': activeTab === 'username' }"
        data-testid="search-tab-username"
        type="button"
        @click="onTab('username')"
      >
        username
      </button>
      <button
        role="tab"
        :aria-selected="activeTab === 'post'"
        class="search-tab"
        :class="{ 'search-tab--active': activeTab === 'post' }"
        data-testid="search-tab-post"
        type="button"
        @click="onTab('post')"
      >
        post
      </button>
    </div>

    <div
      v-if="showPanel && (query.trim() || activeTab !== 'username')"
      class="search-panel panel"
      data-testid="search-panel"
    >
      <template v-if="activeTab === 'username'">
        <div v-if="loading" class="search-panel__state" data-testid="search-loading">searching…</div>
        <div v-else-if="error" class="search-panel__state search-panel__state--error" data-testid="search-error">
          {{ error }}
        </div>
        <div
          v-else-if="!query.trim()"
          class="search-panel__state"
          data-testid="search-empty"
        >
          type to search users
        </div>
        <div
          v-else-if="results.length === 0"
          class="search-panel__state"
          data-testid="search-no-results"
        >
          no users found
        </div>
        <ul v-else class="search-results">
          <li
            v-for="u in results"
            :key="u.id"
            class="search-result"
            :data-testid="`search-result-${u.id}`"
            @mousedown.prevent="selectUser(u)"
          >
            <span class="search-result__avatar" aria-hidden="true">
              <AppIcon name="user" :size="18" />
            </span>
            <span class="search-result__text">
              <span class="search-result__name">{{ u.displayName }}</span>
              <span class="search-result__username">@{{ u.username }}</span>
            </span>
          </li>
        </ul>
      </template>

      <template v-else>
        <div class="search-panel__state" data-testid="search-coming-soon">
          {{ activeTab === 'general' ? 'general search' : 'post search' }} — coming soon
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.search-section {
  position: relative;
  display: grid;
  gap: var(--sarv-space-2);
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-2);
  padding: 6px 8px;
  background: var(--sarv-bg);
  border: 1px solid var(--sarv-border-bright);
}

.search-input-wrap:focus-within {
  border-color: var(--sarv-green-dim);
  box-shadow: 0 0 0 1px var(--sarv-green-dim);
}

.search-icon {
  color: var(--sarv-text-dim);
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  color: var(--sarv-text);
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.search-input::placeholder {
  color: var(--sarv-text-faint);
}

.search-tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--sarv-border);
  border: 1px solid var(--sarv-border);
}

.search-tab {
  padding: 6px 4px;
  font-size: 10px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  background: var(--sarv-panel);
  border: none;
  color: var(--sarv-text-dim);
  cursor: pointer;
}

.search-tab:hover {
  background: var(--sarv-panel-alt);
  color: var(--sarv-text);
}

.search-tab--active {
  background: var(--sarv-green-faint);
  color: var(--sarv-green);
  border-bottom: 1px solid var(--sarv-green-dim);
}

.search-panel {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 20;
  max-height: 260px;
  overflow-y: auto;
  background: var(--sarv-panel);
  border-color: var(--sarv-green-dark);
  box-shadow: var(--sarv-glow);
  padding: var(--sarv-space-2);
}

.search-panel__state {
  padding: var(--sarv-space-3);
  font-size: 12px;
  color: var(--sarv-text-dim);
  text-align: center;
}

.search-panel__state--error {
  color: #ff8fa3;
}

.search-results {
  display: grid;
  gap: 1px;
}

.search-result {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-3);
  padding: 8px 8px;
  border: 1px solid transparent;
  cursor: pointer;
}

.search-result:hover {
  background: var(--sarv-panel-alt);
  border-color: var(--sarv-border);
}

.search-result__avatar {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  background: var(--sarv-bg);
  border: 1px solid var(--sarv-border);
  color: var(--sarv-text-dim);
  flex-shrink: 0;
}

.search-result__text {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.search-result__name {
  font-size: 12px;
  color: var(--sarv-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.search-result__username {
  font-size: 11px;
  color: var(--sarv-text-dim);
}

@media (max-width: 640px) {
  .search-panel {
    max-height: 50dvh;
  }

  .search-result {
    min-height: 44px;
  }
}
</style>
