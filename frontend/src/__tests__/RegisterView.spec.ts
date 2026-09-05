import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import type { VueWrapper } from '@vue/test-utils'

import type { LoginPayload, RegisterPayload } from '@/api/auth'
import type { MediaResponse, UserRegisterResponse, UserResponse } from '@/types/api'

vi.mock('@/api/auth', () => ({
  login: vi.fn<(payload: LoginPayload) => Promise<string>>(),
  register: vi.fn<(payload: RegisterPayload) => Promise<UserRegisterResponse>>(),
}))

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<() => Promise<unknown>>(),
}))

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn<(file: File) => Promise<MediaResponse>>(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<unknown>>(),
}))

vi.mock('gsap', () => {
  const mockTimeline = () => ({
    to: vi.fn().mockReturnThis(),
    call: vi.fn().mockReturnThis(),
    set: vi.fn().mockReturnThis(),
  })
  const mockGsap = {
    to: vi.fn(),
    set: vi.fn(),
    timeline: vi.fn(mockTimeline),
  }
  return {
    default: mockGsap,
    ...mockGsap,
  }
})

// Avoid lazy-import teardown hangs when navigating to the feed (AppShell + FeedView
// pull in PostCard/LeftSidebar/AmbientNetwork). In the registration flow the
// AppShell is not rendered, but the router still has to resolve the lazy
// components for the navigation to complete deterministically with flushPromises.
vi.mock('@/views/AppShell.vue', () => ({
  default: { name: 'AppShellStub', template: '<div><router-view /></div>' },
}))
vi.mock('@/views/FeedView.vue', () => ({
  default: { name: 'FeedViewStub', template: '<div data-testid="feed-view">feed</div>' },
}))
vi.mock('@/views/ProfileView.vue', () => ({
  default: { name: 'ProfileViewStub', template: '<div>profile</div>' },
}))
vi.mock('@/components/LeftSidebar.vue', () => ({
  default: { name: 'LeftSidebarStub', template: '<div>left</div>' },
}))
vi.mock('@/components/RightSidebar.vue', () => ({
  default: { name: 'RightSidebarStub', template: '<div>right</div>' },
}))
vi.mock('@/components/PostCard.vue', () => ({
  default: { name: 'PostCardStub', template: '<div>post</div>' },
}))

import { register as mockRegister } from '@/api/auth'
import { uploadMedia as mockUploadMedia } from '@/api/media'
import { getMe as mockGetMe, updateMe as mockUpdateMe } from '@/api/users'
import { createAppRouter } from '../router'
import RegisterView from '../views/RegisterView.vue'

const mockedRegister = vi.mocked(mockRegister)
const mockedGetMe = vi.mocked(mockGetMe)
const mockedUpdateMe = vi.mocked(mockUpdateMe)
const mockedUploadMedia = vi.mocked(mockUploadMedia)

function mountRegister() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter(createMemoryHistory())
  const wrapper = mount(RegisterView, {
    global: {
      plugins: [pinia, router],
    },
  })
  return { wrapper, router, pinia }
}

const userResponse: UserResponse = {
  id: 1,
  username: 'alice',
  displayName: 'Alice',
  bio: null,
  gender: 'FEMALE',
  location: null,
  profilePictureId: null,
  status: 'ACTIVE',
}

async function fillRequiredFields(wrapper: VueWrapper) {
  await wrapper.find('[data-testid="register-username"]').setValue('alice')
  await wrapper.find('[data-testid="register-password"]').setValue('secret12')
  await wrapper.find('[data-testid="register-confirmPassword"]').setValue('secret12')
  await wrapper.find('[data-testid="register-email"]').setValue('a@x.io')
  await wrapper.find('[data-testid="register-displayName"]').setValue('Alice')
}

