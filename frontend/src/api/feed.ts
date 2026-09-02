import { apiClient } from './client'
import type { Page, Pageable, PostResponse } from '@/types/api'

export async function getChronologicalFeed(pageable: Pageable = {}): Promise<Page<PostResponse>> {
  const { data } = await apiClient.get<Page<PostResponse>>('/feed/chronological', {
    params: pageable,
  })
  return data
}

export async function getRecommendedFeed(pageable: Pageable = {}): Promise<Page<PostResponse>> {
  const { data } = await apiClient.get<Page<PostResponse>>('/feed/recommended', {
    params: pageable,
  })
  return data
}