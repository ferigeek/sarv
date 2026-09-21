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

  it('uses keepalive fetch with auth headers when requested', async () => {
    const fetchMock = vi.fn<() => Promise<unknown>>().mockResolvedValue({})
    vi.stubGlobal('fetch', fetchMock)
    try {
      localStorage.setItem('sarv.jwt', 'test-jwt')

      await reportPostDwell(7, { durationMs: 5000, source: 'FEED' }, { keepalive: true })

      expect(mockedPost).not.toHaveBeenCalled()
      expect(fetchMock).toHaveBeenCalledOnce()
      const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
      expect(url).toBe('/api/posts/7/dwell')
      expect(init.method).toBe('POST')
      expect(init.keepalive).toBe(true)
      expect(init.headers).toMatchObject({
        'Content-Type': 'application/json',
        Authorization: 'Bearer test-jwt',
      })
      expect((init.headers as Record<string, string>)['X-Session-Id']).toBe(
        sessionStorage.getItem('sarv.session_id'),
      )
      expect(JSON.parse(init.body as string)).toEqual({
        durationMs: 5000,
        sessionId: sessionStorage.getItem('sarv.session_id'),
        source: 'FEED',
      })
    } finally {
      vi.unstubAllGlobals()
      localStorage.clear()
    }
  })

  it('omits Authorization on the keepalive path when logged out', async () => {
    const fetchMock = vi.fn<() => Promise<unknown>>().mockResolvedValue({})
    vi.stubGlobal('fetch', fetchMock)
    try {
      await reportPostDwell(7, { durationMs: 100 }, { keepalive: true })

      const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
      expect((init.headers as Record<string, string>).Authorization).toBeUndefined()
    } finally {
      vi.unstubAllGlobals()
    }
  })

  it('never rejects when keepalive fetch fails', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn<() => Promise<unknown>>().mockRejectedValue(new Error('offline')),
    )
    try {
      await expect(
        reportPostDwell(7, { durationMs: 100 }, { keepalive: true }),
      ).resolves.toBeUndefined()
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
