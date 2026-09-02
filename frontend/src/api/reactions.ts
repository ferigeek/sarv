import { apiClient } from './client'
import type { ReactionResponse, ReactionType } from '@/types/api'

export async function addReaction(
  postId: number,
  reactionType: ReactionType,
): Promise<ReactionResponse> {
  const { data } = await apiClient.post<ReactionResponse>(`/posts/${postId}/reactions`, {
    reactionType,
  })
  return data
}

export async function getReaction(postId: number): Promise<ReactionResponse> {
  const { data } = await apiClient.get<ReactionResponse>(`/posts/${postId}/reactions`)
  return data
}

export async function removeReaction(postId: number): Promise<void> {
  await apiClient.delete(`/posts/${postId}/reactions`)
}