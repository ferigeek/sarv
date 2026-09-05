<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import gsap from 'gsap'

import { getMediaBlob, getMediaMetadata } from '@/api/media'
import { getPost } from '@/api/posts'
import { addReaction, getReaction, removeReaction } from '@/api/reactions'
import { getUser } from '@/api/users'
import type { PostResponse, UserResponse, UserReaction } from '@/types/api'
import AppIcon from './AppIcon.vue'

const props = withDefaults(
  defineProps<{ post: PostResponse; clickable?: boolean; detailed?: boolean }>(),
  { clickable: true, detailed: false },
)

const router = useRouter()

const user = ref<UserResponse | null>(null)
const avatarUrl = ref<string | null>(null)
let avatarObjectUrl: string | null = null

const mediaUrl = ref<string | null>(null)
const mediaMimeType = ref<string | null>(null)
let mediaObjectUrl: string | null = null

const likeCount = ref(props.post.likeCount)
const dislikeCount = ref(props.post.dislikeCount)
const viewCount = ref(props.post.viewCount)
const userReaction = ref<UserReaction>(0)
const reactionLoading = ref(false)

const feedback = ref<'smile' | 'sad' | null>(null)
const feedbackRef = ref<HTMLElement | null>(null)

const createdAtLabel = computed(() => {
  try {
    const d = new Date(props.post.createdAt)
    return d.toLocaleString()
  } catch {
    return props.post.createdAt
  }
})

const isVideoMedia = computed(() => mediaMimeType.value?.startsWith('video/') ?? false)

const commentCount = computed(() => props.post.commentCount ?? 0)

/* Category identity: COMMENT / REPOST / QUOTE get a terminal-blue banner,
   and REPOST / QUOTE embed a one-level preview of the referenced post. */
const showBanner = computed(
  () =>
    props.post.postCategory === 'COMMENT' ||
    props.post.postCategory === 'REPOST' ||
    props.post.postCategory === 'QUOTE',
)

const hasOriginal = computed(
  () =>
    (props.post.postCategory === 'REPOST' || props.post.postCategory === 'QUOTE') &&
    props.post.repostOfId !== null &&
    props.post.repostOfId !== undefined &&
    props.post.repostOfId !== props.post.id,
)

const original = ref<PostResponse | null>(null)
const originalAuthor = ref<UserResponse | null>(null)
const originalMissing = ref(false)

const originalSnippet = computed(() => {
  const c = original.value?.content
  if (!c) return '(no text)'
  return c.length > 140 ? `${c.slice(0, 140)}…` : c
})

const bannerLabel = computed(() => {
  if (props.post.postCategory === 'COMMENT') {
    return `▸ COMMENT // on post #${props.post.parentId ?? '?'}`
  }
  const refName =
    originalAuthor.value?.username != null
      ? `@${originalAuthor.value.username}`
      : `post #${props.post.repostOfId ?? '?'}` 
  if (props.post.postCategory === 'REPOST') return `↻ REPOST // of ${refName}`
  return `❝ QUOTE // of ${refName}`
})

function bannerTargetId(): number | null {
  if (props.post.postCategory === 'COMMENT') return props.post.parentId
  return props.post.repostOfId
}

function goBanner() {
  const id = bannerTargetId()
  if (id === null || id === undefined) return
  void router.push({ name: 'post-detail', params: { id: String(id) } })
}

function goOriginal() {
  if (!original.value) return
  void router.push({ name: 'post-detail', params: { id: String(original.value.id) } })
}

async function loadOriginal() {
  original.value = null
  originalAuthor.value = null
  originalMissing.value = false
  if (!hasOriginal.value) return
  const id = props.post.repostOfId as number
  try {
    const o = await getPost(id)
    // Ignore stale responses when the card was reused for another post.
    if (props.post.repostOfId !== id) return
    original.value = o
    try {
      originalAuthor.value = await getUser(o.userId)
    } catch {
      originalAuthor.value = null
    }
  } catch {
    if (props.post.repostOfId !== id) return
    originalMissing.value = true
  }
}

