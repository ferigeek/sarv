import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import type { LoginPayload, RegisterPayload } from '@/api/auth'
import type { Page, UserRegisterResponse, UserResponse, UserSummaryResponse } from '@/types/api'

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

import { getMe as mockGetMe } from '@/api/users'
import App from '../App.vue'
import { createAppRouter } from '../router'

const mockedGetMe = vi.mocked(mockGetMe)

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

describe('AppShell layout (Phase 3)', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    // Default: authenticated to exercise the shell
    setAuthenticated()
  })

  it('renders the three-column shell when authenticated', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter(createMemoryHistory())
    const wrapper = mount(App, { global: { plugins: [pinia, router] } })

    await router.isReady()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="app-shell"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="left-sidebar"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="app-center"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="right-sidebar"]').exists()).toBe(true)
  })

  it('center is largest: shell uses grid with three columns and center renders the feed', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter(createMemoryHistory())
    const wrapper = mount(App, { global: { plugins: [pinia, router] } })

    await router.isReady()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    // Center hosts the routed FeedView
    expect(wrapper.find('[data-testid="feed-view"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="app-center"]').exists()).toBe(true)
  })

  it('right sidebar shows animated Sarv logo, hot topics and platform news', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter(createMemoryHistory())
    const wrapper = mount(App, { global: { plugins: [pinia, router] } })

    await router.isReady()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="sarv-logo"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Sarv"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="hot-topics"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid^="hot-topic-"]').length).toBeGreaterThan(0)
    expect(wrapper.find('[data-testid="platform-news"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid^="platform-news-"]').length).toBeGreaterThan(0)
  })

  it('left sidebar exposes placeholders for Phase 4 wiring', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createAppRouter(createMemoryHistory())
    const wrapper = mount(App, { global: { plugins: [pinia, router] } })

    await router.isReady()
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="left-search"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="left-user-summary"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="left-create-post"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="left-navigation"]').exists()).toBe(true)
  })
})
