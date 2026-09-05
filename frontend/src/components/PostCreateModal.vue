<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import gsap from 'gsap'

import type { ApiError } from '@/api/client'
import { uploadMedia } from '@/api/media'
import { createPost } from '@/api/posts'

const emit = defineEmits<{ close: []; created: [id: number] }>()

const props = withDefaults(
  defineProps<{ mode?: 'post' | 'comment'; parentId?: number | null }>(),
  { mode: 'post', parentId: null },
)

const isComment = computed(() => props.mode === 'comment')

const panelRef = ref<HTMLElement | null>(null)
const progressRef = ref<HTMLElement | null>(null)

const content = ref('')
const fileInput = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const previewUrl = ref<string | null>(null)
let previewObjectUrl: string | null = null

const progress = ref(0) // 0..1
const phase = ref<'idle' | 'uploading' | 'uploaded' | 'publishing' | 'error'>('idle')
const errorMsg = ref('')
const submitting = ref(false)
const lastUploadedMediaId = ref<number | null>(null)

const canSubmit = computed(() => {
  if (phase.value === 'uploading' || phase.value === 'publishing' || submitting.value) return false
  if (phase.value === 'error') return false
  const hasContent = content.value.trim().length > 0
  const hasMedia = phase.value === 'uploaded' && lastUploadedMediaId.value !== null
  return hasContent || hasMedia
})

const isSelectedVideo = computed(() => selectedFile.value?.type.startsWith('video/') ?? false)

function clearPreview() {
  if (previewObjectUrl) {
    URL.revokeObjectURL(previewObjectUrl)

    previewObjectUrl = null
  }
  previewUrl.value = null
}

function resetMedia() {
  clearPreview()
  selectedFile.value = null
  lastUploadedMediaId.value = null
  phase.value = 'idle'
  progress.value = 0
  errorMsg.value = ''
}

function onPickMedia() {
  fileInput.value?.click()
}

function onFileSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (phase.value === 'uploading' || phase.value === 'publishing') return
  clearPreview()
  selectedFile.value = file
  progress.value = 0
  phase.value = 'idle'
  errorMsg.value = ''
  previewObjectUrl = URL.createObjectURL(file)
  previewUrl.value = previewObjectUrl
  // Reset so re-selecting the same file still fires change
  input.value = ''
}

async function doUpload() {
  const file = selectedFile.value
  if (!file || phase.value === 'uploading') return
  errorMsg.value = ''
  phase.value = 'uploading'
  progress.value = 0
  await nextTick()
  try {
    const media = await uploadMedia(file, (p) => {
      progress.value = p
      if (progressRef.value) {
        gsap.to(progressRef.value, { width: `${p * 100}%`, duration: 0.15, ease: 'power1.out' })
      }
    })
    lastUploadedMediaId.value = media.id
    progress.value = 1
    if (progressRef.value) {
      gsap.to(progressRef.value, { width: '100%', duration: 0.2, ease: 'power1.out' })
    }
    phase.value = 'uploaded'
  } catch {
    phase.value = 'error'
    errorMsg.value = 'upload failed — check connection and retry'
  }
}

async function submit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  errorMsg.value = ''
  phase.value = 'publishing'
  try {
    const created = await createPost({
      postCategory: isComment.value ? 'COMMENT' : 'NORMAL',
      content: content.value.trim() || null,
      mediaId: lastUploadedMediaId.value,
      parentId: isComment.value ? props.parentId : null,
      repostOfId: null,
    })
    emit('created', created.id)
    close()
  } catch (e) {
    phase.value = 'error'
    const err = e as ApiError
    errorMsg.value = err.detail ?? err.title ?? 'failed to publish post'
  } finally {
    submitting.value = false
  }
}

function onOverlayClick(e: MouseEvent) {
  if (e.target === e.currentTarget) close()
}

onMounted(() => {
  if (!panelRef.value) return
  gsap.fromTo(
    panelRef.value,
    { opacity: 0, y: 8, scale: 0.98 },
    { opacity: 1, y:  0, scale: 1, duration: 0.22, ease: 'power2.out' },
  )
})

function close() {
  if (!panelRef.value) {
    emit('close')
    return
  }
  gsap.to(panelRef.value, {
    opacity:  0,
    y:  0,
    scale: 0.98,
    duration:  0.18,
    ease: 'power2.in',
    onComplete: () => emit('close'),
  })
}
</script>

