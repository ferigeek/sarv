import { onBeforeUnmount, onMounted, watch, type Ref } from 'vue'

import { reportPostDwell, type DwellSource, type PostDwellPayload } from '@/api/posts'

/* Upper bound matching the backend @Max(1_800_000) validation. */
export const MAX_DWELL_MS = 1_800_000

export interface UsePostDwellOptions {
  source: DwellSource
  /* When given, time only accumulates while the element is >=50% visible. */
  target?: Ref<HTMLElement | null>
  /* Skips the beacon when visible time is below this (backend requires >= 1). */
  minDurationMs?: number
  enabled?: boolean
  report?: (postId: number, payload: PostDwellPayload) => Promise<void> | void
  now?: () => number
}

/*
 * Measures visible time on a post and sends a single dwell beacon on
 * unmount / pagehide. Hidden tabs and (with `target`) off-screen cards
 * pause accumulation. Best-effort: never throws.
 */
export function usePostDwell(
  postId: number | Ref<number> | (() => number),
  options: UsePostDwellOptions,
): void {
  const { source, target, minDurationMs = 1, enabled = true } = options
  const report = options.report ?? reportPostDwell
  const now = options.now ?? (() => Date.now())

  const resolveId = () =>
    typeof postId === 'function' ? (postId as () => number)() : typeof postId === 'object' ? postId.value : postId

  /*
   * Snapshot the id while the component is active: reactive sources such as
   * route params may already be torn down when onBeforeUnmount runs, so the
   * id must not be resolved lazily at flush time.
   */
  let activeId = resolveId()

  let accumulated = 0
  let windowStart: number | null = null
  let visible = target ? false : true
  let reported = false
  let observer: IntersectionObserver | null = null

  function resume() {
    if (!enabled || reported || !visible || windowStart !== null) return
    if (typeof document !== 'undefined' && document.hidden) return
    windowStart = now()
  }

  function pause() {
    if (windowStart === null) return
    accumulated += Math.max(0, now() - windowStart)
    windowStart = null
  }

  function flush() {
    flushWith(activeId)
  }

  function flushWith(id: number) {
    pause()
    if (!enabled || reported) return
    reported = true
    if (typeof id !== 'number' || !Number.isFinite(id)) return
    const durationMs = Math.min(Math.floor(accumulated), MAX_DWELL_MS)
    if (durationMs < minDurationMs) return
    // Guarded for partially-mocked api modules in tests; real impl never rejects.
    if (typeof report !== 'function') return
    try {
      const result = report(id, { durationMs, source })
      if (result && typeof (result as Promise<void>).catch === 'function') {
        ;(result as Promise<void>).catch(() => {})
      }
    } catch {
      // ignore — dwell reporting is best-effort
    }
  }

  function onVisibilityChange() {
    if (typeof document !== 'undefined' && document.hidden) pause()
    else resume()
  }

  function onPageHide() {
    flush()
  }

  // In-place navigation (e.g. /post/5 -> /post/6 reusing the view):
  // attribute accumulated time to the old post and restart for the new one.
  watch(resolveId, (next, prev) => {
    if (next === prev) return
    flushWith(prev)
    activeId = next
    accumulated = 0
    reported = false
    resume()
  })

  onMounted(() => {
    if (!enabled) return
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', onVisibilityChange)
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('pagehide', onPageHide)
    }
    if (target?.value && typeof IntersectionObserver !== 'undefined') {
      visible = false
      observer = new IntersectionObserver(
        (entries) => {
          const entry = entries[0]
          if (!entry) return
          visible = entry.isIntersecting
          if (visible) resume()
          else pause()
        },
        { threshold: 0.5 },
      )
      observer.observe(target.value)
    } else {
      resume()
    }
  })

  onBeforeUnmount(() => {
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', onVisibilityChange)
    }
    if (typeof window !== 'undefined') {
      window.removeEventListener('pagehide', onPageHide)
    }
    observer?.disconnect()
    observer = null
    flush()
  })
}