function goProfile() {
  void router.push({ name: 'profile', params: { id: String(props.post.userId) } })
}

function goDetail() {
  if (!props.clickable || props.detailed) return
  void router.push({ name: 'post-detail', params: { id: String(props.post.id) } })
}

function onComment() {
  if (props.detailed) return
  void router.push({ name: 'post-detail', params: { id: String(props.post.id) } })
}

function clearAvatar() {
  if (avatarObjectUrl) {
    URL.revokeObjectURL(avatarObjectUrl)
    avatarObjectUrl = null
  }
  avatarUrl.value = null
}

function clearMedia() {
  if (mediaObjectUrl) {
    URL.revokeObjectURL(mediaObjectUrl)
    mediaObjectUrl = null
  }
  mediaUrl.value = null
  mediaMimeType.value = null
}

async function loadUser() {
  try {
    const u = await getUser(props.post.userId)
    user.value = u
    clearAvatar()
    if (u.profilePictureId) {
      try {
        const blob = await getMediaBlob(u.profilePictureId)
        avatarObjectUrl = URL.createObjectURL(blob)
        avatarUrl.value = avatarObjectUrl
      } catch {
        avatarUrl.value = null
      }
    }
  } catch {
    user.value = null
  }
}

async function loadReaction() {
  try {
    const r = await getReaction(props.post.id)
    likeCount.value = r.likeCount
    dislikeCount.value = r.dislikeCount
    userReaction.value = r.userReaction
  } catch {
    // Keep counts from post and reaction 0
    likeCount.value = props.post.likeCount
    dislikeCount.value = props.post.dislikeCount
    userReaction.value = 0
  }
}

async function loadMedia() {
  clearMedia()
  if (!props.post.mediaId) return
  const mediaId = props.post.mediaId
  try {
    const metadataPromise: Promise<{ mimeType: string } | null> = Promise.resolve()
      .then(() => getMediaMetadata(mediaId))
      .then(
        (m) => m ?? null,
        () => null,
      )
    const [metadata, blob] = await Promise.all([metadataPromise, getMediaBlob(mediaId)])
    // Ignore stale responses when mediaId changed while fetching.
    if (props.post.mediaId !== mediaId) return
    mediaObjectUrl = URL.createObjectURL(blob)
    mediaUrl.value = mediaObjectUrl
    mediaMimeType.value = metadata?.mimeType ?? (blob.type || null)
  } catch {
    if (props.post.mediaId !== mediaId) return
    mediaUrl.value = null
    mediaMimeType.value = null
  }
}

watch(
  () => props.post.mediaId,
  () => {
    void loadMedia()
  },
)

watch(
  () => [props.post.id, props.post.repostOfId],
  () => {
    void loadReaction()
    void loadOriginal()
  },
)

onMounted(() => {
  viewCount.value = props.post.viewCount
  likeCount.value = props.post.likeCount
  dislikeCount.value = props.post.dislikeCount
  void loadUser()
  void loadReaction()
  void loadMedia()
  void loadOriginal()
})

onBeforeUnmount(() => {
  clearAvatar()
  clearMedia()
})

async function showFeedback(type: 'smile' | 'sad') {
  feedback.value = type
  await nextTick()
  if (!feedbackRef.value) return
  gsap.fromTo(
    feedbackRef.value,
    { scale: 0.3, opacity: 0, y: 8 },
    { scale: 1, opacity: 1, y: 0, duration: 0.28, ease: 'back.out(1.7)' },
  )
  setTimeout(() => {
    if (!feedbackRef.value) {
      feedback.value = null
      return
    }
    gsap.to(feedbackRef.value, {
      scale: 0.6,
      opacity: 0,
      y: -10,
      duration: 0.22,
      ease: 'power2.in',
      onComplete: () => {
        feedback.value = null
      },
    })
  }, 900)
}

