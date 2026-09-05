import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Page, PostResponse, ReactionResponse, UserResponse } from '@/types/api'

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<() => Promise<unknown>>(),
  getUserPosts: vi.fn<() => Promise<unknown>>(),
  getReactedPosts: vi.fn<() => Promise<unknown>>(),
  getUserStats: vi.fn<() => Promise<unknown>>(),
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

vi.mock('@/api/posts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/posts')>()
  return {
    ...actual,
    getPost: vi.fn<(id: number) => Promise<PostResponse>>(),
    getComments: vi.fn<(id: number, sortBy?: unknown, pageable?: unknown) => Promise<Page<PostResponse>>>(),
  }
})

import { getComments as mockGetComments, getPost as mockGetPost } from '@/api/posts'
import { getReaction as mockGetReaction } from '@/api/reactions'
import { getUser as mockGetUser } from '@/api/users'
import { registerPixelicons } from '@/assets/icons/pixelarticons'
import PostDetailView from '@/views/PostDetailView.vue'

registerPixelicons()

const mockedGetPost = vi.mocked(mockGetPost)
const mockedGetComments = vi.mocked(mockGetComments)
const mockedGetUser = vi.mocked(mockGetUser)
const mockedGetReaction = vi.mocked(mockGetReaction)

function makePost(id: number, overrides: Partial<PostResponse> = {}): PostResponse {
  return {
    id,
    userId: 10,
    postCategory: 'NORMAL',
    content: `post ${id}`,
    createdAt: '2026-09-02T10:00:00+00:00',
    updatedAt: null,
    mediaId: null,
    repostOfId: null,
    parentId: null,
    viewCount: 3,
    likeCount: 1,
    dislikeCount: 0,
    commentCount: 1,
    ...overrides,
  }
}

function commentPage(content: PostResponse[]): Page<PostResponse> {
  return {
    content,
    page: { size: 10, number: 0, totalElements: content.length, totalPages: 1 },
  }
}

async function mountDetail(postId = 5) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/post/:id', name: 'post-detail', component: PostDetailView },
      { path: '/profile/:id?', name: 'profile', component: { template: '<div/>' } },
      { path: '/', name: 'feed', component: { template: '<div/>' } },
    ],
  })
  await router.push(`/post/${postId}`)
  await router.isReady()
  const wrapper = mount(PostDetailView, { global: { plugins: [router] } })
  await flushPromises()
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
  return { wrapper, router }
}

describe('PostDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
    mockedGetReaction.mockResolvedValue({ likeCount: 1, dislikeCount: 0, userReaction: 0 })
    mockedGetPost.mockResolvedValue(makePost(5))
    mockedGetComments.mockResolvedValue(commentPage([makePost(6, { postCategory: 'COMMENT', parentId: 5 })]))
  })

  it('renders the post and its comments with a write-comment button', async () => {
    const { wrapper } = await mountDetail()

    expect(wrapper.find('[data-testid="post-detail-view"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-detail-card"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="comment-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="comment-write-btn"]').exists()).toBe(true)
  })

  it('shows an invitation when there are no comments', async () => {
    mockedGetComments.mockResolvedValue(commentPage([]))
    const { wrapper } = await mountDetail()

    expect(wrapper.find('[data-testid="comment-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="comment-empty"]').text()).toContain('write the first one')
  })

  it('switches comment sorting between newest and most liked', async () => {
    const { wrapper } = await mountDetail()

    await wrapper.find('[data-testid="comment-sort-most-liked"]').trigger('click')
    await flushPromises()

    expect(mockedGetComments).toHaveBeenLastCalledWith(5, 'MOST_LIKED', expect.anything())

    await wrapper.find('[data-testid="comment-sort-newest"]').trigger('click')
    await flushPromises()

    expect(mockedGetComments).toHaveBeenLastCalledWith(5, 'NEWEST', expect.anything())
  })

  it('opens the comment composer when writing a comment', async () => {
    const { wrapper } = await mountDetail()

    expect(wrapper.find('[data-testid="post-create-modal"]').exists()).toBe(false)
    await wrapper.find('[data-testid="comment-write-btn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="post-create-modal"]').exists()).toBe(true)
  })

  it('shows an error with retry when the post fails to load', async () => {
    mockedGetPost.mockRejectedValue({ detail: 'Post not found' })
    const { wrapper } = await mountDetail(999)

    expect(wrapper.find('[data-testid="post-detail-error"]').exists()).toBe(true)
  })
})
