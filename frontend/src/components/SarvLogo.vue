<script setup lang="ts">
import { onMounted, ref } from 'vue'
import gsap from 'gsap'

const chars = ['S', 'A', 'R', 'V'] as const
const rootRef = ref<HTMLElement | null>(null)

// Matrix binary scramble characters
const GLYPHS = '01'

function scrambleChars(el: HTMLElement, finalChar: string, duration: number) {
  const proxy = { t: 0 }
  const len = GLYPHS.length

  gsap.to(proxy, {
    t: 1,
    duration,
    ease: 'none',
    onUpdate() {
      // Show random glyphs until the last 20% where final char locks in
      if (proxy.t < 0.8) {
        const g = GLYPHS[Math.floor(Math.random() * len)] ?? '0'
        el.textContent = g
        el.style.opacity = '0.7'
      } else {
        el.textContent = finalChar
        el.style.opacity = '1'
      }
    },
    onComplete() {
      el.textContent = finalChar
      el.style.opacity = '1'
    },
  })
}

onMounted(() => {
  if (!rootRef.value) return

  const els = Array.from(rootRef.value.querySelectorAll<HTMLElement>('.sarv-logo__char'))

  // Initial state: hidden and offset
  gsap.set(els, { opacity: 0, y: -6 })

  // Reveal char by char, each with a scramble burst, preserving matrix/hacker feel.
  // No ScrambleTextPlugin needed — manual scramble above uses only gsap core.
  const tl = gsap.timeline()

  els.forEach((el, i) => {
    const finalChar = chars[i] ?? ''
    tl.call(() => scrambleChars(el, finalChar, 0.8), undefined, i * 0.3)
    tl.to(el, { opacity: 1, y: 0, duration: 0.45, ease: 'steps(6)' }, i * 0.3)
  })

  // Subtle glow pulse after construction completes — reinforces computer/terminal identity
  tl.to(
    rootRef.value,
    { filter: 'drop-shadow(0 0 8px var(--sarv-green))', duration: 0.6, ease: 'power1.out' },
    '+=0.25',
  )
  tl.to(rootRef.value, { filter: 'drop-shadow(0 0 4px var(--sarv-green))', duration: 0.8 }, '+=0.2')
})
</script>

<template>
  <div ref="rootRef" class="sarv-logo" data-testid="sarv-logo" aria-label="Sarv">
    <span
      v-for="(c, i) in chars"
      :key="i"
      class="sarv-logo__char"
      :data-testid="`sarv-char-${i}`"
      >{{ c }}</span
    >
    <span class="sarv-logo__cursor" aria-hidden="true">█</span>
  </div>
</template>

<style scoped>
.sarv-logo {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-family: var(--sarv-font-mono);
  font-size: 28px;
  font-weight: 800;
  letter-spacing: 0.32em;
  color: var(--sarv-green);
  text-shadow: var(--sarv-glow);
  line-height: 1;
  user-select: none;
}

.sarv-logo__char {
  display: inline-block;
  min-width: 0.9em;
  text-align: center;
}

.sarv-logo__cursor {
  margin-left: 4px;
  color: var(--sarv-green);
  font-size: 20px;
  animation: sarv-blink 1s steps(1) infinite;
}
</style>
