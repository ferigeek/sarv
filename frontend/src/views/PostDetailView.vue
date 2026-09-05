<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import type { ApiError } from '@/api/client'
import { getComments, getPost } from '@/api/posts'
import type { CommentSort, PostResponse } from '@/types/api'
import PostCard from '@/components/PostCard.vue'
import PostCreateModal from '@/components/PostCreateModal.vue'

const route = useRoute()
const router = useRouter()

const postId = computed(() => Number(route.params.id))

const post = ref<PostResponse | null>(null)
const postLoading = ref(true)
const postError = ref('')

const comments = ref<PostResponse[]>([])
const commentsLoading = ref(false)
const commentsError = ref('')
const commentsPage = ref(0)
const commentsHasMore = ref(true)
const commentsSize = 10
const sortBy = ref<CommentSort>('NEWEST')

const showComposer = ref(false)

async function loadPost() {
  postLoading.value = true
  postError.value = ''
  try {
    post.value = await getPost(postId.value)
  } catch (e) {
    const err = e as ApiError
    postError.value = err.detail ?? err.title ?? 'Failed to load post.'
    post.value = null
  } finally {
    postLoading.value = false
  }
}

async function loadComments(pageNum: number, append: boolean) {
  if (commentsLoading.value && append) return
  commentsLoading.value = true
  commentsError.value = ''
  try {
    const data = await getComments(postId.value, sortBy.value, { page: pageNum, size: commentsSize })
    comments.value = append ? [...comments.value, ...data.content] : data.content
    commentsHasMore.value = data.page.number + 1 < data.page.totalPages
    if (data.content.length === 0) commentsHasMore.value = false
    commentsPage.value = pageNum
  } catch (e) {
    const err = e as ApiError
    commentsError.value = err.detail ?? err.title ?? 'Failed to load comments.'
    if (!append) comments.value = []
  } finally {
    commentsLoading.value = false
  }
}

function switchSort(next: CommentSort) {
  if (next === sortBy.value) return
  sortBy.value = next
  void loadComments(0, false)
}

function loadMore() {
  if (!commentsHasMore.value || commentsLoading.value) return
  void loadComments(commentsPage.value + 1, true)
}

function onCommentCreated() {
  showComposer.value = false
  void loadPost()
  void loadComments(0, false)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    void router.push({ name: 'feed' })
  }
}

watch(
  () => route.params.id,
  () => {
    showComposer.value = false
    void loadPost()
    void loadComments(0, false)
  },
  { immediate: true },
)
</script>

