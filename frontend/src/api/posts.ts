import { apiClient } from './client'
import type { PostCategory, PostResponse } from '@/types/api'

export interface PostCreatePayload {
  postCategory: PostCategory
  content?: string | null
  mediaId?: number | null
  parentId?: number | null
  repostOfId?: number | null
}

export interface PostUpdatePayload {
  content?: string | null
  mediaId?: number | null
}

export async function getPost(postId: number): Promise<PostResponse> {
  const { data } = await apiClient.get<PostResponse>(`/posts/${postId}`)
  return data
}

export async function createPost(payload: PostCreatePayload): Promise<PostResponse> {
  const { data } = await apiClient.post<PostResponse>('/posts', payload)
  return data
}

export async function updatePost(postId: number, payload: PostUpdatePayload): Promise<PostResponse> {
  const { data } = await apiClient.put<PostResponse>(`/posts/${postId}`, payload)
  return data
}

export async function deletePost(postId: number): Promise<void> {
  await apiClient.delete(`/posts/${postId}`)
}