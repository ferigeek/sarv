import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Page, PostResponse, ReactionResponse, UserResponse } from '@/types/api'

let authUser: UserResponse | null = null

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<() => Promise<UserResponse>>(),
  searchUsers: vi.fn<() => Promise<unknown>>(),
  getUserPosts: vi.fn<() => Promise<unknown>>(),
  getReactedPosts: vi.fn<(id: number, filter?: unknown, pageable?: unknown) => Promise<Page<PostResponse>>>(),
  getUserStats: vi.fn<() => Promise<unknown>>(),
}))

vi.mock('@/api/reactions', () => ({
  addReaction: vi.fn<() => Promise<unknown>>(),
  getReaction: vi.fn<() => Promise<ReactionResponse>>(),
  removeReaction: vi.fn<() => Promise<void>>(),
}))

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn<() => Promise<unknown>>(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<unknown>>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: authUser,
    token: 'jwt',
    fetchMe: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
  }),
}))

import { getReactedPosts as mockGetReactedPosts, getUser as mockGetUser } from '@/api/users'
import { getReaction as mockGetReaction } from '@/api/reactions'
import { getMediaBlob as mockGetMediaBlob } from '@/api/media'
import { registerPixelicons } from '@/assets/icons/pixelarticons'
import LikedPostsView from '@/views/LikedPostsView.vue'

registerPixelicons()

const mockedGetReactedPosts = vi.mocked(mockGetReactedPosts)
const mockedGetUser = vi.mocked(mockGetUser)
const mockedGetReaction = vi.mocked(mockGetReaction)
const mockedGetMediaBlob = vi.mocked(mockGetMediaBlob)

function makePost(id: number): PostResponse {
  return {
    id,
    userId: 9,
    postCategory: 'NORMAL',
    content: `post ${id}`,
    createdAt: '2026-09-02T10:00:00+00:00',
    updatedAt: null,
    mediaId: null,
    repostOfId: null,
    parentId: null,
    viewCount: 1,
    likeCount: 2,
    dislikeCount: 0,
    commentCount: 0,
  }
}

function pageOf(ids: number[]): Page<PostResponse> {
  return {
    content: ids.map(makePost),
    page: { size: 20, number: 0, totalElements: ids.length, totalPages: 1 },
  }
}

async function mountLiked() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/liked', name: 'liked', component: LikedPostsView },
      { path: '/post/:id', name: 'post-detail', component: { template: '<div/>' } },
      { path: '/profile/:id?', name: 'profile', component: { template: '<div/>' } },
    ],
  })
  await router.push('/liked')
  await router.isReady()
  const wrapper = mount(LikedPostsView, { global: { plugins: [router] } })
  await flushPromises()
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
  return { wrapper, router }
}

describe('LikedPostsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authUser = {
      id: 1,
      username: 'alice',
      displayName: 'Alice',
      bio: null,
      gender: 'FEMALE',
      location: null,
      profilePictureId: null,
      status: 'ACTIVE',
    }
    mockedGetUser.mockResolvedValue({
      id: 9,
      username: 'bob',
      displayName: 'Bob',
      bio: null,
      gender: 'MALE',
      location: null,
      profilePictureId: null,
      status: 'ACTIVE',
    })
    mockedGetReaction.mockResolvedValue({ likeCount: 2, dislikeCount: 0, userReaction: 1 })
    mockedGetMediaBlob.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    mockedGetReactedPosts.mockResolvedValue(pageOf([31, 32]))
  })

  it('loads liked posts by default with the LIKE filter', async () => {
    const { wrapper } = await mountLiked()

    expect(mockedGetReactedPosts).toHaveBeenCalledWith(1, 'LIKE', expect.anything())
    expect(wrapper.find('[data-testid="liked-list"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-card-31"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-card-32"]').exists()).toBe(true)
  })

  it('switches to disliked and all filters', async () => {
    const { wrapper } = await mountLiked()

    await wrapper.find('[data-testid="liked-tab-disliked"]').trigger('click')
    await flushPromises()
    expect(mockedGetReactedPosts).toHaveBeenLastCalledWith(1, 'DISLIKE', expect.anything())

    await wrapper.find('[data-testid="liked-tab-all"]').trigger('click')
    await flushPromises()
    expect(mockedGetReactedPosts).toHaveBeenLastCalledWith(1, 'ALL', expect.anything())
  })

  it('shows an empty state when there are no reacted posts', async () => {
    mockedGetReactedPosts.mockResolvedValue(pageOf([]))
    const { wrapper } = await mountLiked()

    expect(wrapper.find('[data-testid="liked-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="liked-empty"]').text()).toContain('no liked posts yet')
  })
})
