import { apiClient } from './client'
import type {
  Gender,
  Page,
  Pageable,
  PostResponse,
  ReactionFilter,
  UserResponse,
  UserStatsResponse,
  UserSummaryResponse,
} from '@/types/api'

export interface UserUpdatePayload {
  displayName: string
  bio?: string | null
  location?: string | null
  profilePictureId?: number | null
  gender: Gender
}

export async function getMe(): Promise<UserResponse> {
  const { data } = await apiClient.get<UserResponse>('/users/me')
  return data
}

export async function getUser(userId: number): Promise<UserResponse> {
  const { data } = await apiClient.get<UserResponse>(`/users/${userId}`)
  return data
}

export async function updateMe(payload: UserUpdatePayload): Promise<UserResponse> {
  const { data } = await apiClient.put<UserResponse>('/users/me', payload)
  return data
}

export async function searchUsers(
  query: string,
  pageable: Pageable = {},
): Promise<Page<UserSummaryResponse>> {
  const { data } = await apiClient.get<Page<UserSummaryResponse>>('/users', {
    params: { query, ...pageable },
  })
  return data
}

export async function getUserPosts(
  userId: number,
  pageable: Pageable = {},
): Promise<Page<PostResponse>> {
  const { data } = await apiClient.get<Page<PostResponse>>(`/users/${userId}/posts`, {
    params: pageable,
  })
  return data
}

export async function getReactedPosts(
  userId: number,
  filter: ReactionFilter = 'ALL',
  pageable: Pageable = {},
): Promise<Page<PostResponse>> {
  const { data } = await apiClient.get<Page<PostResponse>>(`/users/${userId}/reacted-posts`, {
    params: { filter, ...pageable },
  })
  return data
}

export async function getUserStats(userId: number): Promise<UserStatsResponse> {
  const { data } = await apiClient.get<UserStatsResponse>(`/users/${userId}/stats`)
  return data
}