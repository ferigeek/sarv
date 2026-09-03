import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Page, UserSummaryResponse } from '@/types/api'

let currentUser: { id: number } | null = null
const getFollowingSpy = vi.fn<(id: number, pageable?: unknown) => Promise<Page<UserSummaryResponse>>>()

vi.mock('@/api/follows', () => ({
  getFollowers: vi.fn<(id: number, pageable?: unknown) => Promise<Page<UserSummaryResponse>>>(),
  getFollowing: (...args: unknown[]) => getFollowingSpy(...(args as [number, unknown])),
  follow: vi.fn<(id: number) => Promise<void>>(),
  unfollow: vi.fn<(id: number) => Promise<void>>(),
}))

vi.mock('@/api/media', () => ({
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<import('@/types/api').MediaMetadataResponse>>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: currentUser,
    token: 'jwt',
  }),
}))

import { getFollowers as mockGetFollowers } from '@/api/follows'
import FollowersView from '@/views/FollowersView.vue'
import FollowingView from '@/views/FollowingView.vue'

const mockedGetFollowers = vi.mocked(mockGetFollowers)

function makeSummary(id: number): UserSummaryResponse {
  return { id, username: `user${id}`, displayName: `User ${id}`, profilePictureId: null }
}

function pageOf(ids: number[]): Page<UserSummaryResponse> {
  return {
    content: ids.map(makeSummary),
    page: { size: 20, number: 0, totalElements: ids.length, totalPages: ids.length > 0 ? 1 : 0 },
  }
}

async function mountView(component: typeof FollowersView) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/profile/:id?', name: 'profile', component: { template: '<div/>' } }],
  })
  const wrapper = mount(component, {
    global: { plugins: [router] },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('FollowersView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentUser = { id: 1 }
    mockedGetFollowers.mockResolvedValue(pageOf([]))
  })

  it('shows loading while fetching and empty when there are none', async () => {
    let resolve: (v: Page<UserSummaryResponse>) => void
    mockedGetFollowers.mockReturnValue(new Promise((r) => { resolve = r }))
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/profile/:id?', name: 'profile', component: { template: '<div/>' } }],
    })
    const wrapper = mount(FollowersView, {
      global: { plugins: [router] },
    })
    // still pending → loading spinner visible
    expect(wrapper.find('[data-testid="followers-loading"]').exists()).toBe(true)

    resolve!(pageOf([]))
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()
    expect(wrapper.find('[data-testid="followers-empty"]').exists()).toBe(true)
  })

  it('shows an error when the fetch fails', async () => {
    mockedGetFollowers.mockRejectedValue({ status: 500, detail: 'boom' } as unknown as Page<UserSummaryResponse>)
    const { wrapper } = await mountView(FollowersView)
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()
    expect(wrapper.find('[data-testid="followers-error"]').exists()).toBe(true)
  })

  it('renders the list of followers', async () => {
    mockedGetFollowers.mockResolvedValue(pageOf([2, 3]))
    const { wrapper } = await mountView(FollowersView)
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="user-row-2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="user-row-3"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid^="user-row-"]').length).toBe(2)
  })
})

describe('FollowingView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentUser = { id: 1 }
    getFollowingSpy.mockResolvedValue(pageOf([]))
  })

  it('renders the list of following', async () => {
    getFollowingSpy.mockResolvedValue(pageOf([4, 5]))
    const { wrapper } = await mountView(FollowingView)
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="user-row-4"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="user-row-5"]').exists()).toBe(true)
  })

  it('shows a message when not following anyone', async () => {
    getFollowingSpy.mockResolvedValue(pageOf([]))
    const { wrapper } = await mountView(FollowingView)
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="following-empty"]').exists()).toBe(true)
  })
})