import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import type { Page, UserResponse, UserSummaryResponse } from '@/types/api'

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<(query: string, pageable?: unknown) => Promise<Page<UserSummaryResponse>>>(),
}))

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn<(file: File) => Promise<import('@/types/api').MediaResponse>>(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<import('@/types/api').MediaMetadataResponse>>(),
}))

vi.mock('@/api/auth', () => ({
  login: vi.fn<(payload: import('@/api/auth').LoginPayload) => Promise<string>>(),
  register: vi.fn<(payload: import('@/api/auth').RegisterPayload) => Promise<import('@/types/api').UserRegisterResponse>>(),
}))

import { getMediaBlob as mockGetMediaBlob } from '@/api/media'
import { getMe as mockGetMe, searchUsers as mockSearchUsers } from '@/api/users'
import { registerPixelicons } from '@/assets/icons/pixelarticons'
import LeftSidebar from '@/components/LeftSidebar.vue'
import { createAppRouter } from '@/router'

registerPixelicons()

const mockedGetMe = vi.mocked(mockGetMe)
const mockedSearchUsers = vi.mocked(mockSearchUsers)
const mockedGetMediaBlob = vi.mocked(mockGetMediaBlob)

function setAuthenticated(withAvatar = false) {
  localStorage.setItem('sarv.jwt', 'test-jwt')
  mockedGetMe.mockResolvedValue({
    id: 1,
    username: 'alice',
    displayName: 'Alice',
    bio: null,
    gender: 'FEMALE',
    location: null,
    profilePictureId: withAvatar ? 42 : null,
    status: 'ACTIVE',
  })
}

function mountLeftSidebar() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter(createMemoryHistory())
  const wrapper = mount(LeftSidebar, {
    global: {
      plugins: [pinia, router],
    },
  })
  return { wrapper, router, pinia }
}

describe('LeftSidebar (Phase 4)', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    mockedGetMediaBlob.mockResolvedValue(new Blob(['fake'], { type: 'image/png' }))
  })

  it('exposes search with three tabs and a panel on the same page', async () => {
    setAuthenticated()
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="search-section"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-input"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-general"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-username"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-post"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-username"]').classes()).toContain('search-tab--active')
  })

  it('searches users by username and shows results in the same-page panel', async () => {
    setAuthenticated()
    mockedSearchUsers.mockResolvedValue({
      content: [
        { id: 2, username: 'bob', displayName: 'Bob', profilePictureId: null },
        { id: 3, username: 'carol', displayName: 'Carol', profilePictureId: null },
      ],
      page: { size: 8, number: 0, totalElements: 2, totalPages: 1 },
    })

    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    const input = wrapper.find('[data-testid="search-input"]')
    await input.setValue('bob')
    await input.trigger('focus')
    await flushPromises()
    // wait for debounce 300ms
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()

    expect(mockedSearchUsers).toHaveBeenCalledWith('bob', expect.objectContaining({ size: 8 }))
    expect(wrapper.find('[data-testid="search-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-result-2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-result-3"]').exists()).toBe(true)
  })

  it('shows coming soon for general and post tabs (inert)', async () => {
    setAuthenticated()
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    await wrapper.find('[data-testid="search-tab-general"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="search-input"]').setValue('hello')
    await wrapper.find('[data-testid="search-input"]').trigger('focus')
    await flushPromises()
    expect(wrapper.find('[data-testid="search-coming-soon"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-coming-soon"]').text()).toContain('general search')

    await wrapper.find('[data-testid="search-tab-post"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="search-coming-soon"]').text()).toContain('post search')

    // No API call for inert tabs
    expect(mockedSearchUsers).not.toHaveBeenCalled()
  })

  it('navigates to profile when a search result is selected, without leaving the panel via navigation', async () => {
    setAuthenticated()
    mockedSearchUsers.mockResolvedValue({
      content: [{ id: 5, username: 'dave', displayName: 'Dave', profilePictureId: null }],
      page: { size: 8, number: 0, totalElements: 1, totalPages: 1 },
    })

    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    const input = wrapper.find('[data-testid="search-input"]')
    await input.setValue('dave')
    await input.trigger('focus')
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()

    await wrapper.find('[data-testid="search-result-5"]').trigger('mousedown')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'profile' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }

    expect(router.currentRoute.value.name).toBe('profile')
    expect(router.currentRoute.value.params.id).toBe('5')
  })

  it('shows the authenticated user summary with displayName and username', async () => {
    setAuthenticated()
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="user-summary"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="user-summary-name"]').text()).toBe('Alice')
    expect(wrapper.find('[data-testid="user-summary-username"]').text()).toBe('@alice')
  })

  it('loads and shows the profile picture when one exists', async () => {
    setAuthenticated(true)
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()
    // wait for avatar fetch
    await new Promise((r) => setTimeout(r, 20))
    await flushPromises()

    expect(mockedGetMediaBlob).toHaveBeenCalledWith(42)
    expect(wrapper.find('[data-testid="user-summary-img"]').exists()).toBe(true)
  })

  it('user summary click goes to the profile', async () => {
    setAuthenticated()
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    await wrapper.find('[data-testid="user-summary"]').trigger('click')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('profile')
  })

  it('create-post opens a window on the current page', async () => {
    setAuthenticated()
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="post-create-modal"]').exists()).toBe(false)

    await wrapper.find('[data-testid="left-create-post-btn"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="post-create-modal"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-create-overlay"]').exists()).toBe(true)
    // Still on same route
    expect(router.currentRoute.value.name).toBe('feed')

    await wrapper.find('[data-testid="post-create-close"]').trigger('click')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 250))
    await flushPromises()

    expect(wrapper.find('[data-testid="post-create-modal"]').exists()).toBe(false)
  })

  it('navigation: profile, following and followers navigate; liked is inert', async () => {
    setAuthenticated()
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    await wrapper.find('[data-testid="left-nav-profile"]').trigger('click')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'profile' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }
    expect(router.currentRoute.value.name).toBe('profile')

    await wrapper.find('[data-testid="left-nav-following"]').trigger('click')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'following' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }
    expect(router.currentRoute.value.name).toBe('following')

    await wrapper.find('[data-testid="left-nav-followers"]').trigger('click')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'followers' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }
    expect(router.currentRoute.value.name).toBe('followers')

    const before = router.currentRoute.value.fullPath
    await wrapper.find('[data-testid="left-nav-liked"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe(before)
  })
})