<template>
  <div class="post-create-overlay" data-testid="post-create-overlay" @click="onOverlayClick">
    <section ref="panelRef" class="panel post-create-panel" data-testid="post-create-modal">
      <header class="post-create__header">
        <span class="post-create__title">{{ isComment ? 'NEW COMMENT' : 'NEW POST' }}</span>
        <button class="btn post-create__close" type="button" data-testid="post-create-close" @click="close">
          ✕
        </button>
      </header>

      <div class="post-create__body">
        <label class="field">
          <span class="field-label">content</span>
          <textarea
            v-model="content"
            class="field-input field-textarea"
            :placeholder="isComment ? 'write a comment…' : 'what\'s happening?'"
            rows="4"
            data-testid="post-create-content"
            :disabled="phase === 'uploading' || phase === 'publishing'"
          />
        </label>

        <div class="field">
          <span class="field-label">media</span>

          <div v-if="previewUrl" class="post-create__preview" data-testid="post-create-preview">
            <video
              v-if="isSelectedVideo"
              :src="previewUrl"
              controls
              preload="metadata"
              playsinline
              class="post-create__preview-video"
              data-testid="post-create-preview-video"
            />
            <img
              v-else
              :src="previewUrl"
              alt="selected media preview"
              class="post-create__preview-img"
              data-testid="post-create-preview-img"
            />
            <div class="post-create__preview-meta">
              <span class="post-create__preview-name">{{ selectedFile?.name }}</span>
              <button
                class="btn post-create__preview-remove"
                type="button"
                data-testid="post-create-media-remove"
                :disabled="phase === 'uploading' || phase === 'publishing'"
                @click="resetMedia"
              >
                remove
              </button>
            </div>
          </div>

          <div v-else class="post-create__attach" data-testid="post-create-attach">
            <span class="post-create__attach-hint">attach a media file — it uploads first, then the post goes live</span>
            <button
              class="btn"
              type="button"
              data-testid="post-create-media-pick"
              :disabled="phase === 'uploading' || phase === 'publishing'"
              @click="onPickMedia"
            >
              + choose file
            </button>
          </div>

          <input
            ref="fileInput"
            type="file"
            accept="image/*,video/*"
            class="post-create__file-input"
            data-testid="post-create-media-input"
            @change="onFileSelected"
          />

          <template v-if="selectedFile && phase === 'idle'">
            <button
              class="btn post-create__upload-btn"
              type="button"
              data-testid="post-create-media-upload"
              @click="doUpload"
            >
              ⇪ upload media
            </button>
          </template>

          <div v-if="phase === 'uploading'" class="progress" data-testid="post-upload-progress">
            <div ref="progressRef" class="progress__bar" data-testid="post-upload-progress-bar"></div>
            <span class="progress__pct" data-testid="post-upload-progress-pct">{{ Math.round(progress * 100) }}%</span>
            <span class="progress__scan" aria-hidden="true"></span>
          </div>

          <p v-if="phase === 'uploaded'" class="post-create__status post-create__status--ok" data-testid="post-upload-done">
            ✓ media uploaded — ready to post
          </p>

          <p v-if="phase === 'error'" class="post-create__status post-create__status--err" data-testid="post-create-error">
            {{ errorMsg }}
          </p>
        </div>

        <p class="post-create__hint">content, media, or both — upload completes before the post submits</p>
      </div>

      <footer class="post-create__footer">
        <button class="btn" type="button" @click="close" :disabled="phase === 'uploading' || phase === 'publishing'">
          cancel
        </button>
        <button
          class="btn btn-primary"
          type="button"
          data-testid="post-create-submit"
          :disabled="!canSubmit"
          @click="submit"
        >
          {{ phase === 'publishing' ? 'publishing…' : 'post' }}
        </button>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.post-create-overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: var(--sarv-space-4);
  background: color-mix(in srgb, var(--sarv-bg) 70%, transparent);
  backdrop-filter: blur(2px);
}

.post-create-panel {
  width: 100%;
  max-width: 560px;
  max-height: 85vh;
  overflow-y: auto;
  display: grid;
  gap: 1px;
  background: var(--sarv-border);
  border-color: var(--sarv-green-dark);
  box-shadow: var(--sarv-glow);
}

