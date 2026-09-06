<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import gsap from 'gsap'

const props = withDefaults(
  defineProps<{
    text?: string
    effect?: string
    duration?: number
  }>(),
  {
    text: 'SARV',
    effect: undefined,
    duration: undefined,
  },
)

const EFFECTS = ['binarypath', 'decrypt', 'errorcorrect', 'matrix'] as const
type EffectName = (typeof EFFECTS)[number]
const GLYPHS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@#$%&*<>[]{}'
const MATRIX_COLORS = ['#0b3d20', '#00c853', '#b9ffcb']

const chars = [...props.text]
const displayed = ref<string[]>([...chars])
const activeEffect = ref<EffectName>('decrypt')
const rootRef = ref<HTMLElement | null>(null)
const rainParticles = ref<{ id: number; x: number; char: string }[]>([])

let intervals: number[] = []
let timeouts: number[] = []
let timeline: gsap.core.Timeline | null = null

function clamp(v: number, min = 0, max = 1) {
  return Math.min(max, Math.max(min, v))
}

function glyph(value: number): string {
  return GLYPHS[Math.abs(Math.imul(value + 1, 2654435761)) % GLYPHS.length] ?? 'A'
}

function shuffle<T>(items: T[]): T[] {
  const a = [...items]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    const tmp = a[i]!
    a[i] = a[j]!
    a[j] = tmp
  }
  return a
}

function colorAt(stops: string[], progress: number): string {
  if (stops.length === 0) return '#ffffff'
  if (stops.length === 1) return stops[0]!
  const p = clamp(progress) * (stops.length - 1)
  const idx = Math.min(stops.length - 2, Math.floor(p))
  const from = stops[idx]!
  const to = stops[idx + 1]!
  const t = p - idx
  return mixHex(from, to, t)
}

function mixHex(from: string, to: string, t: number): string {
  const a = hexToRgb(from)
  const b = hexToRgb(to)
  if (!a || !b) return from
  const r = Math.round(a[0] + (b[0] - a[0]) * t)
  const g = Math.round(a[1] + (b[1] - a[1]) * t)
  const bl = Math.round(a[2] + (b[2] - a[2]) * t)
  return `rgb(${r}, ${g}, ${bl})`
}

