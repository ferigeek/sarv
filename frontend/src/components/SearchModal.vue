<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import gsap from 'gsap'

import { getMediaBlob } from '@/api/media'
import { searchPosts } from '@/api/posts'
import { searchUsers } from '@/api/users'
import type { PostResponse, UserSummaryResponse } from '@/types/api'
import AppIcon from './AppIcon.vue'

export type SearchTab = 'general' | 'username' | 'post'

const props = defineProps<{ query: string; activeTab: SearchTab }>()

const emit = defineEmits<{
  'update:query': [value: string]
  'update:activeTab': [tab: SearchTab]
  close: []
  selectUser: [id: number]
  selectPost: [id: number]
}>()

const panelRef = ref<HTMLElement | null>(null)
const inputRef = ref<HTMLInputElement | null>(null)

const userResults = ref<UserSummaryResponse[]>([])
const postResults = ref<PostResponse[]>([])
const loading = ref(false)
const error = ref('')

const avatarUrls = ref<Record<number, string | null>>({})
const objectUrls = new Map<number, string>()

let debounce: ReturnType<typeof setTimeout> | null = null
let requestSeq = 0

const trimmed = computed(() => props.query.trim())
const showUsers = computed(() => props.activeTab === 'username' || props.activeTab === 'general')
const showPosts = computed(() => props.activeTab === 'post' || props.activeTab === 'general')

function clearAvatars() {
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
  objectUrls.clear()
  avatarUrls.value = {}
}

