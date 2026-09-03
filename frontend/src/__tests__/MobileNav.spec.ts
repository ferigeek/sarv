import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import type { LoginPayload, RegisterPayload } from '@/api/auth'
import type { Page, PostResponse, UserRegisterResponse, UserResponse, UserSummaryResponse } from '@/types/api'

vi.mock('@/api/auth', () => ({
  login: vi.fn<(payload: LoginPayload) => Promise<string>>(),
  register: vi.fn<(payload: RegisterPayload) => Promise<UserRegisterResponse>>(),
}))

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<() => Promise<Page<UserSummaryResponse>>>(),
}))

vi.mock('@/api/feed', () => ({
  getRecommendedFeed: vi.fn<() => Promise<Page<PostResponse>>>(),
  getChronologicalFeed: vi.fn<() => Promise<Page<PostResponse>>>(),
}))

vi.mock('@/api/reactions', () => ({
  addReaction: vi.fn(),
  getReaction: vi.fn(),
  removeReaction: vi.fn(),
}))

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn(),
}))

import { getChronologicalFeed as mockChrono, getRecommendedFeed as mockRecommended } from '@/api/feed'
import { getReaction as mockGetReaction } from '@/api/reactions'
import { getMe as mockGetMe } from '@/api/users'
import { registerPixelicons } from '@/assets/icons/pixelarticons'
import { createAppRouter } from '@/router'
import AppShell from '@/views/AppShell.vue'

registerPixelicons()

const mockedGetMe = vi.mocked(mockGetMe)
const mockedRecommended = vi.mocked(mockRecommended)
const mockedChrono = vi.mocked(mockChrono)
const mockedGetReaction = vi.mocked(mockGetReaction)

function setAuthenticated() {
  localStorage.setItem('sarv.jwt', 'test-jwt')
  mockedGetMe.mockResolvedValue({
    id: 1,
    username: 'alice',
    displayName: 'Alice',
    bio: null,
    gender: 'FEMALE',
    location: null,
    profilePictureId: null,
    status: 'ACTIVE',
  })
}

function emptyFeed(): Page<PostResponse> {
  return { content: [], page: { size: 20, number: 0, totalElements: 0, totalPages: 0 } }
}

async function mountShell() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter(createMemoryHistory())
  const wrapper = mount(AppShell, { global: { plugins: [pinia, router] } })
  await router.isReady()
  await flushPromises()
  await new Promise((r) => setTimeout(r, 0))
  await flushPromises()
  return wrapper
}

describe('Mobile navigation (AppShell)', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    document.body.classList.remove('no-scroll')
    setAuthenticated()
    mockedRecommended.mockResolvedValue(emptyFeed())
    mockedChrono.mockResolvedValue(emptyFeed())
    mockedGetReaction.mockRejectedValue(new Error('no reaction'))
  })

  it('renders mobile top bar and bottom nav alongside the desktop shell', async () => {
    const wrapper = mountShell()
    const w = await wrapper
    expect(w.find('[data-testid="mobile-topbar"]').exists()).toBe(true)
    expect(w.find('[data-testid="mobile-bottom-nav"]').exists()).toBe(true)
    expect(w.find('[data-testid="left-sidebar"]').exists()).toBe(true)
    expect(w.find('[data-testid="right-sidebar"]').exists()).toBe(true)
    w.unmount()
  })

  it('opens the left drawer from the bottom nav and closes it via scrim', async () => {
    const w = await mountShell()
    expect(w.find('[data-testid="drawer-scrim"]').exists()).toBe(false)

    await w.find('[data-testid="mobile-nav-menu"]').trigger('click')
    expect(w.find('[data-testid="left-sidebar"].drawer-open').exists()).toBe(true)
    expect(w.find('[data-testid="drawer-scrim"]').exists()).toBe(true)
    expect(document.body.classList.contains('no-scroll')).toBe(true)

    await w.find('[data-testid="drawer-scrim"]').trigger('click')
    expect(w.find('[data-testid="left-sidebar"].drawer-open').exists()).toBe(false)
    w.unmount()
  })

  it('opens the right drawer from the topics tab', async () => {
    const w = await mountShell()
    await w.find('[data-testid="mobile-nav-topics"]').trigger('click')
    expect(w.find('[data-testid="right-sidebar"].drawer-open').exists()).toBe(true)
    expect(w.find('[data-testid="hot-topics"]').exists()).toBe(true)
    expect(w.find('[data-testid="platform-news"]').exists()).toBe(true)
    w.unmount()
  })

  it('opens the create-post sheet from the bottom nav and closes on Escape', async () => {
    const w = await mountShell()
    await w.find('[data-testid="mobile-nav-create"]').trigger('click')
    expect(w.find('[data-testid="post-create-modal"]').exists()).toBe(true)

    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(w.find('[data-testid="post-create-modal"]').exists()).toBe(false)
    w.unmount()
  })
})
