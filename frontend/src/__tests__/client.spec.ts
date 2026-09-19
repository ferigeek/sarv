import { beforeEach, describe, expect, it } from 'vitest'
import type { InternalAxiosRequestConfig } from 'axios'

import { apiClient } from '@/api/client'
import { getSessionId } from '@/utils/session'

describe('apiClient', () => {
  beforeEach(() => {
    sessionStorage.clear()
    localStorage.clear()
  })

  it('attaches the X-Session-Id header to requests', async () => {
    let captured: InternalAxiosRequestConfig | undefined
    await apiClient.get('/ping', {
      adapter: (async (config) => {
        captured = config
        return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
      }) as never,
    })

    expect(captured?.headers?.['X-Session-Id']).toBe(getSessionId())
  })
})