async function loadAvatars(users: UserSummaryResponse[]) {
  clearAvatars()
  for (const u of users) {
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

function postSnippet(p: PostResponse): string {
  if (p.content) return p.content.length > 140 ? `${p.content.slice(0, 140)}…` : p.content
  if (p.postCategory === 'REPOST') return '↻ repost'
  if (p.postCategory === 'QUOTE') return '❝ quote'
  return '(no text)'
}

function postTime(p: PostResponse): string {
  try {
    return new Date(p.createdAt).toLocaleString()
  } catch {
    return p.createdAt
  }
}

async function runSearch() {
  const q = props.query.trim()
  if (!q) {
    userResults.value = []
    postResults.value = []
    loading.value = false
    error.value = ''
    return
  }
  const seq = ++requestSeq
  loading.value = true
  error.value = ''
  try {
    const tasks: Promise<unknown>[] = []
    if (showUsers.value) {
      tasks.push(
        searchUsers(q, { size: props.activeTab === 'general' ? 5 : 8 }).then((page) => {
          if (seq !== requestSeq) return
          userResults.value = page.content
          void loadAvatars(page.content)
        }),
      )
    } else {
      userResults.value = []
    }
    if (showPosts.value) {
      tasks.push(
        searchPosts(q, { page: 0, size: props.activeTab === 'general' ? 5 : 8 }).then((page) => {
          if (seq !== requestSeq) return
          postResults.value = page.content
        }),
      )
    } else {
      postResults.value = []
    }
    await Promise.all(tasks)
    if (seq !== requestSeq) return
    error.value = ''
  } catch (e) {
    if (seq !== requestSeq) return
    userResults.value = []
    postResults.value = []
    const detail = (e as { detail?: string })?.detail
    error.value = detail ?? (e instanceof Error ? e.message : 'Search failed.')
  } finally {
    if (seq === requestSeq) loading.value = false
  }
}

function scheduleSearch() {
  error.value = ''
  if (debounce) clearTimeout(debounce)
  const q = props.query.trim()
  if (!q) {
    userResults.value = []
    postResults.value = []
    loading.value = false
    return
  }
  loading.value = true
  debounce = setTimeout(() => {
    void runSearch()
  }, 300)
}

watch([() => props.query, () => props.activeTab], scheduleSearch)

function onInput(e: Event) {
  emit('update:query', (e.target as HTMLInputElement).value)
}

function onTab(tab: SearchTab) {
  emit('update:activeTab', tab)
}

function onOverlayClick(e: MouseEvent) {
  if (e.target === e.currentTarget) emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => {
  if (typeof window !== 'undefined') window.addEventListener('keydown', onKeydown)
  if (panelRef.value) {
    gsap.fromTo(
      panelRef.value,
      { opacity: 0, y: 10, scale: 0.98 },
      { opacity: 1, y: 0, scale: 1, duration: 0.22, ease: 'power2.out' },
    )
  }
  void nextTick(() => inputRef.value?.focus())
  scheduleSearch()
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('keydown', onKeydown)
  if (debounce) clearTimeout(debounce)
  requestSeq += 1
  clearAvatars()
})
</script>

<template>
  <div class="search-modal-overlay" data-testid="search-modal" @click="onOverlayClick">
    <section ref="panelRef" class="panel search-modal" role="dialog" aria-modal="true" aria-label="Search">
      <header class="search-modal__header">
        <span class="search-modal__title">SEARCH //</span>
        <button
          class="btn search-modal__close"
          type="button"
          data-testid="search-modal-close"
          aria-label="Close search"
          @click="$emit('close')"
        >
          ✕
        </button>
      </header>

      <div class="search-modal__input-wrap">
        <AppIcon name="search" :size="18" class="search-modal__icon" />
        <input
          ref="inputRef"
          :value="query"
          class="search-modal__input"
          type="text"
          placeholder="SEARCH //"
          data-testid="search-modal-input"
          @input="onInput"
        />
      </div>

      <div class="search-modal__tabs" role="tablist" aria-label="Search tabs">
        <button
          role="tab"
          :aria-selected="activeTab === 'general'"
          class="search-modal__tab"
          :class="{ 'search-modal__tab--active': activeTab === 'general' }"
          data-testid="search-modal-tab-general"
          type="button"
          @click="onTab('general')"
        >
          general
        </button>
        <button
          role="tab"
          :aria-selected="activeTab === 'username'"
          class="search-modal__tab"
          :class="{ 'search-modal__tab--active': activeTab === 'username' }"
          data-testid="search-modal-tab-username"
          type="button"
          @click="onTab('username')"
        >
          username
        </button>
        <button
          role="tab"
          :aria-selected="activeTab === 'post'"
          class="search-modal__tab"
          :class="{ 'search-modal__tab--active': activeTab === 'post' }"
          data-testid="search-modal-tab-post"
          type="button"
          @click="onTab('post')"
        >
          post
        </button>
      </div>

      <div class="search-modal__results">
        <div v-if="loading" class="search-modal__state" data-testid="search-loading">searching…</div>
        <div
          v-else-if="error"
          class="search-modal__state search-modal__state--error"
          data-testid="search-error"
        >
          {{ error }}
        </div>
        <div v-else-if="!trimmed" class="search-modal__state" data-testid="search-empty">
          type to search users and posts
        </div>
        <template v-else>
          <section v-if="showUsers" class="search-modal__group" aria-label="Accounts">
            <header v-if="activeTab === 'general'" class="search-modal__group-title">
              <span>ACCOUNTS</span>
              <button
                v-if="userResults.length > 0"
                class="search-modal__see-all"
                type="button"
                data-testid="search-see-all-users"
                @click="onTab('username')"
              >
                see all →
              </button>
            </header>
            <div
              v-if="userResults.length === 0"
              class="search-modal__state"
              data-testid="search-no-users"
            >
              no users found
            </div>
            <ul v-else class="search-modal__list">
              <li
                v-for="u in userResults"
                :key="u.id"
                class="search-modal__row"
                :data-testid="`search-result-${u.id}`"
                @mousedown.prevent="$emit('selectUser', u.id)"
              >
                <span class="search-modal__avatar" aria-hidden="true">
                  <img
                    v-if="avatarUrls[u.id]"
                    :src="avatarUrls[u.id] ?? undefined"
                    alt=""
                    class="search-modal__avatar-img"
                  />
                  <AppIcon v-else name="user" :size="18" />
                </span>
                <span class="search-modal__text">
                  <span class="search-modal__name">{{ u.displayName }}</span>
                  <span class="search-modal__username">@{{ u.username }}</span>
                </span>
              </li>
            </ul>
          </section>

          <section v-if="showPosts" class="search-modal__group" aria-label="Posts">
            <header v-if="activeTab === 'general'" class="search-modal__group-title">
              <span>POSTS</span>
              <button
                v-if="postResults.length > 0"
                class="search-modal__see-all"
                type="button"
                data-testid="search-see-all-posts"
                @click="onTab('post')"
              >
                see all →
              </button>
            </header>
            <div
              v-if="postResults.length === 0"
              class="search-modal__state"
              data-testid="search-no-posts"
            >
              no posts found
            </div>
            <ul v-else class="search-modal__list">
              <li
                v-for="p in postResults"
                :key="p.id"
                class="search-modal__row search-modal__row--post"
                :data-testid="`search-post-${p.id}`"
                @mousedown.prevent="$emit('selectPost', p.id)"
              >
                <span class="search-modal__text">
                  <span class="search-modal__snippet">{{ postSnippet(p) }}</span>
                  <span class="search-modal__post-meta">
                    ◷ {{ postTime(p) }} · ▸ {{ p.viewCount }} views · ▲ {{ p.likeCount }} ·
                    ▾ {{ p.dislikeCount }}
                  </span>
                </span>
              </li>
            </ul>
          </section>
        </template>
      </div>
    </section>
  </div>
</template>

<style scoped>
.search-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: var(--sarv-space-4);
  background: rgb(0 0 0 / 0.6);
  overflow-y: auto;
}

.search-modal {
  width: min(560px, 94vw);
  max-height: min(640px, 84dvh);
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  border-color: var(--sarv-green-dark);
  box-shadow: var(--sarv-glow);
  background: var(--sarv-panel);
}

.search-modal__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sarv-space-3) var(--sarv-space-4);
  border-bottom: 1px solid var(--sarv-border);
}

