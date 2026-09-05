<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import AppIcon from './AppIcon.vue'
import SearchModal, { type SearchTab } from './SearchModal.vue'

const router = useRouter()

const query = ref('')
const activeTab = ref<SearchTab>('username')
const showModal = ref(false)

function openModal(tab?: SearchTab) {
  if (tab) activeTab.value = tab
  showModal.value = true
}

function onFocus() {
  openModal()
}

function onTab(tab: SearchTab) {
  openModal(tab)
}

function selectUser(id: number) {
  showModal.value = false
  void router.push({ name: 'profile', params: { id: String(id) } })
}

function selectPost(id: number) {
  showModal.value = false
  void router.push({ name: 'post-detail', params: { id: String(id) } })
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

    <SearchModal
      v-if="showModal"
      :query="query"
      :active-tab="activeTab"
      @update:query="query = $event"
      @update:active-tab="activeTab = $event"
      @close="showModal = false"
      @select-user="selectUser"
      @select-post="selectPost"
    />
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
</style>
