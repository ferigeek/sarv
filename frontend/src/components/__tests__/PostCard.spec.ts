import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import type { PostResponse, ReactionResponse, UserResponse } from '@/types/api'

vi.mock('@/api/users', () => ({
  getMe: vi.fn<() => Promise<UserResponse>>(),
  getUser: vi.fn<(id: number) => Promise<UserResponse>>(),
  updateMe: vi.fn<(payload: unknown) => Promise<UserResponse>>(),
  searchUsers: vi.fn<(query: string, pageable?: unknown) => Promise<import('@/types/api').Page<import('@/types/api').UserResponse>>>(),
}))

vi.mock('@/api/reactions', () => ({
  addReaction: vi.fn<(postId: number, type: number) => Promise<ReactionResponse>>(),
  getReaction: vi.fn<(postId: number) => Promise<ReactionResponse>>(),
  removeReaction: vi.fn<(postId: number) => Promise<void>>(),
}))

vi.mock('@/api/media', () => ({
  uploadMedia: vi.fn<(file: File) => Promise<import('@/types/api').MediaResponse>>(),
  getMediaBlob: vi.fn<() => Promise<Blob>>(),
  getMediaMetadata: vi.fn<() => Promise<import('@/types/api').MediaMetadataResponse>>(),
}))

import { getMediaBlob as mockGetMediaBlob, getMediaMetadata as mockGetMediaMetadata } from '@/api/media'
import { addReaction as mockAddReaction, getReaction as mockGetReaction, removeReaction as mockRemoveReaction } from '@/api/reactions'
import { getUser as mockGetUser } from '@/api/users'
import { registerPixelicons } from '@/assets/icons/pixelarticons'
import PostCard from '../PostCard.vue'

registerPixelicons()

const mockedGetUser = vi.mocked(mockGetUser)
const mockedGetReaction = vi.mocked(mockGetReaction)
const mockedAddReaction = vi.mocked(mockAddReaction)
const mockedRemoveReaction = vi.mocked(mockRemoveReaction)
const mockedGetMediaBlob = vi.mocked(mockGetMediaBlob)
const mockedGetMediaMetadata = vi.mocked(mockGetMediaMetadata)

function makePost(overrides: Partial<PostResponse> = {}): PostResponse {
  return {
    id: 1,
    userId: 10,
    postCategory: 'NORMAL',
    content: 'hello world',
    createdAt: '2026-09-02T10:00:00+00:00',
    updatedAt: null,
    mediaId: null,
    repostOfId: null,
    parentId: null,
    viewCount: 5,
    likeCount: 2,
    dislikeCount: 1,
    commentCount: 0,
    ...overrides,
  }
}

