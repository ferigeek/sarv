import { apiClient } from './client'
import type { CommentSort, Page, Pageable, PostCategory, PostResponse } from '@/types/api'

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

export async function repostPost(postId: number): Promise<PostResponse> {
  return createPost({
    postCategory: 'REPOST',
    content: null,
    mediaId: null,
    parentId: null,
    repostOfId: postId,
  })
}

export async function quotePost(
  postId: number,
  payload: { content?: string | null; mediaId?: number | null },
): Promise<PostResponse> {
  return createPost({
    postCategory: 'QUOTE',
    content: payload.content ?? null,
    mediaId: payload.mediaId ?? null,
    parentId: null,
    repostOfId: postId,
  })
}

export async function searchPosts(query: string, pageable: Pageable = {}): Promise<Page<PostResponse>> {
  const { data } = await apiClient.get<Page<PostResponse>>('/posts/search', {
    params: { query, ...pageable },
  })
  return data
}

export async function getComments(
  postId: number,
  sortBy: CommentSort = 'NEWEST',
  pageable: Pageable = {},
): Promise<Page<PostResponse>> {
  const { data } = await apiClient.get<Page<PostResponse>>(`/posts/${postId}/comments`, {
    params: { sortBy, ...pageable },
  })
  return data
}