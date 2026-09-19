import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({
  apiClient: { post: vi.fn<() => Promise<unknown>>() },
}))

import { apiClient } from '@/api/client'
import { reportPostDwell } from '@/api/posts'

const mockedPost = vi.mocked(apiClient.post)

describe('reportPostDwell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    mockedPost.mockResolvedValue({ data: {} })
  })

  it('posts duration, session and source to the dwell endpoint', async () => {
    await reportPostDwell(7, { durationMs: 5000, source: 'DETAIL' })

    expect(mockedPost).toHaveBeenCalledOnce()
    expect(mockedPost).toHaveBeenCalledWith(
      '/posts/7/dwell',
      expect.objectContaining({ durationMs: 5000, source: 'DETAIL' }),
    )
    const body = mockedPost.mock.calls[0]?.[1] as { sessionId: string }
    expect(body.sessionId).toBe(sessionStorage.getItem('sarv.session_id'))
  })

  it('prefers an explicit session id when given', async () => {
    await reportPostDwell(7, { durationMs: 100, sessionId: 'custom-session' })

    expect(mockedPost).toHaveBeenCalledWith(
      '/posts/7/dwell',
      expect.objectContaining({ sessionId: 'custom-session' }),
    )
  })

  it('never rejects on network failure', async () => {
    mockedPost.mockRejectedValue(new Error('offline'))

    await expect(reportPostDwell(7, { durationMs: 100 })).resolves.toBeUndefined()
  })
})
