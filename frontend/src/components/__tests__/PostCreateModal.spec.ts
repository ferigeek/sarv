import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import type { MediaResponse, PostResponse } from '@/types/api'

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn<(file: File, onProgress?: (p: number) => void) => Promise<MediaResponse>>(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<import('@/types/api').MediaMetadataResponse>>(),
}))

vi.mock('@/api/posts', () => ({
  createPost: vi.fn<(payload: unknown) => Promise<PostResponse>>(),
  getPost: vi.fn<(id: number) => Promise<PostResponse>>(),
  updatePost: vi.fn<() => Promise<PostResponse>>(),
  deletePost: vi.fn<(id: number) => Promise<void>>(),
}))

import { uploadMedia as mockUploadMedia } from '@/api/media'
import { createPost as mockCreatePost } from '@/api/posts'
import PostCreateModal from '../PostCreateModal.vue'

const mockedUploadMedia = vi.mocked(mockUploadMedia)
const mockedCreatePost = vi.mocked(mockCreatePost)

function makeFile(name = 'pic.png', type = 'image/png'): File {
  return new File(['hello'], name, { type })
}

function mountComposer() {
  return mount(PostCreateModal, { attachTo: document.body })
}

describe('PostCreateModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('submit is disabled until there is content or an uploaded media', async () => {
    const wrapper = mountComposer()

    const submit = wrapper.find('[data-testid="post-create-submit"]')
    expect(submit.attributes('disabled')).toBeDefined()

    await wrapper.find('[data-testid="post-create-content"]').setValue('hello world')
    expect(wrapper.find('[data-testid="post-create-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('creates a post with content only (no media)', async () => {
    mockedCreatePost.mockResolvedValue({
      id: 7,
      userId: 1,
      postCategory: 'NORMAL',
      content: 'just text',
      createdAt: '2026-09-02T10:00:00+00:00',
      updatedAt: null,
      mediaId: null,
      repostOfId: null,
      parentId: null,
      viewCount: 0,
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
    })

    const wrapper = mountComposer()
    await wrapper.find('[data-testid="post-create-content"]').setValue('just text')
    await wrapper.find('[data-testid="post-create-submit"]').trigger('click')
    await flushPromises()

    expect(mockedCreatePost).toHaveBeenCalledWith({
      postCategory: 'NORMAL',
      content: 'just text',
      mediaId: null,
      parentId: null,
      repostOfId: null,
    })
    expect(wrapper.emitted('created')?.[0]).toEqual([7])
  })

  it('uploads media first with progress, then posts with the returned mediaId', async () => {
// Deferred so we can observe the progress bar mid-upload.

    let resolveUpload: (v: MediaResponse) => void
    mockedUploadMedia.mockImplementation((_file, onProgress) => {
      onProgress?.(0.4)
      return new Promise((r) => { resolveUpload = r })
    })
    mockedCreatePost.mockResolvedValue({
      id: 8,
      userId: 1,
      postCategory: 'NORMAL',
      content: 'with image',
      createdAt: '2026-09-02T10:00:00+00:00',
      updatedAt: null,
      mediaId: 42,
      repostOfId: null,
      parentId: null,
      viewCount: 0,
      likeCount: 0,
      dislikeCount: 0,
      commentCount: 0,
    })

    const wrapper = mountComposer()
    await wrapper.find('[data-testid="post-create-content"]').setValue('with image')

    // Pick a file
    const input = wrapper.find('[data-testid="post-create-media-input"]')
    Object.defineProperty(input.element, 'files', { value: [makeFile()] })
    await input.trigger('change')

    // Upload
    await wrapper.find('[data-testid="post-create-media-upload"]').trigger('click')
    await flushPromises()

    // While upload is in-flight, the progress bar shows a percentage
    expect(wrapper.find('[data-testid="post-upload-progress"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-upload-progress-pct"]').text()).toContain('%')

    // Complete the upload
    resolveUpload!({ id:  42, url: '/api/media/42' })
    await flushPromises()
    expect(mockedUploadMedia).toHaveBeenCalledTimes(1)

    // Now submit
    await wrapper.find('[data-testid="post-create-submit"]').trigger('click')
    await flushPromises()

    // Upload happened before the post; post used the returned mediaId
    expect(mockedCreatePost).toHaveBeenCalledWith({
      postCategory: 'NORMAL',
      content: 'with image',
      mediaId: 42,
      parentId: null,
      repostOfId: null,
    })
    expect(wrapper.emitted('created')?.[0]).toEqual([8])
  })

  it('requires upload to complete before allowing submit when media is attached', async () => {
    const wrapper = mountComposer()
    // Only media, no text content
    const input = wrapper.find('[data-testid="post-create-media-input"]')
    Object.defineProperty(input.element, 'files', { value: [makeFile()] })
    await input.trigger('change')

    // File picked but not yet uploaded → submit must be disabled
    expect(wrapper.find('[data-testid="post-create-submit"]').attributes('disabled')).toBeDefined()

    // Upload completes
    mockedUploadMedia.mockResolvedValue({ id: 99, url: '/api/media/99' })
    await wrapper.find('[data-testid="post-create-media-upload"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="post-create-submit"]').attributes('disabled')).toBeUndefined()
  })

  it('previews video files with a video element instead of an image', async () => {
    const wrapper = mountComposer()
    const input = wrapper.find('[data-testid="post-create-media-input"]')
    Object.defineProperty(input.element, 'files', {
      value: [makeFile('clip.mp4', 'video/mp4')],
    })
    await input.trigger('change')

    expect(wrapper.find('[data-testid="post-create-preview-video"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-create-preview-img"]').exists()).toBe(false)
  })

  it('previews image files (including gifs) with an image element', async () => {
    const wrapper = mountComposer()
    const input = wrapper.find('[data-testid="post-create-media-input"]')
    Object.defineProperty(input.element, 'files', {
      value: [makeFile('anim.gif', 'image/gif')],
    })
    await input.trigger('change')

    expect(wrapper.find('[data-testid="post-create-preview-img"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-create-preview-video"]').exists()).toBe(false)
  })
})
