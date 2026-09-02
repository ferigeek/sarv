import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { login as apiLogin, register as apiRegister, type RegisterPayload } from '@/api/auth'
import { getMe } from '@/api/users'
import type { UserResponse } from '@/types/api'
import { clearToken, getToken, setToken } from '@/utils/token'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<UserResponse | null>(null)

  const isAuthenticated = computed(() => Boolean(token.value))

  async function login(username: string, password: string) {
    const newToken = await apiLogin({ username, password })
    token.value = newToken
    setToken(newToken)
    await fetchMe()
  }

  async function register(payload: RegisterPayload) {
    const response = await apiRegister(payload)
    token.value = response.token
    setToken(response.token)
    await fetchMe()
  }

  function logout() {
    token.value = null
    user.value = null
    clearToken()
  }

  async function fetchMe() {
    if (!token.value) return
    user.value = await getMe()
  }

  return { token, user, isAuthenticated, login, register, logout, fetchMe }
})