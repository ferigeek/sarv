import { apiClient } from './client'
import type { MediaMetadataResponse, MediaResponse } from '@/types/api'

export type UploadProgressHandler = (progress: number) => void

/* Uploads a file to /api/media. Accepts an optional progress callback (0..1) so
   the composer can render a pixelated progress bar while the bytes go up. */
export async function uploadMedia(
  file: File,
  onProgress?: UploadProgressHandler,
): Promise<MediaResponse> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await apiClient.post<MediaResponse>('/media', form, {
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        onProgress(Math.min(e.loaded / e.total, 1))
      }
    },
  })
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