.post-create__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sarv-space-3) var(--sarv-space-4);
  background: var(--sarv-panel);
}

.post-create__title {
  font-size: 12px;
  letter-spacing: 0.14em;
  color: var(--sarv-green);
}

.post-create__close {
  padding: 4px 8px;
  font-size: 12px;
}

.post-create__body {
  display: grid;
  gap: var(--sarv-space-4);
  padding: var(--sarv-space-4);
  background: var(--sarv-panel);
}

.field {
  display: grid;
  gap: var(--sarv-space-1);
}

.field-label {
  font-size: 11px;
  letter-spacing:  0.1em;
  text-transform: uppercase;
  color: var(--sarv-text-dim);
}

.field-input {
  width: 100%;
  padding: 10px 12px;
  background: var(--sarv-bg);
  border:  01px solid var(--sarv-border-bright);
  color: var(--sarv-text);
  outline: none;
}

.field-textarea {
  min-height: 96px;
  resize: vertical;
}

.post-create__attach {
  display: grid;
  gap: var(--sarv-space-2);
  padding: var(--sarv-space-3);
  background: var(--sarv-bg);
  border:  01px dashed var(--sarv-border-bright);
  color: var(--sarv-text-dim);
  font-size: 12px;
  text-align: center;
  justify-items: center;
}

.post-create__preview {
  display: grid;
  gap: var(--sarv-space-2);
  padding: var(--sarv-space-2);
  background: var(--sarv-bg);
  border:  01px solid var(--sarv-border-bright);
}

.post-create__preview-img,
.post-create__preview-video {
  display: block;
  width: 100%;
  max-height: 200px;
  object-fit: contain;
  background: #000;
}

.post-create__preview-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--sarv-space-2);
}

.post-create__preview-name {
  font-size: 11px;
  color: var(--sarv-text-dim);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.post-create__file-input {
  display: none;
}

.post-create__upload-btn {
  justify-content: center;
  width: 100%;
}

/* Pixelated / matrix progress bar */
.progress {
  position: relative;
  height: 22px;
  background: var(--sarv-bg);
  border:  01px solid var(--sarv-border-bright);
  overflow: hidden;
}

.progress__bar {
  height: 100%;
  width: 0%;
  background: repeating-linear-gradient(
    90deg,
    var(--sarv-green),
    var(--sarv-green) 8px,
    var(--sarv-green-dark) 8px,
    var(--sarv-green-dark) 10px
  );
  box-shadow: var(--sarv-glow);
}

.progress__pct {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 12px;
  font-weight: 700;
  letter-spacing:  0.1em;
  color: var(--sarv-bg);
  mix-blend-mode: screen;
}

/* Scanning highlight sweeping across the bar */
.progress__scan {
  position: absolute;
  top: 0;
  left:  0;
  width:  040px;
  height:  0100%;
  background: linear-gradient(90deg, transparent, color-mix(in srgb, var(--sarv-green-bright) 60%, transparent), transparent);
  animation: sarv-scan 1.1s linear infinite;
}

@keyframes sarv-scan {
  from { transform: translateX(-40px); }
  to { transform: translateX(560px); }
}

.post-create__status {
  font-size:  011px;
  text-align: center;
}

.post-create__status--ok {
  color: var(--sarv-green);
}

.post-create__status--err {
  color: var(--sarv-red);
}

.post-create__hint {
  font-size: 11px;
  color: var(--sarv-text-faint);
  text-align: center;
}

.post-create__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--sarv-space-3);
  padding: var(--sarv-space-3) var(--sarv-space-4);
  background: var(--sarv-panel);
  border-top:  01px solid var(--sarv-border);
}

@media (max-width: 640px) {
  .post-create-overlay {
    place-items: end center;
    padding: 0;
  }

  .post-create-panel {
    max-width: 100%;
    max-height: 92dvh;
    border-left: none;
    border-right: none;
    border-bottom: none;
    border-top: 2px solid var(--sarv-green-dark);
  }

  .post-create__footer {
    position: sticky;
    bottom: 0;
    padding-bottom: calc(var(--sarv-space-3) + env(safe-area-inset-bottom));
  }

  .post-create__footer .btn {
    flex: 1;
    min-height: 48px;
    justify-content: center;
  }
}
</style>
