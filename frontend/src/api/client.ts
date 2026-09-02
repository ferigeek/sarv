import axios, { AxiosError } from 'axios'

import type { ProblemDetail } from '@/types/api'
import { clearToken, getToken } from '@/utils/token'

export interface ApiError {
  status: number
  title: string
  detail: string
  instance?: string
}

let onSessionExpiredHandler: (() => void) | null = null

/* The router wires this hook (in the auth/layout phase) so a dead session
   redirects the user to the login screen. Kept here to avoid a circular import. */
export function setOnSessionExpired(handler: (() => void) | null): void {
  onSessionExpiredHandler = handler
}

export const apiClient = axios.create({
  baseURL: '/api',
})

apiClient.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ProblemDetail>) => {
    // A missing/invalid/expired JWT yields 403 with an empty body (Spring Security entry point).
    if (error.response?.status === 403 && !isProblemBody(error.response.data)) {
      clearToken()
      onSessionExpiredHandler?.()
    }
    return Promise.reject(toApiError(error))
  },
)

function isProblemBody(data: unknown): data is ProblemDetail {
  return Boolean(data && typeof data === 'object' && 'status' in data)
}

function toApiError(error: AxiosError<ProblemDetail>): ApiError {
  const body = error.response?.data
  return {
    status: error.response?.status ?? 0,
    title: body?.title ?? error.message,
    detail: body?.detail ?? error.message,
    instance: body?.instance,
  }
}