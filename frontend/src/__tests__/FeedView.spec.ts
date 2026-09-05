import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import type { Page, PostResponse, UserResponse } from '@/types/api'

vi.mock('@/api/feed', () => ({
  getRecommendedFeed: vi.fn<(pageable?: unknown) => Promise<Page<PostResponse>>>(),
  getChronologicalFeed: vi.fn<(pageable?: unknown) => Promise<Page<PostResponse>>>(),
}))

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<(query: string, pageable?: unknown) => Promise<Page<UserResponse>>>(),
}))

vi.mock('@/api/reactions', () => ({
  addReaction: vi.fn<(postId: number, type: number) => Promise<import('@/types/api').ReactionResponse>>(),
  getReaction: vi.fn<() => Promise<import('@/types/api').ReactionResponse>>(),
  removeReaction: vi.fn<(postId: number) => Promise<void>>(),
}))

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn<(file: File) => Promise<import('@/types/api').MediaResponse>>(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<import('@/types/api').MediaMetadataResponse>>(),
}))

import { getChronologicalFeed as mockGetChronologicalFeed, getRecommendedFeed as mockGetRecommendedFeed } from '@/api/feed'
import { getReaction as mockGetReaction } from '@/api/reactions'
import { getUser as mockGetUser } from '@/api/users'
import { getMediaBlob as mockGetMediaBlob } from '@/api/media'
import { getMe as mockGetMe } from '@/api/users'
import { registerPixelicons } from '@/assets/icons/pixelarticons'
import { createAppRouter } from '@/router'
import FeedView from '@/views/FeedView.vue'

registerPixelicons()

const mockedGetRecommendedFeed = vi.mocked(mockGetRecommendedFeed)
const mockedGetChronologicalFeed = vi.mocked(mockGetChronologicalFeed)
const mockedGetUser = vi.mocked(mockGetUser)
const mockedGetReaction = vi.mocked(mockGetReaction)
const mockedGetMeFn = vi.mocked(mockGetMe)
const mockedGetMediaBlob = vi.mocked(mockGetMediaBlob)

function makePost(id: number, content = `post ${id}`): PostResponse {
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

function makePage(content: PostResponse[], page = 0, totalPages = 1): Page<PostResponse> {
  return {
    content,
    page: { size: 20, number: page, totalElements: content.length, totalPages },
  }
}

describe('FeedView', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    localStorage.setItem('sarv.jwt', 'test-jwt')
    mockedGetMeFn.mockResolvedValue({
      id: 1,
      username: 'alice',
      displayName: 'Alice',
      bio: null,
      gender: 'FEMALE',
      location: null,
      profilePictureId: null,
      status: 'ACTIVE',
    })
    mockedGetUser.mockResolvedValue({
      id: 10,
      username: 'bob',
      displayName: 'Bob',
      bio: null,
      gender: 'MALE',
      location: null,
      profilePictureId: null,
      status: 'ACTIVE',
    })
    mockedGetReaction.mockResolvedValue({ likeCount: 0, dislikeCount: 0, userReaction: 0 })
    mockedGetMediaBlob.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
  })

  async function mountFeed() {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter(createMemoryHistory())
    const wrapper = mount(FeedView, {
      global: {
        plugins: [pinia, router],
      },
    })
    // FeedView is rendered via AppShell -> router; but we mount FeedView directly
    // so no need to push route
    return { wrapper, router }
  }

  it('fetches recommended feed on mount and renders posts', async () => {
    mockedGetRecommendedFeed.mockResolvedValue(makePage([makePost(1), makePost(2)]))

    const { wrapper } = await mountFeed()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(mockedGetRecommendedFeed).toHaveBeenCalledWith({ page: 0, size: 20 })
    expect(wrapper.find('[data-testid="feed-list"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid^="post-card-"]').length).toBe(2)
    expect(wrapper.find('[data-testid="post-card-1"]').exists()).toBe(true)
  })

  it('falls back to chronological when recommended fails', async () => {
    mockedGetRecommendedFeed.mockRejectedValue(new Error('service down'))
    mockedGetChronologicalFeed.mockResolvedValue(makePage([makePost(3)]))

    const { wrapper } = await mountFeed()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(mockedGetChronologicalFeed).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="post-card-3"]').exists()).toBe(true)
  })

  it('falls back to chronological when recommended is empty on first page', async () => {
    mockedGetRecommendedFeed.mockResolvedValue(makePage([]))
    mockedGetChronologicalFeed.mockResolvedValue(makePage([makePost(4)]))

    const { wrapper } = await mountFeed()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(mockedGetChronologicalFeed).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="post-card-4"]').exists()).toBe(true)
  })

  it('shows loading, empty and error states', async () => {
    // Loading
    let resolve: (v: Page<PostResponse>) => void
    mockedGetRecommendedFeed.mockReturnValue(new Promise((r) => { resolve = r }))
    const { wrapper } = await mountFeed()
    await flushPromises()
    expect(wrapper.find('[data-testid="feed-loading"]').exists()).toBe(true)

    // Resolve to empty
    resolve!(makePage([]))
    mockedGetChronologicalFeed.mockResolvedValue(makePage([]))
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()
    expect(wrapper.find('[data-testid="feed-empty"]').exists()).toBe(true)
  })

  it('shows error and retry', async () => {
    mockedGetRecommendedFeed.mockRejectedValue({ status: 500, detail: 'failed' })
    mockedGetChronologicalFeed.mockRejectedValue({ status: 500, detail: 'failed' })

    const { wrapper } = await mountFeed()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="feed-error"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="feed-error"]').text()).toContain('failed')

    mockedGetRecommendedFeed.mockResolvedValue(makePage([makePost(5)]))
    await wrapper.find('[data-testid="feed-retry"]').trigger('click')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="post-card-5"]').exists()).toBe(true)
  })

  it('paginates with load more', async () => {
    mockedGetRecommendedFeed.mockResolvedValueOnce(makePage([makePost(1), makePost(2)], 0, 2))
    mockedGetRecommendedFeed.mockResolvedValueOnce(makePage([makePost(3)], 1, 2))

    const { wrapper } = await mountFeed()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.findAll('[data-testid^="post-card-"]').length).toBe(2)
    expect(wrapper.find('[data-testid="feed-load-more"]').exists()).toBe(true)

    await wrapper.find('[data-testid="feed-load-more"]').trigger('click')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(mockedGetRecommendedFeed).toHaveBeenCalledWith({ page: 1, size: 20 })
    expect(wrapper.findAll('[data-testid^="post-card-"]').length).toBe(3)
    // After second page with 1 item < size, no more
    expect(wrapper.find('[data-testid="feed-end"]').exists()).toBe(true)
  })

  it('each post shows view, like and dislike counts', async () => {
    mockedGetRecommendedFeed.mockResolvedValue(makePage([makePost(1, 'hello')]))

    const { wrapper } = await mountFeed()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()
    await new Promise((r) => setTimeout(r, 20))
    await flushPromises()

    const card = wrapper.find('[data-testid="post-card-1"]')
    expect(card.find('[data-testid="post-view-count"]').exists()).toBe(true)
    expect(card.find('[data-testid="post-like-count"]').exists()).toBe(true)
    expect(card.find('[data-testid="post-dislike-count"]').exists()).toBe(true)
    expect(card.find('[data-testid="post-content"]').text()).toBe('hello')
  })
})
