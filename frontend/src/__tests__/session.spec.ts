import { beforeEach, describe, expect, it } from 'vitest'

import { getSessionId } from '@/utils/session'

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

describe('session', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('returns a UUID-shaped id', () => {
    expect(getSessionId()).toMatch(UUID_V4)
  })

  it('returns a stable id within the same tab session', () => {
    expect(getSessionId()).toBe(getSessionId())
    expect(sessionStorage.getItem('sarv.session_id')).toBe(getSessionId())
  })

  it('generates a fresh id after the stored one is gone', () => {
    const first = getSessionId()
    sessionStorage.removeItem('sarv.session_id')
    const second = getSessionId()
    expect(second).toMatch(UUID_V4)
    expect(second).not.toBe(first)
  })
})