function hexToRgb(hex: string): [number, number, number] | null {
  const m = hex.trim().replace('#', '')
  const full = m.length === 3 ? m.split('').map((c) => c + c).join('') : m
  if (full.length !== 6) return null
  return [
    Number.parseInt(full.slice(0, 2), 16),
    Number.parseInt(full.slice(2, 4), 16),
    Number.parseInt(full.slice(4, 6), 16),
  ]
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

function clearTimers() {
  intervals.forEach((id) => clearInterval(id))
  timeouts.forEach((id) => clearTimeout(id))
  intervals = []
  timeouts = []
  if (timeline) {
    timeline.kill()
    timeline = null
  }
}

function elFor(index: number): HTMLElement | null {
  if (!rootRef.value) return null
  return rootRef.value.querySelector<HTMLElement>(`[data-char-index="${index}"]`)
}

function resetChars() {
  displayed.value = [...chars]
  if (!rootRef.value) return
  const els = Array.from(rootRef.value.querySelectorAll<HTMLElement>('[data-char-index]'))
  gsap.set(els, { clearProps: 'all' })
  els.forEach((el) => {
    el.style.color = ''
    el.style.textShadow = ''
    el.style.opacity = '1'
  })
}

function runBinaryPath() {
  const duration = props.duration ?? 2200
  resetChars()
  const els = chars.map((_, i) => elFor(i)).filter(Boolean) as HTMLElement[]
  if (els.length === 0) return

  // Each char gets a random edge offset to travel from
  const plans = chars.map(() => {
    const angle = Math.random() * Math.PI * 2
    const distance = 34 + Math.random() * 36
    return {
      x: Math.cos(angle) * distance,
      y: Math.sin(angle) * distance,
      binary: Math.random() > 0.5 ? '1' : '0',
    }
  })

  // Initialize from edge positions with binary chars, staggered
  els.forEach((el, i) => {
    const plan = plans[i]!
    gsap.set(el, { x: plan.x, y: plan.y, opacity: 0 })
    displayed.value[i] = plan.binary
  })

  // Binary flicker until settle — matches tte local<0.82 shows 0/1 green
  els.forEach((el, i) => {
    const plan = plans[i]!
    const staggerDelay = (i / Math.max(1, chars.length - 1)) * 0.35
    const localDuration = duration / 1000
    // flicker interval
    const intId = window.setInterval(() => {
      // find progress approx via time elapsed externally? simplified: keep binary until near end
      if (el.dataset.settled === '1') return
      displayed.value[i] = Math.random() > 0.5 ? '1' : '0'
      el.style.color = '#55ff99'
      el.style.textShadow = '0 0 8px #55ff99'
    }, 60)
    intervals.push(intId)
    const settleAt = window.setTimeout(
      () => {
        displayed.value[i] = chars[i]!
        el.dataset.settled = '1'
        el.style.color = 'var(--sarv-green)'
        el.style.textShadow = 'var(--sarv-glow)'
        clearInterval(intId)
      },
      staggerDelay * 1000 + localDuration * 0.82 * 1000,
    )
    timeouts.push(settleAt)
  })

  timeline = gsap.timeline()
  els.forEach((el, i) => {
    const delay = (i / Math.max(1, chars.length - 1)) * 0.35
    timeline!.to(
      el,
      {
        x: 0,
        y: 0,
        opacity: 1,
        duration: duration / 1000 * 0.6,
        ease: 'power3.out',
        delay,
      },
      0,
    )
  })
  // finalize ensure settled
  timeouts.push(
    window.setTimeout(() => {
      clearTimers()
      displayed.value = [...chars]
      els.forEach((el) => {
        el.style.color = 'var(--sarv-green)'
        el.style.textShadow = 'var(--sarv-glow)'
      })
    }, duration),
  )
}

function runDecrypt() {
  const duration = props.duration ?? 2200
  resetChars()
  const els = chars.map((_, i) => elFor(i)).filter(Boolean) as HTMLElement[]
  if (els.length === 0) return
  const order = shuffle(chars.map((_, i) => i))
  const ranks = new Map(order.map((idx, rank) => [idx, rank]))

  els.forEach((el, i) => {
    const rank = ranks.get(i) ?? 0
    const settleAt = 0.18 + (rank / Math.max(1, chars.length)) * 0.72
    const settleMs = settleAt * duration
    el.style.color = '#5a7d8a'
    el.style.textShadow = '0 0 7px rgba(90,125,138,0.8)'
    // tick every 45ms like tte
    const intId = window.setInterval(() => {
      if (el.dataset.settled === '1') return
      const tick = Math.floor(Date.now() / 45)
      displayed.value[i] = glyph(i + tick * 17)
    }, 45)
    intervals.push(intId)
    timeouts.push(
      window.setTimeout(() => {
        displayed.value[i] = chars[i]!
        el.dataset.settled = '1'
        el.style.color = 'var(--sarv-green)'
        el.style.textShadow = 'var(--sarv-glow)'
        clearInterval(intId)
      }, settleMs),
    )
  })

  // fade-in overall
  gsap.set(els, { opacity: 0.2 })
  timeline = gsap.timeline()
  timeline.to(els, { opacity: 1, duration: duration / 1000, ease: 'none' }, 0)

  timeouts.push(
    window.setTimeout(() => {
      displayed.value = [...chars]
    }, duration),
  )
}

function runErrorCorrect() {
  const duration = props.duration ?? 2300
  resetChars()
  const els = chars.map((_, i) => elFor(i)).filter(Boolean) as HTMLElement[]
  if (els.length === 0) return
  const order = shuffle(chars.map((_, i) => i))
  const ranks = new Map(order.map((idx, rank) => [idx, rank]))

  // Initially show random glyphs in red for all
  els.forEach((el, i) => {
    displayed.value[i] = glyph(i + Math.floor(Math.random() * 1000))
    el.style.color = '#ff5577'
    el.style.textShadow = '0 0 7px #ff5577'
    const intId = window.setInterval(() => {
      if (el.dataset.settled === '1') return
      displayed.value[i] = glyph(i + Math.floor(Date.now() / 90))
    }, 90)
    intervals.push(intId)
  })

  order.forEach((idx, rank) => {
    const el = elFor(idx)
    if (!el) return
    const correctedAt = ((rank + 1) / order.length) * duration
    timeouts.push(
      window.setTimeout(() => {
        displayed.value[idx] = chars[idx]!
        el.dataset.settled = '1'
        el.style.color = 'var(--sarv-green)'
        el.style.textShadow = 'var(--sarv-glow)'
        // flash correction
        gsap.fromTo(el, { scale: 1.15 }, { scale: 1, duration: 0.18, ease: 'power2.out' })
      }, correctedAt),
    )
  })

  timeline = gsap.timeline()
  timeline.set(els, { opacity: 1 })
}

function runMatrix() {
  const duration = props.duration ?? 2800
  resetChars()
  const els = chars.map((_, i) => elFor(i)).filter(Boolean) as HTMLElement[]
  if (els.length === 0) return

  const plans = chars.map(() => Math.random())
  // rain particles behind title — binary rain until settled
  rainParticles.value = Array.from({ length: 10 }, (_, id) => ({
    id,
    x: Math.random() * 100,
    char: Math.random() > 0.5 ? '1' : '0',
  }))

  els.forEach((el, i) => {
    const value = plans[i]!
    const settleAt = 0.35 + value * 0.55
    const settleMs = settleAt * duration
    el.style.color = colorAt(MATRIX_COLORS, value)
    el.style.textShadow = '0 0 7px currentColor'
    const intId = window.setInterval(() => {
      if (el.dataset.settled === '1') return
      const tick = Math.floor(Date.now() / 70)
      displayed.value[i] = glyph(i + tick)
      el.style.color = colorAt(MATRIX_COLORS, Math.random())
    }, 70)
    intervals.push(intId)
    timeouts.push(
      window.setTimeout(() => {
        displayed.value[i] = chars[i]!
        el.dataset.settled = '1'
        el.style.color = 'var(--sarv-green)'
        el.style.textShadow = 'var(--sarv-glow)'
        clearInterval(intId)
      }, settleMs),
    )
  })

  // animate rain particles falling
  nextTick(() => {
    const rainEls = rootRef.value?.querySelectorAll<HTMLElement>('.auth-brand__rain-char')
    if (!rainEls) return
    gsap.set(rainEls, { y: -22, opacity: 0.9 })
    timeline = gsap.timeline()
    rainEls.forEach((r, idx) => {
      timeline!.to(
        r,
        {
          y: 36 + Math.random() * 12,
          opacity: 0,
          duration: 0.7 + Math.random() * 0.5,
          ease: 'none',
          repeat: 3,
          delay: idx * 0.08,
        },
        0,
      )
    })
  })

  // fade rain out at end
  timeouts.push(
    window.setTimeout(() => {
      rainParticles.value = []
      displayed.value = [...chars]
    }, duration),
  )

  gsap.set(els, { opacity: 1 })
}

function runEffect(name: EffectName) {
  clearTimers()
  // reset settled markers
  rootRef.value?.querySelectorAll<HTMLElement>('[data-char-index]').forEach((el) => {
    delete el.dataset.settled
  })
  switch (name) {
    case 'binarypath':
      runBinaryPath()
      break
    case 'decrypt':
      runDecrypt()
      break
    case 'errorcorrect':
      runErrorCorrect()
      break
    case 'matrix':
      runMatrix()
      break
  }
}

onMounted(async () => {
  const chosen = (props.effect as EffectName | undefined) ?? (EFFECTS[Math.floor(Math.random() * EFFECTS.length)] as EffectName)
  activeEffect.value = EFFECTS.includes(chosen) ? chosen : 'decrypt'
  await nextTick()

  if (prefersReducedMotion()) {
    displayed.value = [...chars]
    return
  }

  // jsdom / vitest guard — gsap still works but intervals are cheap
  const els = rootRef.value?.querySelectorAll<HTMLElement>('[data-char-index]')
  if (!els || els.length === 0) return

  gsap.set(Array.from(els), { opacity: 0 })
  // small stagger to ensure DOM is painted before effect timeline starts
  timeouts.push(window.setTimeout(() => runEffect(activeEffect.value), 60))
})

onBeforeUnmount(() => {
  clearTimers()
})

defineExpose({ activeEffect, runEffect })
</script>

<template>
  <h1
    ref="rootRef"
    class="auth-brand"
    data-testid="auth-title-animation"
    :data-effect="activeEffect"
    aria-label="Sarv"
  >
    <span
      v-for="(c, i) in chars"
      :key="i"
      class="auth-brand__char"
      :data-testid="`auth-char-${i}`"
      :data-char-index="i"
      >{{ displayed[i] }}</span
    >
    <span class="auth-brand__cursor" aria-hidden="true">█</span>
    <span
      v-if="rainParticles.length"
      class="auth-brand__rain"
      aria-hidden="true"
      data-testid="auth-rain"
    >
      <span
        v-for="p in rainParticles"
        :key="p.id"
        class="auth-brand__rain-char"
        :style="{ left: p.x + '%' }"
        >{{ p.char }}</span
      >
    </span>
  </h1>
</template>

<style scoped>
.auth-brand {
  position: relative;
  display: block;
  font-size: 2rem;
  letter-spacing: 0.35em;
  color: var(--sarv-green);
  text-shadow: var(--sarv-glow);
  text-align: center;
  line-height: 1;
  user-select: none;
  overflow: visible;
}

.auth-brand__char {
  display: inline-block;
  min-width: 0.9em;
  text-align: center;
  will-change: transform, opacity;
}

.auth-brand__cursor {
  margin-left: 6px;
  color: var(--sarv-green);
  font-size: 1.1rem;
  vertical-align: middle;
  animation: sarv-blink 1s steps(1) infinite;
}

.auth-brand__rain {
  position: absolute;
  inset: -6px 0 -10px 0;
  pointer-events: none;
  overflow: hidden;
}

.auth-brand__rain-char {
  position: absolute;
  top: 0;
  font-size: 11px;
  color: var(--sarv-green-dim);
  text-shadow: 0 0 6px var(--sarv-green);
  opacity: 0.9;
}
</style>
