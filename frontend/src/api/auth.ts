import { apiClient } from './client'
import type { UserRegisterResponse, Gender } from '@/types/api'

export interface LoginPayload {
  username: string
  password: string
}

export interface RegisterPayload {
  username: string
  password: string
  confirmPassword: string
  email: string
  displayName: string
  gender: Gender
}

export async function login({ username, password }: LoginPayload): Promise<string> {
  const { data } = await apiClient.post<string>('/auth/login', { username, password })
  return data
}

export async function register(payload: RegisterPayload): Promise<UserRegisterResponse> {
  const { data } = await apiClient.post<UserRegisterResponse>('/auth/register', payload)
  return data
}