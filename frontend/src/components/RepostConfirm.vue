<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import gsap from 'gsap'

import type { ApiError } from '@/api/client'
import { repostPost } from '@/api/posts'
import type { PostResponse } from '@/types/api'

const props = defineProps<{ post: PostResponse; authorLabel: string }>()

const emit = defineEmits<{ close: []; reposted: [id: number] }>()

const panelRef = ref<HTMLElement | null>(null)
const publishing = ref(false)
const errorMsg = ref('')

const snippet = computed(() => {
  const c = props.post.content
  if (c) return c.length > 120 ? `${c.slice(0, 120)}…` : c
  return '↻ repost'
})

async function confirm() {
  if (publishing.value) return
  publishing.value = true
  errorMsg.value = ''
  try {
    const created = await repostPost(props.post.id)
    emit('reposted', created.id)
    close()
  } catch (e) {
    const err = e as ApiError
    errorMsg.value = err.detail ?? err.title ?? 'failed to repost'
  } finally {
    publishing.value = false
  }
}

function onOverlayClick(e: MouseEvent) {
  if (e.target === e.currentTarget && !publishing.value) close()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && !publishing.value) close()
}

onMounted(() => {
  if (typeof window !== 'undefined') window.addEventListener('keydown', onKeydown)
  if (!panelRef.value) return
  gsap.fromTo(
    panelRef.value,
    { opacity: 0, y: 8, scale: 0.98 },
    { opacity: 1, y: 0, scale: 1, duration: 0.22, ease: 'power2.out' },
  )
})

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') window.removeEventListener('keydown', onKeydown)
})

function close() {
  if (!panelRef.value) {
    emit('close')
    return
  }
  gsap.to(panelRef.value, {
    opacity: 0,
    y: 0,
    scale: 0.98,
    duration: 0.18,
    ease: 'power2.in',
    onComplete: () => emit('close'),
  })
}
</script>

<template>
  <div
    class="repost-confirm-overlay"
    data-testid="repost-confirm-overlay"
    @click.stop="onOverlayClick"
  >
    <section ref="panelRef" class="panel repost-confirm-panel" data-testid="repost-confirm-modal">
      <header class="repost-confirm__header">
        <span class="repost-confirm__title">REPOST //</span>
        <button
          class="btn repost-confirm__close"
          type="button"
          data-testid="repost-confirm-cancel-top"
          :disabled="publishing"
          @click="close"
        >
          ✕
        </button>
      </header>

      <p class="repost-confirm__prompt">
        Repost this post by <strong>{{ authorLabel }}</strong>?
      </p>

      <blockquote class="repost-confirm__snippet" data-testid="repost-confirm-snippet">
        {{ snippet }}
      </blockquote>

      <p v-if="errorMsg" class="repost-confirm__error" data-testid="repost-confirm-error">
        {{ errorMsg }}
      </p>

      <div class="repost-confirm__actions">
        <button
          class="btn"
          type="button"
          data-testid="repost-confirm-cancel"
          :disabled="publishing"
          @click="close"
        >
          cancel
        </button>
        <button
          class="btn btn-primary"
          type="button"
          data-testid="repost-confirm-submit"
          :disabled="publishing"
          @click="confirm"
        >
          {{ publishing ? 'reposting…' : 'repost' }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.repost-confirm-overlay {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: var(--sarv-space-4);
  background: rgb(0 0 0 / 0.6);
}

.repost-confirm-panel {
  width: min(420px, 94vw);
  border-color: var(--sarv-green-dark);
  box-shadow: var(--sarv-glow);
  background: var(--sarv-panel);
}

.repost-confirm__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sarv-space-3) var(--sarv-space-4);
  border-bottom: 1px solid var(--sarv-border);
}

.repost-confirm__title {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
}

.repost-confirm__close {
  padding: 4px 10px;
}

.repost-confirm__prompt {
  padding: var(--sarv-space-4) var(--sarv-space-4) 0;
  font-size: 13px;
  color: var(--sarv-text);
}

.repost-confirm__snippet {
  margin: var(--sarv-space-3) var(--sarv-space-4) 0;
  padding: 10px 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--sarv-text-dim);
  background: var(--sarv-bg);
  border: 1px solid var(--sarv-border);
  border-left: 3px solid var(--sarv-blue);
  word-break: break-word;
}

.repost-confirm__error {
  margin: var(--sarv-space-3) var(--sarv-space-4) 0;
  padding: 8px 10px;
  background: color-mix(in srgb, var(--sarv-red) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--sarv-red) 40%, transparent);
  color: #ff8fa3;
  font-size: 12px;
}

.repost-confirm__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--sarv-space-3);
  padding: var(--sarv-space-4);
}
</style>
