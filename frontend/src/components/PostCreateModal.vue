<script setup lang="ts">
import { onMounted, ref } from 'vue'
import gsap from 'gsap'

const emit = defineEmits<{ close: [] }>()

const panelRef = ref<HTMLElement | null>(null)

onMounted(() => {
  if (!panelRef.value) return
  gsap.fromTo(
    panelRef.value,
    { opacity: 0, y: 8, scale: 0.98 },
    { opacity: 1, y: 0, scale: 1, duration: 0.22, ease: 'power2.out' },
  )
})

function close() {
  if (!panelRef.value) {
    emit('close')
    return
  }
  gsap.to(panelRef.value, {
    opacity: 0,
    y: 8,
    scale: 0.98,
    duration: 0.18,
    ease: 'power2.in',
    onComplete: () => emit('close'),
  })
}

function onOverlayClick(e: MouseEvent) {
  if (e.target === e.currentTarget) close()
}
</script>

<template>
  <div class="post-create-overlay" data-testid="post-create-overlay" @click="onOverlayClick">
    <section ref="panelRef" class="panel post-create-panel" data-testid="post-create-modal">
      <header class="post-create__header">
        <span class="post-create__title">NEW POST</span>
        <button class="btn post-create__close" type="button" data-testid="post-create-close" @click="close">
          ✕
        </button>
      </header>

      <div class="post-create__body">
        <label class="field">
          <span class="field-label">content</span>
          <textarea
            class="field-input field-textarea"
            placeholder="what's happening? // content — upcoming in full composer"
            rows="4"
            data-testid="post-create-content"
            disabled
          />
        </label>

        <div class="field">
          <span class="field-label">media</span>
          <div class="post-create__media" data-testid="post-create-media">media attach — upcoming</div>
        </div>

        <p class="post-create__hint">post creation will be fully wired in the next phase</p>
      </div>

      <footer class="post-create__footer">
        <button class="btn" type="button" @click="close">cancel</button>
        <button class="btn btn-primary" type="button" disabled data-testid="post-create-submit">
          post
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
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--sarv-text-dim);
}

.field-input {
  width: 100%;
  padding: 10px 12px;
  background: var(--sarv-bg);
  border: 1px solid var(--sarv-border-bright);
  color: var(--sarv-text);
  outline: none;
}

.field-textarea {
  min-height: 96px;
  resize: vertical;
}

.post-create__media {
  padding: 12px;
  background: var(--sarv-bg);
  border: 1px dashed var(--sarv-border-bright);
  color: var(--sarv-text-dim);
  font-size: 12px;
  text-align: center;
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
  border-top: 1px solid var(--sarv-border);
}
</style>