.search-modal__title {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
}

.search-modal__close {
  padding: 4px 10px;
}

.search-modal__input-wrap {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-2);
  margin: var(--sarv-space-3) var(--sarv-space-4) 0;
  padding: 8px 10px;
  background: var(--sarv-bg);
  border: 1px solid var(--sarv-border-bright);
}

.search-modal__input-wrap:focus-within {
  border-color: var(--sarv-green-dim);
  box-shadow: 0 0 0 1px var(--sarv-green-dim);
}

.search-modal__icon {
  color: var(--sarv-text-dim);
  flex-shrink: 0;
}

.search-modal__input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  outline: none;
  color: var(--sarv-text);
  font-size: 13px;
  letter-spacing: 0.04em;
}

.search-modal__input::placeholder {
  color: var(--sarv-text-faint);
}

.search-modal__tabs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  margin: var(--sarv-space-3) var(--sarv-space-4) 0;
  background: var(--sarv-border);
  border: 1px solid var(--sarv-border);
}

.search-modal__tab {
  padding: 8px 4px;
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  background: var(--sarv-panel-alt);
  border: none;
  border-bottom: 1px solid transparent;
  color: var(--sarv-text-dim);
  cursor: pointer;
}

.search-modal__tab:hover {
  color: var(--sarv-text);
}

.search-modal__tab--active {
  background: var(--sarv-green-faint);
  color: var(--sarv-green);
  border-bottom-color: var(--sarv-green-dim);
}

.search-modal__results {
  margin: var(--sarv-space-3) var(--sarv-space-4) var(--sarv-space-4);
  border: 1px solid var(--sarv-border);
  background: var(--sarv-bg);
  overflow-y: auto;
  min-height: 120px;
  max-height: 100%;
}

.search-modal__state {
  padding: var(--sarv-space-4);
  font-size: 12px;
  color: var(--sarv-text-dim);
  text-align: center;
}

.search-modal__state--error {
  color: #ff8fa3;
}

.search-modal__group + .search-modal__group {
  border-top: 1px solid var(--sarv-border);
}

.search-modal__group-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sarv-space-2) var(--sarv-space-3);
  font-size: 10px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
  border-bottom: 1px solid var(--sarv-border);
}

.search-modal__see-all {
  background: transparent;
  border: none;
  color: var(--sarv-text-dim);
  font-size: 11px;
  letter-spacing: 0.04em;
  cursor: pointer;
  padding: 2px 4px;
}

.search-modal__see-all:hover {
  color: var(--sarv-green);
}

.search-modal__list {
  display: grid;
  gap: 1px;
}

.search-modal__row {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-3);
  padding: 10px 12px;
  border: 1px solid transparent;
  cursor: pointer;
}

.search-modal__row:hover {
  background: var(--sarv-panel-alt);
  border-color: var(--sarv-border);
}

.search-modal__avatar {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  background: var(--sarv-panel);
  border: 1px solid var(--sarv-border);
  color: var(--sarv-text-dim);
  flex-shrink: 0;
  overflow: hidden;
}

.search-modal__avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.search-modal__text {
  display: grid;
  gap: 2px;
  min-width: 0;
  flex: 1;
}

.search-modal__name,
.search-modal__snippet {
  font-size: 12px;
  color: var(--sarv-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.search-modal__row--post .search-modal__snippet {
  white-space: normal;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.search-modal__username,
.search-modal__post-meta {
  font-size: 11px;
  color: var(--sarv-text-dim);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

@media (max-width: 640px) {
  .search-modal-overlay {
    padding: var(--sarv-space-2);
    place-items: start center;
  }

  .search-modal {
    width: 100%;
    max-height: 92dvh;
  }

  .search-modal__row {
    min-height: 48px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .search-modal-overlay * {
    animation: none;
  }
}
</style>