async function onLike() {
  if (reactionLoading.value) return
  reactionLoading.value = true
  try {
    if (userReaction.value === 1) {
      await removeReaction(props.post.id)
      // After removal, counts decrement; we could fetch but optimistically update
      likeCount.value = Math.max(0, likeCount.value - 1)
      userReaction.value = 0
    } else {
      const res = await addReaction(props.post.id, 1)
      likeCount.value = res.likeCount
      dislikeCount.value = res.dislikeCount
      userReaction.value = res.userReaction
      if (res.userReaction === 1) {
        void showFeedback('smile')
      }
    }
  } catch {
    // Keep previous state on error; could fetch again
    await loadReaction()
  } finally {
    reactionLoading.value = false
  }
}

async function onDislike() {
  if (reactionLoading.value) return
  reactionLoading.value = true
  try {
    if (userReaction.value === -1) {
      await removeReaction(props.post.id)
      dislikeCount.value = Math.max(0, dislikeCount.value - 1)
      userReaction.value = 0
    } else {
      const res = await addReaction(props.post.id, -1)
      likeCount.value = res.likeCount
      dislikeCount.value = res.dislikeCount
      userReaction.value = res.userReaction
      if (res.userReaction === -1) {
        void showFeedback('sad')
      }
    }
  } catch {
    await loadReaction()
  } finally {
    reactionLoading.value = false
  }
}
</script>

<template>
  <article
    class="post-card panel"
    :class="{ 'post-card--clickable': clickable && !detailed }"
    :data-testid="`post-card-${post.id}`"
    @click="goDetail"
  >
    <button
      v-if="showBanner"
      class="post-category"
      type="button"
      data-testid="post-category-banner"
      @click.stop="goBanner"
    >
      {{ bannerLabel }}
    </button>

    <header class="post-header">
      <button
        class="post-author"
        type="button"
        data-testid="post-author-link"
        :aria-label="`View profile of ${user?.displayName ?? `User ${post.userId}`}`"
        @click.stop="goProfile"
      >
        <span class="post-avatar" data-testid="post-avatar" aria-hidden="true">
          <img
            v-if="avatarUrl"
            :src="avatarUrl"
            alt=""
            class="post-avatar__img"
            data-testid="post-avatar-img"
          />
          <AppIcon v-else name="user" :size="20" />
        </span>
        <span class="post-user">
          <span class="post-displayName" data-testid="post-author-name">{{
            user?.displayName ?? `User ${post.userId}`
          }}</span>
          <span class="post-username" data-testid="post-author-username"
            >@{{ user?.username ?? `user${post.userId}` }}</span
          >
        </span>
      </button>
      <span class="post-time" data-testid="post-time">{{ createdAtLabel }}</span>
    </header>

    <div
      v-if="post.content && post.postCategory !== 'REPOST'"
      class="post-content"
      data-testid="post-content"
    >
      {{ post.content }}
    </div>

    <div v-if="hasOriginal" class="post-original" data-testid="post-original">
      <div
        v-if="originalMissing"
        class="post-original__missing"
        data-testid="post-original-missing"
      >
        original post unavailable
      </div>
      <button
        v-else-if="original"
        class="post-original__body"
        type="button"
        data-testid="post-original-body"
        @click.stop="goOriginal"
      >
        <span class="post-original__author"
          >{{ originalAuthor?.displayName ?? `User ${original.userId}` }}
          <span class="post-original__username"
            >@{{ originalAuthor?.username ?? `user${original.userId}` }}</span
          ></span
        >
        <span class="post-original__snippet" data-testid="post-original-content">{{
          originalSnippet
        }}</span>
        <span class="post-original__meta"
          >▸ {{ original.viewCount }} views · ▲ {{ original.likeCount }} · ▾
          {{ original.dislikeCount }}</span
        >
      </button>
      <div v-else class="post-original__missing" data-testid="post-original-loading">
        loading original…
      </div>
    </div>

    <div v-if="mediaUrl" class="post-media" data-testid="post-media" @click.stop>
      <video
        v-if="isVideoMedia"
        :src="mediaUrl"
        controls
        preload="metadata"
        playsinline
        class="post-media__video"
        data-testid="post-media-video"
      />
      <img v-else :src="mediaUrl" alt="post media" class="post-media__img" data-testid="post-media-img" />
    </div>

    <footer class="post-footer">
      <span class="post-stat" data-testid="post-view-count" title="views">
        <AppIcon name="eye" :size="16" />
        {{ viewCount }}
      </span>

      <span class="post-stat" data-testid="post-comment-count" title="comments">
        <AppIcon name="comment" :size="16" />
        {{ commentCount }}
      </span>

      <button
        class="post-action"
        :class="{ 'post-action--liked': userReaction === 1 }"
        type="button"
        data-testid="post-like-btn"
        :aria-pressed="userReaction === 1"
        :disabled="reactionLoading"
        @click.stop="onLike"
      >
        <AppIcon name="thumbs-up" :size="16" />
        <span data-testid="post-like-count">{{ likeCount }}</span>
      </button>

      <button
        class="post-action"
        :class="{ 'post-action--disliked': userReaction === -1 }"
        type="button"
        data-testid="post-dislike-btn"
        :aria-pressed="userReaction === -1"
        :disabled="reactionLoading"
        @click.stop="onDislike"
      >
        <AppIcon name="thumbs-down" :size="16" />
        <span data-testid="post-dislike-count">{{ dislikeCount }}</span>
      </button>

      <button
        class="post-action post-action--inert"
        type="button"
        data-testid="post-repost-btn"
        title="Repost — coming soon"
      >
        <AppIcon name="repeat" :size="16" />
        repost
      </button>

      <button
        class="post-action post-action--inert"
        type="button"
        data-testid="post-quote-btn"
        title="Quote — coming soon"
      >
        <AppIcon name="share" :size="16" />
        quote
      </button>

      <button
        class="post-action"
        type="button"
        data-testid="post-comment-btn"
        title="View comments and reply"
        @click.stop="onComment"
      >
        <AppIcon name="comment" :size="16" />
        comment
      </button>
    </footer>

    <div
      v-if="feedback"
      ref="feedbackRef"
      class="post-feedback"
      :class="`post-feedback--${feedback}`"
      :data-testid="`post-feedback-${feedback}`"
      aria-hidden="true"
    >
      <span class="post-feedback__emoji">{{ feedback === 'smile' ? '☺' : '☹' }}</span>
    </div>
  </article>
