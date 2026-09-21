import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import type { LoginPayload, RegisterPayload } from '@/api/auth'
import type { UserRegisterResponse, UserSummaryResponse } from '@/types/api'

vi.mock('@/api/auth', () => ({
  login: vi.fn<(payload: LoginPayload) => Promise<string>>(),
  register: vi.fn<(payload: RegisterPayload) => Promise<UserRegisterResponse>>(),
}))

vi.mock('@/api/users', () => ({
  getMeSummary: vi.fn<() => Promise<UserSummaryResponse>>(),
}))

import { login as mockLogin, register as mockRegister } from '@/api/auth'
import { getMeSummary as mockGetMeSummary } from '@/api/users'
import { useAuthStore } from '../stores/auth'

const mockedLogin = vi.mocked(mockLogin)
const mockedRegister = vi.mocked(mockRegister)
const mockedGetMeSummary = vi.mocked(mockGetMeSummary)

const usr: UserSummaryResponse = {
  id: 1,
  username: 'alice',
  displayName: 'Alice',
  profilePictureId: null,
}

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    setActivePinia(createPinia())
  })

  it('starts unauthenticated with no token', () => {
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(false)
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
  })

  it('login stores the token, persists it and loads the current user', async () => {
    mockedLogin.mockResolvedValue('jwt-token')
    mockedGetMeSummary.mockResolvedValue(usr)
    const auth = useAuthStore()

    await auth.login('alice', 'secret12')

    expect(mockLogin).toHaveBeenCalledWith({ username: 'alice', password: 'secret12' })
    expect(mockGetMeSummary).toHaveBeenCalledOnce()
    expect(auth.token).toBe('jwt-token')
    expect(auth.user?.username).toBe('alice')
    expect(localStorage.getItem('sarv.jwt')).toBe('jwt-token')
    expect(auth.isAuthenticated).toBe(true)
  })

  it('register stores the token and loads the current user', async () => {
    mockedRegister.mockResolvedValue({ id: 1, username: 'alice', displayName: 'Alice', email: 'alice@x.io', token: 'reg-token' })
    mockedGetMeSummary.mockResolvedValue(usr)
    const auth = useAuthStore()

    await auth.register({
      username: 'alice',
      password: 'secret12',
      confirmPassword: 'secret12',
      email: 'alice@x.io',
      displayName: 'Alice',
      gender: 'FEMALE',
    })

    expect(auth.token).toBe('reg-token')
    expect(localStorage.getItem('sarv.jwt')).toBe('reg-token')
    expect(auth.user?.username).toBe('alice')
  })

  it('logout clears the token, user and storage', async () => {
    mockedLogin.mockResolvedValue('jwt-token')
    mockedGetMeSummary.mockResolvedValue(usr)
    const auth = useAuthStore()
    await auth.login('alice', 'secret12')

    auth.logout()

    expect(auth.isAuthenticated).toBe(false)
    expect(auth.token).toBeNull()
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('sarv.jwt')).toBeNull()
  })

  it('restores the authenticated state from a persisted token', () => {
    localStorage.setItem('sarv.jwt', 'persisted-token')
    const auth = useAuthStore()
    expect(auth.isAuthenticated).toBe(true)
    expect(auth.token).toBe('persisted-token')
  })
})