<template>
  <section class="post-detail" data-testid="post-detail-view">
    <header class="post-detail__header">
      <button class="btn" type="button" data-testid="post-detail-back" @click="goBack">← back</button>
      <span class="post-detail__meta">SYS.POST // {{ postId }}</span>
    </header>

    <div v-if="postLoading" class="post-detail__state" data-testid="post-detail-loading">loading post…</div>

    <div
      v-else-if="postError && !post"
      class="post-detail__state post-detail__state--error"
      data-testid="post-detail-error"
    >
      {{ postError }}
      <button class="btn" type="button" data-testid="post-detail-retry" @click="loadPost">retry</button>
    </div>

    <template v-else-if="post">
      <PostCard :post="post" detailed data-testid="post-detail-card" />

      <section class="post-detail__comments panel">
        <header class="post-detail__comments-header">
          <span class="post-detail__comments-title">COMMENTS // {{ post.commentCount ?? comments.length }}</span>
          <button
            class="btn btn-primary"
            type="button"
            data-testid="comment-write-btn"
            @click="showComposer = true"
          >
            + write a comment
          </button>
        </header>

        <nav class="comment-sort" role="tablist" aria-label="Comment sort" data-testid="comment-sort">
          <button
            class="comment-sort__tab"
            :class="{ 'comment-sort__tab--active': sortBy === 'NEWEST' }"
            role="tab"
            :aria-selected="sortBy === 'NEWEST'"
            type="button"
            data-testid="comment-sort-newest"
            @click="switchSort('NEWEST')"
          >
            newest
          </button>
          <button
            class="comment-sort__tab"
            :class="{ 'comment-sort__tab--active': sortBy === 'MOST_LIKED' }"
            role="tab"
            :aria-selected="sortBy === 'MOST_LIKED'"
            type="button"
            data-testid="comment-sort-most-liked"
            @click="switchSort('MOST_LIKED')"
          >
            most liked
          </button>
        </nav>

        <div
          v-if="commentsLoading && comments.length === 0"
          class="post-detail__state"
          data-testid="comment-loading"
        >
          loading comments…
        </div>

        <div
          v-else-if="commentsError && comments.length === 0"
          class="post-detail__state post-detail__state--error"
          data-testid="comment-error"
        >
          {{ commentsError }}
          <button class="btn" type="button" data-testid="comment-retry" @click="loadComments(0, false)">
            retry
          </button>
        </div>

        <div v-else-if="comments.length === 0" class="post-detail__state" data-testid="comment-empty">
          no comments yet — write the first one
        </div>

        <template v-else>
          <div class="comment-list" data-testid="comment-list">
            <PostCard v-for="c in comments" :key="c.id" :post="c" />
          </div>

          <footer class="comment-footer">
            <div
              v-if="commentsError"
              class="post-detail__state post-detail__state--error"
              data-testid="comment-error-more"
            >
              {{ commentsError }}
            </div>
            <button
              v-if="commentsHasMore"
              class="btn"
              type="button"
              data-testid="comment-load-more"
              :disabled="commentsLoading"
              @click="loadMore"
            >
              {{ commentsLoading ? 'loading…' : 'load more' }}
            </button>
            <span v-else class="comment-end" data-testid="comment-end">— end of comments —</span>
          </footer>
        </template>
      </section>
    </template>

    <PostCreateModal
      v-if="showComposer && post"
      mode="comment"
      :parent-id="post.id"
      @close="showComposer = false"
      @created="onCommentCreated"
    />
  </section>
</template>

<style scoped>
.post-detail {
  min-height: 100%;
  display: grid;
  gap: 1px;
  background: var(--sarv-border);
  align-content: start;
}

.post-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sarv-space-3) var(--sarv-space-4);
  background: var(--sarv-panel);
}

.post-detail__meta {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: var(--sarv-text-faint);
}

.post-detail__state {
  padding: var(--sarv-space-6);
  text-align: center;
  font-size: 12px;
  color: var(--sarv-text-dim);
  background: var(--sarv-panel);
  display: grid;
  gap: var(--sarv-space-3);
  justify-items: center;
}

.post-detail__state--error {
  color: #ff8fa3;
}

.post-detail__comments {
  padding: 0;
  overflow: hidden;
}

.post-detail__comments-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sarv-space-3);
  padding: var(--sarv-space-3) var(--sarv-space-4);
  background: var(--sarv-panel);
  border-bottom: 1px solid var(--sarv-border);
}

.post-detail__comments-title {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
}

.comment-sort {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1px;
  background: var(--sarv-border);
  border-bottom: 1px solid var(--sarv-border);
}

.comment-sort__tab {
  padding: var(--sarv-space-2) var(--sarv-space-3);
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  background: var(--sarv-panel);
  border: none;
  border-bottom: 1px solid transparent;
  color: var(--sarv-text-dim);
  cursor: pointer;
}

.comment-sort__tab:hover {
  background: var(--sarv-panel-alt);
  color: var(--sarv-text);
}

.comment-sort__tab--active {
  background: var(--sarv-panel-alt);
  color: var(--sarv-green);
  border-bottom-color: var(--sarv-green);
}

.comment-list {
  display: grid;
  gap: 1px;
}

.comment-footer {
  display: grid;
  gap: var(--sarv-space-3);
  padding: var(--sarv-space-4);
  background: var(--sarv-panel);
  justify-items: center;
}

.comment-end {
  font-size: 11px;
  letter-spacing: 0.08em;
  color: var(--sarv-text-faint);
}
</style>
