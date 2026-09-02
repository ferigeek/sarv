import { apiClient } from './client'
import type { Page, Pageable, UserSummaryResponse } from '@/types/api'

export async function getFollowers(
  userId: number,
  pageable: Pageable = {},
): Promise<Page<UserSummaryResponse>> {
  const { data } = await apiClient.get<Page<UserSummaryResponse>>(
    `/users/${userId}/followers`,
    { params: pageable },
  )
  return data
}

export async function getFollowing(
  userId: number,
  pageable: Pageable = {},
): Promise<Page<UserSummaryResponse>> {
  const { data } = await apiClient.get<Page<UserSummaryResponse>>(
    `/users/${userId}/following`,
    { params: pageable },
  )
  return data
}

export async function follow(userId: number): Promise<void> {
  await apiClient.post(`/users/${userId}/followers`)
}

export async function unfollow(userId: number): Promise<void> {
  await apiClient.delete(`/users/${userId}/followers`)
}