describe('PostCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedGetUser.mockResolvedValue({
      id: 10,
      username: 'bob',
      displayName: 'Bob',
      bio: null,
      gender: 'MALE',
      location: null,
      profilePictureId: null,
      status: 'ACTIVE',
    })
    mockedGetReaction.mockResolvedValue({ likeCount: 2, dislikeCount: 1, userReaction: 0 })
    mockedGetMediaBlob.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    mockedGetMediaMetadata.mockResolvedValue({
      id: 42,
      size: 1,
      name: 'pic.png',
      mimeType: 'image/png',
      createdAt: '2026-09-02T10:00:00+00:00',
    })
  })

  it('renders post content, view count and like/dislike counts', async () => {
    const wrapper = mount(PostCard, { props: { post: makePost() } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="post-content"]').text()).toBe('hello world')
    expect(wrapper.find('[data-testid="post-view-count"]').text()).toContain('5')
    expect(wrapper.find('[data-testid="post-like-count"]').text()).toBe('2')
    expect(wrapper.find('[data-testid="post-dislike-count"]').text()).toBe('1')
    expect(wrapper.find('[data-testid="post-author-name"]').text()).toBe('Bob')
  })

  it('shows repost placeholder when content is null for REPOST', async () => {
    const wrapper = mount(PostCard, { props: { post: makePost({ postCategory: 'REPOST', content: null }) } })
    await flushPromises()
    expect(wrapper.find('[data-testid="post-content"]').text()).toContain('repost')
  })

  it('like button is green when already liked and red for disliked', async () => {
    mockedGetReaction.mockResolvedValue({ likeCount: 5, dislikeCount: 0, userReaction: 1 })
    const wrapper = mount(PostCard, { props: { post: makePost({ likeCount: 5 }) } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(wrapper.find('[data-testid="post-like-btn"]').classes()).toContain('post-action--liked')
    expect(wrapper.find('[data-testid="post-dislike-btn"]').classes()).not.toContain('post-action--disliked')

    // Switch to disliked
    mockedGetReaction.mockResolvedValue({ likeCount: 2, dislikeCount: 3, userReaction: -1 })
    const wrapper2 = mount(PostCard, { props: { post: makePost() } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()
    expect(wrapper2.find('[data-testid="post-dislike-btn"]').classes()).toContain('post-action--disliked')
    expect(wrapper2.find('[data-testid="post-like-btn"]').classes()).not.toContain('post-action--liked')
  })

  it('liking a post calls addReaction, updates counts, shows green and pixelated smile feedback', async () => {
    const wrapper = mount(PostCard, { props: { post: makePost() } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    mockedAddReaction.mockResolvedValue({ likeCount: 3, dislikeCount: 1, userReaction: 1 })

    await wrapper.find('[data-testid="post-like-btn"]').trigger('click')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(mockedAddReaction).toHaveBeenCalledWith(1, 1)
    expect(wrapper.find('[data-testid="post-like-count"]').text()).toBe('3')
    expect(wrapper.find('[data-testid="post-like-btn"]').classes()).toContain('post-action--liked')
    expect(wrapper.find('[data-testid="post-feedback-smile"]').exists()).toBe(true)
  })

  it('clicking liked again removes the reaction', async () => {
    mockedGetReaction.mockResolvedValue({ likeCount: 3, dislikeCount: 1, userReaction: 1 })
    const wrapper = mount(PostCard, { props: { post: makePost() } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    mockedRemoveReaction.mockResolvedValue(undefined)

    await wrapper.find('[data-testid="post-like-btn"]').trigger('click')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(mockedRemoveReaction).toHaveBeenCalledWith(1)
    expect(wrapper.find('[data-testid="post-like-btn"]').classes()).not.toContain('post-action--liked')
  })

  it('disliking a post shows red and pixelated sad feedback', async () => {
    const wrapper = mount(PostCard, { props: { post: makePost() } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    mockedAddReaction.mockResolvedValue({ likeCount: 2, dislikeCount: 2, userReaction: -1 })

    await wrapper.find('[data-testid="post-dislike-btn"]').trigger('click')
    await flushPromises()
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()

    expect(mockedAddReaction).toHaveBeenCalledWith(1, -1)
    expect(wrapper.find('[data-testid="post-dislike-btn"]').classes()).toContain('post-action--disliked')
    expect(wrapper.find('[data-testid="post-feedback-sad"]').exists()).toBe(true)
  })

  it('inert actions (repost, quote, comment) do nothing', async () => {
    const wrapper = mount(PostCard, { props: { post: makePost() } })
    await flushPromises()

    expect(wrapper.find('[data-testid="post-repost-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-quote-btn"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-comment-btn"]').exists()).toBe(true)

    await wrapper.find('[data-testid="post-repost-btn"]').trigger('click')
    await wrapper.find('[data-testid="post-quote-btn"]').trigger('click')
    await wrapper.find('[data-testid="post-comment-btn"]').trigger('click')
    await flushPromises()

    expect(mockedAddReaction).not.toHaveBeenCalled()
  })

  it('shows media when mediaId is present', async () => {
    const wrapper = mount(PostCard, { props: { post: makePost({ mediaId: 42 }) } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 20))
    await flushPromises()

    expect(mockedGetMediaBlob).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="post-media"]').exists()).toBe(true)
  })

  it('renders an image element for image media including gifs', async () => {
    mockedGetMediaMetadata.mockResolvedValue({
      id: 43,
      size: 2,
      name: 'anim.gif',
      mimeType: 'image/gif',
      createdAt: '2026-09-02T10:00:00+00:00',
    })
    mockedGetMediaBlob.mockResolvedValue(new Blob(['gif'], { type: 'image/gif' }))
    const wrapper = mount(PostCard, { props: { post: makePost({ mediaId: 43 }) } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 20))
    await flushPromises()

    expect(wrapper.find('[data-testid="post-media-img"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="post-media-video"]').exists()).toBe(false)
  })

  it('renders a video element for video media', async () => {
    mockedGetMediaMetadata.mockResolvedValue({
      id: 44,
      size: 3,
      name: 'clip.mp4',
      mimeType: 'video/mp4',
      createdAt: '2026-09-02T10:00:00+00:00',
    })
    mockedGetMediaBlob.mockResolvedValue(new Blob(['video'], { type: 'video/mp4' }))
    const wrapper = mount(PostCard, { props: { post: makePost({ mediaId: 44 }) } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 20))
    await flushPromises()

    const video = wrapper.find('[data-testid="post-media-video"]')
    expect(video.exists()).toBe(true)
    expect(video.attributes('controls')).toBeDefined()
    expect(wrapper.find('[data-testid="post-media-img"]').exists()).toBe(false)
  })

  it('falls back to the blob content type when metadata is unavailable', async () => {
    mockedGetMediaMetadata.mockRejectedValue(new Error('not found'))
    mockedGetMediaBlob.mockResolvedValue(new Blob(['video'], { type: 'video/webm' }))
    const wrapper = mount(PostCard, { props: { post: makePost({ mediaId: 45 }) } })
    await flushPromises()
    await new Promise((r) => setTimeout(r, 20))
    await flushPromises()

    expect(wrapper.find('[data-testid="post-media-video"]').exists()).toBe(true)
  })
})
