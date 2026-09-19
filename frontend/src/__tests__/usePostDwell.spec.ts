import { h, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

import { MAX_DWELL_MS, usePostDwell, type UsePostDwellOptions } from '@/composables/usePostDwell'
import type { PostDwellPayload } from '@/api/posts'

function mountDwell(overrides: Partial<UsePostDwellOptions> = {}, id = 7) {
  const report = vi.fn<(postId: number, payload: PostDwellPayload) => Promise<void>>()
  let t = 0
  const wrapper = mount({
    setup() {
      usePostDwell(id, {
        source: 'DETAIL',
        report,
        now: () => t,
        ...overrides,
      })
      return () => h('div')
    },
  })
  return { wrapper, report, advance: (ms: number) => (t += ms) }
}

function setHidden(hidden: boolean) {
  Object.defineProperty(document, 'hidden', { value: hidden, configurable: true })
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('usePostDwell', () => {
  beforeEach(() => {
    Object.defineProperty(document, 'hidden', { value: false, configurable: true })
  })

  it('reports accumulated visible time once on unmount', () => {
    const { wrapper, report, advance } = mountDwell()

    advance(2500)
    wrapper.unmount()

    expect(report).toHaveBeenCalledOnce()
    expect(report).toHaveBeenCalledWith(7, { durationMs: 2500, source: 'DETAIL' }, {})
  })

  it('pauses while the tab is hidden', () => {
    const { wrapper, report, advance } = mountDwell()

    advance(1000)
    setHidden(true)
    advance(5000)
    setHidden(false)
    advance(1000)
    wrapper.unmount()

    expect(report).toHaveBeenCalledWith(7, { durationMs: 2000, source: 'DETAIL' }, {})
  })

  it('reports only once across pagehide and unmount, via keepalive', () => {
    const { wrapper, report, advance } = mountDwell()

    advance(800)
    window.dispatchEvent(new Event('pagehide'))
    wrapper.unmount()

    expect(report).toHaveBeenCalledOnce()
    expect(report).toHaveBeenCalledWith(7, { durationMs: 800, source: 'DETAIL' }, { keepalive: true })
  })

  it('skips the beacon below minDurationMs', () => {
    const { wrapper, report, advance } = mountDwell({ minDurationMs: 1000 })

    advance(500)
    wrapper.unmount()

    expect(report).not.toHaveBeenCalled()
  })

  it('caps the reported duration at MAX_DWELL_MS', () => {
    const { wrapper, report, advance } = mountDwell()

    advance(MAX_DWELL_MS + 5000)
    wrapper.unmount()

    expect(report).toHaveBeenCalledWith(7, { durationMs: MAX_DWELL_MS, source: 'DETAIL' }, {})
  })

  it('attributes accumulated time to the previous post on in-place navigation', async () => {
    const id = ref(7)
    const report = vi.fn<(postId: number, payload: PostDwellPayload) => Promise<void>>()
    let t = 0
    const wrapper = mount({
      setup() {
        usePostDwell(id, { source: 'DETAIL', report, now: () => t })
        return () => h('div')
      },
    })

    t += 1000
    id.value = 8
    await wrapper.vm.$nextTick()
    t += 500
    wrapper.unmount()

    expect(report).toHaveBeenCalledTimes(2)
    expect(report).toHaveBeenNthCalledWith(1, 7, { durationMs: 1000, source: 'DETAIL' }, {})
    expect(report).toHaveBeenNthCalledWith(2, 8, { durationMs: 500, source: 'DETAIL' }, {})
  })
})
