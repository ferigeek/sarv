import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory } from 'vue-router'

import type { LoginPayload, RegisterPayload } from '@/api/auth'
import type { UserRegisterResponse, UserResponse, UserSummaryResponse, Page } from '@/types/api'

vi.mock('@/api/auth', () => ({
  login: vi.fn<(payload: LoginPayload) => Promise<string>>(),
  register: vi.fn<(payload: RegisterPayload) => Promise<UserRegisterResponse>>(),
}))

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<(query: string) => Promise<Page<UserSummaryResponse>>>(),
}))

import { login as mockLogin } from '@/api/auth'
import { getMe as mockGetMe } from '@/api/users'
import { createAppRouter } from '../router'
import LoginView from '../views/LoginView.vue'

const mockedLogin = vi.mocked(mockLogin)
const mockedGetMe = vi.mocked(mockGetMe)

function mountLogin() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createAppRouter(createMemoryHistory())
  const wrapper = mount(LoginView, {
    global: {
      plugins: [pinia, router],
    },
  })
  return { wrapper, router, pinia }
}

describe('LoginView', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('renders the login form and a link to registration', async () => {
    const { wrapper, router } = mountLogin()
    await router.push('/login')
    await flushPromises()

    expect(wrapper.find('[data-testid="login-username"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="login-password"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="login-submit"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="login-to-register"]').exists()).toBe(true)
  })

  it('shows a validation error when fields are empty', async () => {
    const { wrapper, router } = mountLogin()
    await router.push('/login')
    await flushPromises()

    await wrapper.find('[data-testid="login-submit"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="login-error"]').text()).toContain('required')
    expect(mockedLogin).not.toHaveBeenCalled()
  })

  it('logs in and redirects to the feed on success', async () => {
    mockedLogin.mockResolvedValue('jwt')
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
    const { wrapper, router } = mountLogin()
    await router.push('/login')
    await flushPromises()

    await wrapper.find('[data-testid="login-username"]').setValue('alice')
    await wrapper.find('[data-testid="login-password"]').setValue('secret12')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 60))
    await flushPromises()

    expect(mockedLogin).toHaveBeenCalledWith({ username: 'alice', password: 'secret12' })
    expect(router.currentRoute.value.name).toBe('feed')
  })

  it('shows an invalid credentials message on 401', async () => {
    mockedLogin.mockRejectedValue({ status: 401, title: 'Unauthorized', detail: 'Bad credentials' })
    const { wrapper, router } = mountLogin()
    await router.push('/login')
    await flushPromises()

    await wrapper.find('[data-testid="login-username"]').setValue('alice')
    await wrapper.find('[data-testid="login-password"]').setValue('wrongpass')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="login-error"]').text()).toContain('Invalid username')
  })

  it('shows the backend detail on other errors', async () => {
    mockedLogin.mockRejectedValue({ status: 400, title: 'Bad Request', detail: 'Username is required' })
    const { wrapper, router } = mountLogin()
    await router.push('/login')
    await flushPromises()

    await wrapper.find('[data-testid="login-username"]').setValue('a')
    await wrapper.find('[data-testid="login-password"]').setValue('short')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="login-error"]').text()).toContain('Username is required')
  })
})
