import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'

const fetchMe = vi.fn<() => Promise<void>>()
const logoutSpy = vi.fn<() => void>()

interface AuthState {
  isAuthenticated: boolean
  user: unknown
}

const state: AuthState = {
  isAuthenticated: false,
  user: null,
}

function mockStore() {
  return {
    isAuthenticated: state.isAuthenticated,
    token: state.isAuthenticated ? 'jwt' : null,
    user: state.user,
    fetchMe,
    logout: () => {
      logoutSpy()
      state.isAuthenticated = false
      state.user = null
    },
  }
}

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => mockStore(),
}))

import { createAppRouter } from '../router'

function setAuth(authenticated: boolean) {
  state.isAuthenticated = authenticated
  state.user = authenticated ? { id: 1, username: 'alice', displayName: 'Alice' } : null
}

describe('router auth guard', () => {
  beforeEach(() => {
    setAuth(false)
    vi.clearAllMocks()
  })

  it('redirects unauthenticated users to login', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('keeps the redirect target when bouncing to login', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/following')
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/following')
  })

  it('lets authenticated users into protected routes', async () => {
    setAuth(true)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    expect(router.currentRoute.value.name).toBe('feed')
  })

  it('moves authenticated users away from the login page', async () => {
    setAuth(true)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/login')
    expect(router.currentRoute.value.name).toBe('feed')
  })

  it('hydrates the user when a token exists but the user is missing', async () => {
    setAuth(true)
    state.user = null
    fetchMe.mockResolvedValue(undefined)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    expect(fetchMe).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('feed')
  })

  it('logs out and redirects to login when hydration fails', async () => {
    setAuth(true)
    state.user = null
    fetchMe.mockRejectedValue(new Error('session expired'))
    const router = createAppRouter(createMemoryHistory())
    await router.push('/')
    expect(logoutSpy).toHaveBeenCalledOnce()
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('allows unauthenticated users to visit register', async () => {
    const router = createAppRouter(createMemoryHistory())
    await router.push('/register')
    expect(router.currentRoute.value.name).toBe('register')
  })

  it('allows authenticated users to stay on register for the optional step', async () => {
    setAuth(true)
    const router = createAppRouter(createMemoryHistory())
    await router.push('/register')
    expect(router.currentRoute.value.name).toBe('register')
  })
})