describe('RegisterView', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('renders step 1 with mandatory fields', async () => {
    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    expect(wrapper.find('[data-testid="register-username"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-password"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-confirmPassword"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-email"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-displayName"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-gender"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-submit"]').exists()).toBe(true)
  })

  it('shows a validation error when mandatory fields are missing', async () => {
    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="register-error"]').text()).toContain('required')
    expect(mockedRegister).not.toHaveBeenCalled()
  })

  it('creates the account and moves to step 2', async () => {
    mockedRegister.mockResolvedValue({ id: 1, username: 'alice', displayName: 'Alice', email: 'a@x.io', token: 'tok' })
    mockedGetMe.mockResolvedValue(userResponse)

    const { wrapper, router, pinia } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await fillRequiredFields(wrapper)
    await wrapper.find('[data-testid="register-gender"]').setValue('FEMALE')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockedRegister).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="register-bio"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-skip"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-complete"]').exists()).toBe(true)

    // Strengthened: verify authentication side effect
    const { useAuthStore } = await import('@/stores/auth')
    const auth = useAuthStore(pinia)
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.token).toBe('tok')
    expect(localStorage.getItem('sarv.jwt')).toBe('tok')
    expect(auth.user).toEqual(expect.objectContaining({ username: 'alice' }))
  })

  it('shows backend detail when registration fails', async () => {
    mockedRegister.mockRejectedValue({ status: 409, title: 'Conflict', detail: 'Username is already taken' })

    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="register-error"]').text()).toContain('already taken')
  })

  it('blocks submit when passwords do not match', async () => {
    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await wrapper.find('[data-testid="register-username"]').setValue('alice')
    await wrapper.find('[data-testid="register-password"]').setValue('secret12')
    await wrapper.find('[data-testid="register-confirmPassword"]').setValue('other12')
    await wrapper.find('[data-testid="register-email"]').setValue('a@x.io')
    await wrapper.find('[data-testid="register-displayName"]').setValue('Alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="register-error"]').text()).toContain('do not match')
    expect(mockedRegister).not.toHaveBeenCalled()
  })

  it('sends confirmPassword to the backend', async () => {
    mockedRegister.mockResolvedValue({ id: 1, username: 'alice', displayName: 'Alice', email: 'a@x.io', token: 'tok' })
    mockedGetMe.mockResolvedValue(userResponse)

    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockedRegister).toHaveBeenCalledWith(expect.objectContaining({ confirmPassword: 'secret12' }))
  })

  it('skips the optional step and goes to the feed', async () => {
    mockedRegister.mockResolvedValue({ id: 1, username: 'alice', displayName: 'Alice', email: 'a@x.io', token: 'tok' })
    mockedGetMe.mockResolvedValue(userResponse)

    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="register-skip"]').exists()).toBe(true)

    await wrapper.find('[data-testid="register-skip"]').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('feed')
    expect(mockedUpdateMe).not.toHaveBeenCalled()
  })

  it('completes the optional step with profile data', async () => {
    mockedRegister.mockResolvedValue({ id: 1, username: 'alice', displayName: 'Alice', email: 'a@x.io', token: 'tok' })
    mockedGetMe.mockResolvedValue(userResponse)
    mockedUpdateMe.mockResolvedValue({ ...userResponse, bio: 'hello', location: 'Tehran' })

    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    await wrapper.find('[data-testid="register-bio"]').setValue('hello')
    await wrapper.find('[data-testid="register-location"]').setValue('Tehran')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockedUpdateMe).toHaveBeenCalledWith(
      expect.objectContaining({ bio: 'hello', location: 'Tehran' }),
    )
    expect(router.currentRoute.value.name).toBe('feed')
  })

  it('uploads a profile picture before completing', async () => {
    mockedRegister.mockResolvedValue({ id: 1, username: 'alice', displayName: 'Alice', email: 'a@x.io', token: 'tok' })
    mockedGetMe.mockResolvedValue(userResponse)
    mockedUploadMedia.mockResolvedValue({ id: 42, url: '/api/media/42' })
    mockedUpdateMe.mockResolvedValue({ ...userResponse, profilePictureId: 42 })

    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await fillRequiredFields(wrapper)
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    const file = new File(['img'], 'avatar.png', { type: 'image/png' })
    const input = wrapper.find('[data-testid="register-file"]')
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await flushPromises()

    expect(wrapper.find('[data-testid="register-file-name"]').text()).toBe('avatar.png')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockedUploadMedia).toHaveBeenCalledOnce()
    expect(mockedUpdateMe).toHaveBeenCalledWith(expect.objectContaining({ profilePictureId: 42 }))
    expect(router.currentRoute.value.name).toBe('feed')
  })
})
