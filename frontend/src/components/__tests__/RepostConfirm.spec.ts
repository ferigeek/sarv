import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import type { PostResponse } from '@/types/api'

const postSpy = vi.fn<(url: string, payload: unknown) => Promise<{ data: PostResponse }>>()

vi.mock('@/api/client', () => ({
  apiClient: {
    get: vi.fn<() => Promise<unknown>>(),
    post: (...args: unknown[]) => postSpy(...(args as [string, unknown])),
    put: vi.fn<() => Promise<unknown>>(),
    delete: vi.fn<() => Promise<unknown>>(),
  },
}))

vi.mock('gsap', () => {
  const mockGsap = {
    to: vi.fn((_t: unknown, vars: { onComplete?: () => void }) => vars.onComplete?.()),
    fromTo: vi.fn(),
  }
  return { default: mockGsap, ...mockGsap }
})

import RepostConfirm from '../RepostConfirm.vue'

function makePost(id: number, content: string | null = 'hello world'): PostResponse {
  return {
    id,
    userId: 10,
    postCategory: 'NORMAL',
    content,
    createdAt: '2026-09-02T10:00:00+00:00',
    updatedAt: null,
    mediaId: null,
    repostOfId: null,
    parentId: null,
    viewCount: 1,
    likeCount: 0,
    dislikeCount: 0,
    commentCount: 0,
  }
}

describe('RepostConfirm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows the author and a snippet of the post', async () => {
    const wrapper = mount(RepostConfirm, { props: { post: makePost(5), authorLabel: 'Bob' } })
    await flushPromises()

    expect(wrapper.find('[data-testid="repost-confirm-modal"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Bob')
    expect(wrapper.find('[data-testid="repost-confirm-snippet"]').text()).toBe('hello world')
  })

  it('sends a REPOST payload with null content and the referenced id', async () => {
    postSpy.mockResolvedValue({ data: makePost(77, null) })
    const wrapper = mount(RepostConfirm, { props: { post: makePost(5), authorLabel: 'Bob' } })
    await flushPromises()

    await wrapper.find('[data-testid="repost-confirm-submit"]').trigger('click')
    await flushPromises()

    expect(postSpy).toHaveBeenCalledWith('/posts', {
      postCategory: 'REPOST',
      content: null,
      mediaId: null,
      parentId: null,
      repostOfId: 5,
    })
    expect(wrapper.emitted('reposted')?.[0]).toEqual([77])
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('shows an error when reposting fails', async () => {
    postSpy.mockRejectedValue({ detail: 'gone' })
    const wrapper = mount(RepostConfirm, { props: { post: makePost(5), authorLabel: 'Bob' } })
    await flushPromises()

    await wrapper.find('[data-testid="repost-confirm-submit"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="repost-confirm-error"]').text()).toContain('gone')
    expect(wrapper.emitted('reposted')).toBeFalsy()
  })

  it('cancel closes without calling the API', async () => {
    const wrapper = mount(RepostConfirm, { props: { post: makePost(5), authorLabel: 'Bob' } })
    await flushPromises()

    await wrapper.find('[data-testid="repost-confirm-cancel"]').trigger('click')
    await flushPromises()

    expect(postSpy).not.toHaveBeenCalled()
    expect(wrapper.emitted('close')).toBeTruthy()
  })
})
