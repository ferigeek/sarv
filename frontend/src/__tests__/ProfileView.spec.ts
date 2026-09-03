import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

import type { MediaResponse, Page, UserResponse, UserSummaryResponse } from '@/types/api'

let authUser: UserResponse | null = null

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<(query: string, pageable?: unknown) => Promise<Page<UserResponse>>>(),
}))

vi.mock('@/api/follows', () => ({
  follow: vi.fn<(id: number) => Promise<void>>(),
  unfollow: vi.fn<(id: number) => Promise<void>>(),
  getFollowing: vi.fn<(id: number, pageable?: unknown) => Promise<Page<UserSummaryResponse>>>(),
}))

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn<(file: File) => Promise<MediaResponse>>(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<import('@/types/api').MediaMetadataResponse>>(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    user: authUser,
    token: 'jwt',
    fetchMe: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
  }),
}))

import { follow as mockFollow, getFollowing as mockGetFollowing, unfollow as mockUnfollow } from '@/api/follows'
import { getUser as mockGetUser, updateMe as mockUpdateMe } from '@/api/users'
import { getMediaBlob as mockGetMediaBlob } from '@/api/media'
import ProfileView from '@/views/ProfileView.vue'

const mockedGetUser = vi.mocked(mockGetUser)
const mockedUpdateMe = vi.mocked(mockUpdateMe)
const mockedGetFollowing = vi.mocked(mockGetFollowing)
const mockedFollow = vi.mocked(mockFollow)
const mockedUnfollow = vi.mocked(mockUnfollow)
const mockedGetMediaBlob = vi.mocked(mockGetMediaBlob)

function makeUser(overrides: Partial<UserResponse> = {}): UserResponse {
  return {
    id: 1,
    username: 'alice',
    displayName: 'Alice',
    bio: null,
    gender: 'FEMALE',
    location: null,
    profilePictureId: null,
    status: 'ACTIVE',
    ...overrides,
  }
}

function makeSummaryList(ids: number[]): Page<UserSummaryResponse> {
  return {
    content: ids.map((id) => ({ id, username: `user${id}`, displayName: `User ${id}`, profilePictureId: null })),
    page: { size: 50, number: 0, totalElements: ids.length, totalPages: 1 },
  }
}

async function mountProfile(routeId?: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/profile/:id?', name: 'profile', component: ProfileView },
    ],
  })
  await router.push(routeId ? `/profile/${routeId}` : '/profile')
  await router.isReady()
  const wrapper = mount(ProfileView, {
    global: { plugins: [router] },
  })
  await flushPromises()
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
  return { wrapper, router }
}

describe('ProfileView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    authUser = null
    mockedGetMediaBlob.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
  })

  it('renders profile info for a viewed user', async () => {
    authUser = makeUser({ id: 1 })
    mockedGetUser.mockResolvedValue(makeUser({ id: 2, username: 'bob', displayName: 'Bob', bio: 'hi', location: 'Tehran' }))
    mockedGetFollowing.mockResolvedValue(makeSummaryList([]))

    const { wrapper } = await mountProfile('2')

    expect(wrapper.find('[data-testid="profile-name"]').text()).toBe('Bob')
    expect(wrapper.find('[data-testid="profile-username"]').text()).toBe('@bob')
    expect(wrapper.find('[data-testid="profile-bio"]').text()).toBe('hi')
    expect(wrapper.find('[data-testid="profile-location"]').text()).toContain('Tehran')
    // Other user → no edit form, follow button instead
    expect(wrapper.find('[data-testid="profile-edit-toggle"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="profile-follow-btn"]').exists()).toBe(true)
    // Posts section rendered but inert
    expect(wrapper.find('[data-testid="profile-posts-inert"]').exists()).toBe(true)
  })

	it('shows follow when not following and unfollow when already following', async () => {
    authUser = makeUser({ id: 1 })
    mockedGetUser.mockResolvedValue(makeUser({ id: 2, username: 'bob' }))
    mockedGetFollowing.mockResolvedValue(makeSummaryList([]))

    const { wrapper } = await mountProfile('2')
    expect(wrapper.find('[data-testid="profile-follow-btn"]').text()).toBe('follow')
    wrapper.unmount()

    // A different user that we already follow shows 'unfollow'
    mockedGetUser.mockResolvedValue(makeUser({ id: 3, username: 'carol' }))
    mockedGetFollowing.mockResolvedValue(makeSummaryList([3]))
    const { wrapper: wrapper2 } = await mountProfile('3')
    expect(wrapper2.find('[data-testid="profile-follow-btn"]').text()).toBe('unfollow')
  })

	it('follow toggles and calls the API', async () => {
    authUser = makeUser({ id: 1 })
    mockedGetUser.mockResolvedValue(makeUser({ id: 2, username: 'bob' }))
    mockedGetFollowing.mockResolvedValue(makeSummaryList([]))
    mockedFollow.mockResolvedValue(undefined)

    const { wrapper } = await mountProfile('2')
    await wrapper.find('[data-testid="profile-follow-btn"]').trigger('click')
    await flushPromises()

    expect(mockedFollow).toHaveBeenCalledWith(2)
  })

	it('unfollow removes the follow', async () => {
    authUser = makeUser({ id: 1 })
    mockedGetUser.mockResolvedValue(makeUser({ id: 2, username: 'bob' }))
    mockedGetFollowing.mockResolvedValue(makeSummaryList([2]))
    mockedUnfollow.mockResolvedValue(undefined)

    const { wrapper } = await mountProfile('2')
    expect(wrapper.find('[data-testid="profile-follow-btn"]').text()).toBe('unfollow')

    await wrapper.find('[data-testid="profile-follow-btn"]').trigger('click')
    await flushPromises()

    expect(mockedUnfollow).toHaveBeenCalledWith(2)
  })

	it('self profile shows edit form and saving updates the profile', async () => {
    authUser = makeUser({ id: 1 })
    mockedGetUser.mockResolvedValue(makeUser({ id: 1, bio: 'original' }))
    mockedUpdateMe.mockResolvedValue(makeUser({ id: 1, bio: 'updated bio', displayName: 'Alice' }))

    const { wrapper } = await mountProfile('1')

    // Self → no follow button, edit toggle present
    expect(wrapper.find('[data-testid="profile-follow-btn"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="profile-edit-toggle"]').exists()).toBe(true)

    await wrapper.find('[data-testid="profile-edit-toggle"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="profile-edit-bio"]').setValue('updated bio')
    await wrapper.find('[data-testid="profile-edit-save"]').trigger('click')
    await flushPromises()

    expect(mockedUpdateMe).toHaveBeenCalledWith(expect.objectContaining({ bio: 'updated bio' }))
    expect(wrapper.find('[data-testid="profile-edit-form"]').exists()).toBe(false)
  })
})