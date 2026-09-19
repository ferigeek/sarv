import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import type { PostResponse, ReactionResponse, UserSummaryResponse } from '@/types/api'

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<unknown>>(),
  getUser: vi.fn<(id: number) => Promise<unknown>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<unknown>>(),
  searchUsers: vi.fn<(query: string, pageable?: unknown) => Promise<unknown>>(),
}))

vi.mock('@/api/posts', () => ({
  getPost: vi.fn<(id: number) => Promise<PostResponse>>(),
  getPostAuthor: vi.fn<(id: number) => Promise<UserSummaryResponse>>(),
  createPost: vi.fn<(payload: unknown) => Promise<PostResponse>>(),
  updatePost: vi.fn<() => Promise<PostResponse>>(),
  deletePost: vi.fn<() => Promise<void>>(),
  searchPosts: vi.fn<() => Promise<unknown>>(),
  getComments: vi.fn<() => Promise<unknown>>(),
  repostPost: vi.fn<(id: number) => Promise<PostResponse>>(),
  quotePost: vi.fn<() => Promise<PostResponse>>(),
  reportPostDwell: vi.fn<(id: number, payload: unknown) => Promise<void>>(),
}))

vi.mock('@/api/reactions', () => ({
  addReaction: vi.fn<(postId: number, type: number) => Promise<ReactionResponse>>(),
  getReaction: vi.fn<(postId: number) => Promise<ReactionResponse>>(),
  removeReaction: vi.fn<(postId: number) => Promise<void>>(),
}))

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn<() => Promise<import('@/types/api').MediaResponse>>(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<import('@/types/api').MediaMetadataResponse>>(),
}))

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn<() => Promise<unknown>>() }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return { ...actual, useRouter: () => ({ push: pushMock }) }
})

import { getPostAuthor as mockGetPostAuthor } from '@/api/posts'
import { reportPostDwell as mockReportPostDwell } from '@/api/posts'
import { getReaction as mockGetReaction } from '@/api/reactions'
import { registerPixelicons } from '@/assets/icons/pixelarticons'
import PostCard from '../PostCard.vue'

registerPixelicons()

const mockedGetPostAuthor = vi.mocked(mockGetPostAuthor)
const mockedGetReaction = vi.mocked(mockGetReaction)
const mockedReportPostDwell = vi.mocked(mockReportPostDwell)

const observerCallbacks: IntersectionObserverCallback[] = []

function setAllVisible(visible: boolean) {
  const entry = {
    isIntersecting: visible,
    target: document.body,
  } as unknown as IntersectionObserverEntry
  for (const cb of observerCallbacks) {
    cb([entry], {} as IntersectionObserver)
  }
}

function makePost(overrides: Partial<PostResponse> = {}): PostResponse {
  return {
    id: 1,
    userId: 10,
    postCategory: 'NORMAL',
    content: 'hello world',
    createdAt: '2026-09-02T10:00:00+00:00',
    updatedAt: null,
    mediaId: null,
    repostOfId: null,
    parentId: null,
    viewCount: 5,
    likeCount: 2,
    dislikeCount: 1,
    commentCount: 0,
    ...overrides,
  }
}

describe('PostCard dwell', () => {
  let nowValue = 0
  let nowSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    vi.clearAllMocks()
    observerCallbacks.length = 0
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(cb: IntersectionObserverCallback) {
          observerCallbacks.push(cb)
        }
        observe() {}
        unobserve() {}
        disconnect() {}
        takeRecords() {
          return []
        }
      },
    )
    mockedGetPostAuthor.mockResolvedValue({
      id: 10,
      username: 'bob',
      displayName: 'Bob',
      profilePictureId: null,
    })
    mockedGetReaction.mockResolvedValue({ likeCount: 2, dislikeCount: 1, userReaction: 0 })
    nowValue = 2_000_000
    nowSpy = vi.spyOn(Date, 'now').mockImplementation(() => nowValue)
  })

  afterEach(() => {
    nowSpy.mockRestore()
    vi.unstubAllGlobals()
  })

  async function mountCard(props: Record<string, unknown> = {}) {
    const wrapper = mount(PostCard, { props: { post: makePost(), ...props } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()
    return wrapper
  }

  it('reports FEED dwell for a visible card on unmount', async () => {
    const wrapper = await mountCard({ dwellSource: 'FEED' })

    setAllVisible(true)
    nowValue += 1500
    wrapper.unmount()

    expect(mockedReportPostDwell).toHaveBeenCalledOnce()
    expect(mockedReportPostDwell).toHaveBeenCalledWith(1, { durationMs: 1500, source: 'FEED' })
  })

  it('reports nothing when the card never becomes visible', async () => {
    const wrapper = await mountCard({ dwellSource: 'FEED' })

    nowValue += 5000
    wrapper.unmount()

    expect(mockedReportPostDwell).not.toHaveBeenCalled()
  })

  it('reports nothing for brief views below the 1s threshold', async () => {
    const wrapper = await mountCard({ dwellSource: 'FEED' })

    setAllVisible(true)
    nowValue += 500
    wrapper.unmount()

    expect(mockedReportPostDwell).not.toHaveBeenCalled()
  })

  it('tracks nothing without dwellSource', async () => {
    const wrapper = await mountCard()

    setAllVisible(true)
    nowValue += 5000
    wrapper.unmount()

    expect(mockedReportPostDwell).not.toHaveBeenCalled()
    expect(observerCallbacks).toHaveLength(0)
  })
})
