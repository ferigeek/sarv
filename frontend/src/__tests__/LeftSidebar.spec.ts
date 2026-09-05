import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DOMWrapper, flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import type { Page, PostResponse, UserResponse, UserSummaryResponse } from '@/types/api'

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<(query: string, pageable?: unknown) => Promise<Page<UserSummaryResponse>>>(),
}))

vi.mock('@/api/posts', () => ({
  getPost: vi.fn<() => Promise<unknown>>(),
  createPost: vi.fn<() => Promise<unknown>>(),
  updatePost: vi.fn<() => Promise<unknown>>(),
  deletePost: vi.fn<() => Promise<void>>(),
  searchPosts: vi.fn<(query: string, pageable?: unknown) => Promise<Page<import('@/types/api').PostResponse>>>(),
  getComments: vi.fn<() => Promise<unknown>>(),
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
import { searchPosts as mockSearchPosts } from '@/api/posts'
import { getMe as mockGetMe, searchUsers as mockSearchUsers } from '@/api/users'
import { registerPixelicons } from '@/assets/icons/pixelarticons'
import LeftSidebar from '@/components/LeftSidebar.vue'
import { createAppRouter } from '@/router'

registerPixelicons()

const mockedGetMe = vi.mocked(mockGetMe)
const mockedSearchUsers = vi.mocked(mockSearchUsers)
const mockedSearchPosts = vi.mocked(mockSearchPosts)
const mockedGetMediaBlob = vi.mocked(mockGetMediaBlob)

function makePostResult(id: number, content = `post ${id}`): PostResponse {
  return {
    id,
    userId: 9,
    postCategory: 'NORMAL',
    content,
    createdAt: '2026-09-02T10:00:00+00:00',
    updatedAt: null,
    mediaId: null,
    repostOfId: null,
    parentId: null,
    viewCount: 2,
    likeCount: 1,
    dislikeCount: 0,
    commentCount: 0,
  }
}

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
    attachTo: document.body,
    global: {
      plugins: [pinia, router],
    },
  })
  return { wrapper, router, pinia }
}

// SearchModal is teleported to document.body so the drawer scrollbar/border
// never paints above it — query the modal there instead of inside the wrapper.
function modalEl(testid: string): Element | null {
  return document.body.querySelector(`[data-testid="${testid}"]`)
}

function modalExists(testid: string): boolean {
  return modalEl(testid) !== null
}

function modalWrapper(testid: string): DOMWrapper<Element> | null {
  const el = modalEl(testid)
  return el ? new DOMWrapper(el) : null
}

async function setModalInput(value: string) {
  const w = modalWrapper('search-modal-input')
  if (!w) throw new Error('search-modal-input not found in document.body')
  await w.setValue(value)
}

async function clickModal(testid: string) {
  const w = modalWrapper(testid)
  if (!w) throw new Error(`${testid} not found in document.body`)
  await w.trigger('click')
}

async function mousedownModal(testid: string) {
  const w = modalWrapper(testid)
  if (!w) throw new Error(`${testid} not found in document.body`)
  await w.trigger('mousedown')
}

describe('LeftSidebar (Phase 4)', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    localStorage.clear()
    vi.clearAllMocks()
    mockedGetMediaBlob.mockResolvedValue(new Blob(['fake'], { type: 'image/png' }))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('exposes search with three tabs and opens a centered modal on focus', async () => {
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
    expect(modalExists('search-modal')).toBe(false)

    await wrapper.find('[data-testid="search-input"]').trigger('focus')
    await flushPromises()

    expect(modalExists('search-modal')).toBe(true)
    expect(modalExists('search-modal-input')).toBe(true)
  })

  it('searches users by username and shows account results in the modal window', async () => {
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

    await wrapper.find('[data-testid="search-input"]').trigger('focus')
    await flushPromises()
    await setModalInput('bob')
    await flushPromises()
    // wait for debounce 300ms
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()

    expect(mockedSearchUsers).toHaveBeenCalledWith('bob', expect.objectContaining({ size: 8 }))
    expect(modalExists('search-modal')).toBe(true)
    expect(modalExists('search-result-2')).toBe(true)
    expect(modalExists('search-result-3')).toBe(true)
  })

  it('searches post content on the post tab and general combines both', async () => {
    setAuthenticated()
    mockedSearchPosts.mockResolvedValue({
      content: [makePostResult(21, 'hello world post')],
      page: { size: 8, number: 0, totalElements: 1, totalPages: 1 },
    })
    mockedSearchUsers.mockResolvedValue({
      content: [{ id: 2, username: 'bob', displayName: 'Bob', profilePictureId: null }],
      page: { size: 5, number: 0, totalElements: 1, totalPages: 1 },
    })

    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    await wrapper.find('[data-testid="search-tab-post"]').trigger('click')
    await flushPromises()
    expect(modalExists('search-modal')).toBe(true)

    await setModalInput('hello')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()

    expect(mockedSearchPosts).toHaveBeenCalledWith('hello', expect.anything())
    expect(modalExists('search-post-21')).toBe(true)

    await clickModal('search-modal-tab-general')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()

    expect(mockedSearchUsers).toHaveBeenCalledWith('hello', expect.anything())
    expect(modalExists('search-result-2')).toBe(true)
    expect(modalExists('search-post-21')).toBe(true)
  })

  it('navigates to profile when an account result is selected', async () => {
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

    await wrapper.find('[data-testid="search-input"]').trigger('focus')
    await flushPromises()
    await setModalInput('dave')
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()

    await mousedownModal('search-result-5')
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
    expect(modalExists('search-modal')).toBe(false)
  })

  it('navigates to the post detail when a post result is selected', async () => {
    setAuthenticated()
    mockedSearchPosts.mockResolvedValue({
      content: [makePostResult(22, 'find me')],
      page: { size: 8, number: 0, totalElements: 1, totalPages: 1 },
    })

    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    await wrapper.find('[data-testid="search-tab-post"]').trigger('click')
    await flushPromises()
    await setModalInput('find me')
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()

    await mousedownModal('search-post-22')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'post-detail' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }

    expect(router.currentRoute.value.name).toBe('post-detail')
    expect(router.currentRoute.value.params.id).toBe('22')
  })

  it('closes the search modal with the close button', async () => {
    setAuthenticated()
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    await wrapper.find('[data-testid="search-input"]').trigger('focus')
    await flushPromises()
    expect(modalExists('search-modal')).toBe(true)

    await clickModal('search-modal-close')
    await flushPromises()
    expect(modalExists('search-modal')).toBe(false)
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

  it('navigation: home, profile, liked, following and followers navigate', async () => {
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

    await wrapper.find('[data-testid="left-nav-liked"]').trigger('click')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'liked' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }
    expect(router.currentRoute.value.name).toBe('liked')

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
  })

  it('home nav item and brand section navigate to the feed', async () => {
    setAuthenticated()
    const { wrapper, router } = mountLeftSidebar()
    await router.push('/liked')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="left-nav-home"]').exists()).toBe(true)

    await wrapper.find('[data-testid="left-nav-home"]').trigger('click')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'feed' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }
    expect(router.currentRoute.value.name).toBe('feed')

    await router.push('/liked')
    await flushPromises()

    await wrapper.find('[data-testid="left-brand-home"]').trigger('click')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'feed' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }
    expect(router.currentRoute.value.name).toBe('feed')
  })
})
