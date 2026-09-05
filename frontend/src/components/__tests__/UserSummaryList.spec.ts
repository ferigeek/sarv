import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { Page, UserSummaryResponse } from '@/types/api'

let currentUser: { id: number } | null = null

const followSpy = vi.fn<(id: number) => Promise<void>>()
const unfollowSpy = vi.fn<(id: number) => Promise<void>>()
const getFollowingSpy = vi.fn<(id: number, pageable?: unknown) => Promise<Page<UserSummaryResponse>>>()

vi.mock('@/api/follows', () => ({
  follow: (...args: unknown[]) => followSpy(...(args as [number])),
  unfollow: (...args: unknown[]) => unfollowSpy(...(args as [number])),
  getFollowing: (...args: unknown[]) => getFollowingSpy(...(args as [number, unknown])),
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

import UserSummaryList from '../UserSummaryList.vue'
import { registerPixelicons } from '@/assets/icons/pixelarticons'

registerPixelicons()

function makeSummary(id: number): UserSummaryResponse {
  return { id, username: `user${id}`, displayName: `User ${id}`, profilePictureId: null }
}

async function mountList(users: UserSummaryResponse[]) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/profile/:id?', name: 'profile', component: { template: '<div/>' } }],
  })
  const wrapper = mount(UserSummaryList, {
    props: { users },
    global: { plugins: [router] },
  })
  await flushPromises()
  return { wrapper, router }
}

function emptyFollowing() {
  return { content: [], page: { size: 100, number: 0, totalElements: 0, totalPages: 1 } }
}

describe('UserSummaryList', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentUser = { id: 1 }
    getFollowingSpy.mockResolvedValue(emptyFollowing())
  })

  it('renders avatar, display name and username for each user', async () => {
    const { wrapper } = await mountList([makeSummary(2), makeSummary(3)])
    expect(wrapper.findAll('[data-testid^="user-row-"]').length).toBe(2)
    expect(wrapper.find('[data-testid="user-row-2"]').text()).toContain('User 2')
    expect(wrapper.find('[data-testid="user-row-2"]').text()).toContain('@user2')
  })

  it('shows follow for not-followed users and unfollow for followed ones', async () => {
    getFollowingSpy.mockResolvedValue(emptyFollowing())
    const first = await mountList([makeSummary(2)])
    expect(first.wrapper.find('[data-testid="user-follow-2"]').text()).toBe('follow')
    first.wrapper.unmount()

    getFollowingSpy.mockResolvedValue({
      content: [makeSummary(2)],
      page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
    })
    const second = await mountList([makeSummary(2)])
    expect(second.wrapper.find('[data-testid="user-follow-2"]').text()).toBe('unfollow')
  })

  it('follow calls the follow API with the user id', async () => {
    followSpy.mockResolvedValue(undefined)
    const { wrapper } = await mountList([makeSummary(2)])
    await wrapper.find('[data-testid="user-follow-2"]').trigger('click')
    await flushPromises()
    expect(followSpy).toHaveBeenCalledWith(2)
  })

  it('unfollow calls the unfollow API when already following', async () => {
    getFollowingSpy.mockResolvedValue({
      content: [makeSummary(2)],
      page: { size: 100, number: 0, totalElements: 1, totalPages: 1 },
    })
    unfollowSpy.mockResolvedValue(undefined)
    const { wrapper } = await mountList([makeSummary(2)])
    await wrapper.find('[data-testid="user-follow-2"]').trigger('click')
    await flushPromises()
    expect(unfollowSpy).toHaveBeenCalledWith(2)
  })

  it('omits the follow button for the current user', async () => {
    const { wrapper } = await mountList([makeSummary(1)])
    expect(wrapper.find('[data-testid="user-follow-1"]').exists()).toBe(false)
  })

  it('navigates to the profile when the user identity is clicked', async () => {
    const { wrapper, router } = await mountList([makeSummary(2)])
    const push = vi.spyOn(router, 'push')
    await wrapper.find('[data-testid="user-profile-2"]').trigger('click')
    expect(push).toHaveBeenCalledWith({ name: 'profile', params: { id: '2' } })
  })
})