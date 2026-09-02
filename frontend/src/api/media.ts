import { apiClient } from './client'
import type { MediaMetadataResponse, MediaResponse } from '@/types/api'

export async function uploadMedia(file: File): Promise<MediaResponse> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<MediaResponse>('/media', form)
  return data
}

export async function getMediaBlob(mediaId: number): Promise<Blob> {
  const { data } = await apiClient.get<Blob>(`/media/${mediaId}`, { responseType: 'blob' })
  return data
}

export async function getMediaMetadata(mediaId: number): Promise<MediaMetadataResponse> {
  const { data } = await apiClient.get<MediaMetadataResponse>(`/media/${mediaId}/metadata`)
  return data
}