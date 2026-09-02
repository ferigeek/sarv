import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

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
  return { wrapper, router }
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

    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await wrapper.find('[data-testid="register-username"]').setValue('alice')
    await wrapper.find('[data-testid="register-password"]').setValue('secret12')
    await wrapper.find('[data-testid="register-email"]').setValue('a@x.io')
    await wrapper.find('[data-testid="register-displayName"]').setValue('Alice')
    await wrapper.find('[data-testid="register-gender"]').setValue('FEMALE')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(mockedRegister).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="register-bio"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-skip"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="register-complete"]').exists()).toBe(true)
  })

  it('shows backend detail when registration fails', async () => {
    mockedRegister.mockRejectedValue({ status: 409, title: 'Conflict', detail: 'Username is already taken' })

    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await wrapper.find('[data-testid="register-username"]').setValue('alice')
    await wrapper.find('[data-testid="register-password"]').setValue('secret12')
    await wrapper.find('[data-testid="register-email"]').setValue('a@x.io')
    await wrapper.find('[data-testid="register-displayName"]').setValue('Alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="register-error"]').text()).toContain('already taken')
  })

  it('skips the optional step and goes to the feed', async () => {
    mockedRegister.mockResolvedValue({ id: 1, username: 'alice', displayName: 'Alice', email: 'a@x.io', token: 'tok' })
    mockedGetMe.mockResolvedValue(userResponse)

    const { wrapper, router } = mountRegister()
    await router.push('/register')
    await flushPromises()

    await wrapper.find('[data-testid="register-username"]').setValue('alice')
    await wrapper.find('[data-testid="register-password"]').setValue('secret12')
    await wrapper.find('[data-testid="register-email"]').setValue('a@x.io')
    await wrapper.find('[data-testid="register-displayName"]').setValue('Alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 60))
    await flushPromises()

    await wrapper.find('[data-testid="register-skip"]').trigger('click')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'feed' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }

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

    await wrapper.find('[data-testid="register-username"]').setValue('alice')
    await wrapper.find('[data-testid="register-password"]').setValue('secret12')
    await wrapper.find('[data-testid="register-email"]').setValue('a@x.io')
    await wrapper.find('[data-testid="register-displayName"]').setValue('Alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 60))
    await flushPromises()

    await wrapper.find('[data-testid="register-bio"]').setValue('hello')
    await wrapper.find('[data-testid="register-location"]').setValue('Tehran')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'feed' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }

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

    await wrapper.find('[data-testid="register-username"]').setValue('alice')
    await wrapper.find('[data-testid="register-password"]').setValue('secret12')
    await wrapper.find('[data-testid="register-email"]').setValue('a@x.io')
    await wrapper.find('[data-testid="register-displayName"]').setValue('Alice')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 60))
    await flushPromises()

    const file = new File(['img'], 'avatar.png', { type: 'image/png' })
    const input = wrapper.find('[data-testid="register-file"]')
    // jsdom file input: set files property then trigger change
    Object.defineProperty(input.element, 'files', { value: [file] })
    await input.trigger('change')
    await flushPromises()

    expect(wrapper.find('[data-testid="register-file-name"]').text()).toBe('avatar.png')

    await wrapper.find('form').trigger('submit')
    await flushPromises()
    {
      const start = Date.now()
      while (router.currentRoute.value.name !== 'feed' && Date.now() - start < 1000) {
        await new Promise((r) => setTimeout(r, 20))
        await flushPromises()
      }
    }

    expect(mockedUploadMedia).toHaveBeenCalledOnce()
    expect(mockedUpdateMe).toHaveBeenCalledWith(expect.objectContaining({ profilePictureId: 42 }))
    expect(router.currentRoute.value.name).toBe('feed')
  })
})