</template>

<style scoped>
.post-card {
  position: relative;
  padding: var(--sarv-space-4);
  display: grid;
  gap: var(--sarv-space-3);
  overflow: hidden;
}

.post-card--clickable {
  cursor: pointer;
}

.post-card--clickable:hover {
  border-color: var(--sarv-border-bright);
}

.post-header {
  display: flex;
  align-items: center;
  gap: var(--sarv-space-3);
}

.post-author {
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

.post-author:hover .post-displayName {
  text-decoration: underline;
  text-decoration-color: var(--sarv-green-dim);
  text-underline-offset: 2px;
}

.post-author:focus-visible {
  outline: 1px solid var(--sarv-green);
  outline-offset: 2px;
}

.post-avatar {
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

.post-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.post-user {
  display: grid;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.post-displayName {
  font-size: 13px;
  font-weight: 700;
  color: var(--sarv-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.post-username {
  font-size: 11px;
  color: var(--sarv-text-dim);
}

.post-time {
  font-size: 11px;
  color: var(--sarv-text-faint);
  white-space: nowrap;
}

.post-content {
  font-size: 14px;
  line-height: 1.5;
  color: var(--sarv-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.post-category {
  justify-self: start;
  max-width: 100%;
  padding: 4px 8px;
  font-family: inherit;
  font-size: 11px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: var(--sarv-blue);
  background: color-mix(in srgb, var(--sarv-blue) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--sarv-blue) 40%, transparent);
  cursor: pointer;
}

.post-category:hover {
  background: color-mix(in srgb, var(--sarv-blue) 18%, transparent);
}

.post-category:focus-visible {
  outline: 1px solid var(--sarv-blue);
  outline-offset: 2px;
}

.post-original {
  border: 1px solid var(--sarv-border);
  border-left: 3px solid var(--sarv-blue);
  background: var(--sarv-bg);
  overflow: hidden;
}

.post-original__body {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 10px 12px;
  background: transparent;
  border: none;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.post-original__body:hover {
  background: var(--sarv-panel-alt);
}

.post-original__body:focus-visible {
  outline: 1px solid var(--sarv-blue);
  outline-offset: -1px;
}

.post-original__author {
  font-size: 12px;
  font-weight: 700;
  color: var(--sarv-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.post-original__username {
  font-weight: 400;
  color: var(--sarv-text-dim);
}

.post-original__snippet {
  font-size: 13px;
  line-height: 1.5;
  color: var(--sarv-text);
  word-break: break-word;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-original__meta {
  font-size: 11px;
  color: var(--sarv-text-dim);
}

.post-original__missing {
  padding: 10px 12px;
  font-size: 12px;
  color: var(--sarv-text-dim);
}

.post-media {
  border: 1px solid var(--sarv-border);
  background: var(--sarv-bg);
  overflow: hidden;
}

.post-media__img,
.post-media__video {
  display: block;
  width: 100%;
  max-height: 420px;
  object-fit: contain;
}

.post-media__video {
  background: #000;
}

.post-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--sarv-space-2);
  padding-top: var(--sarv-space-2);
  border-top: 1px solid var(--sarv-border);
}

.post-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--sarv-text-dim);
}

.post-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  font-size: 12px;
  background: transparent;
  border: 1px solid transparent;
  color: var(--sarv-text-dim);
  cursor: pointer;
}

.post-action:hover {
  background: var(--sarv-panel-alt);
  border-color: var(--sarv-border);
  color: var(--sarv-text);
}

.post-action:disabled {
  opacity: 0.6;
  cursor: wait;
}

.post-action--liked {
  color: var(--sarv-green);
  border-color: color-mix(in srgb, var(--sarv-green) 30%, transparent);
  background: color-mix(in srgb, var(--sarv-green) 8%, transparent);
}

.post-action--disliked {
  color: var(--sarv-red);
  border-color: color-mix(in srgb, var(--sarv-red) 30%, transparent);
  background: color-mix(in srgb, var(--sarv-red) 8%, transparent);
}

.post-action--inert {
  cursor: default;
  opacity: 0.85;
}

.post-feedback {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  background: var(--sarv-panel);
  border: 1px solid var(--sarv-border-bright);
  box-shadow: var(--sarv-glow);
  pointer-events: none;
}

.post-feedback--smile {
  color: var(--sarv-green);
  border-color: var(--sarv-green);
}

.post-feedback--sad {
  color: var(--sarv-red);
  border-color: var(--sarv-red);
}

.post-feedback__emoji {
  font-size: 28px;
  line-height: 1;
  font-family: var(--sarv-font-mono);
  /* Pixelated look */
  image-rendering: pixelated;
  filter: contrast(1.2);
}

@media (max-width: 640px) {
  .post-card {
    padding: var(--sarv-space-3);
  }

  .post-header {
    gap: var(--sarv-space-2);
  }

  .post-time {
    font-size: 10px;
    max-width: 96px;
    white-space: normal;
    text-align: right;
    line-height: 1.3;
  }

  .post-footer {
    gap: 4px;
  }

  .post-action,
  .post-stat {
    min-height: 44px;
    min-width: 44px;
    justify-content: center;
  }

  /* Icon-only for not-yet-implemented actions to save horizontal space. */
  .post-action--inert {
    font-size: 0;
    gap: 0;
    padding: 4px 10px;
  }

  .post-media__img,
  .post-media__video {
    max-height: 300px;
  }
}
</style>
