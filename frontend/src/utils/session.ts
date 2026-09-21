const SESSION_KEY = 'sarv.session_id'

function newSessionId(): string {
  try {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID()
    }
  } catch {
    // fall through to the Math.random fallback below
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}

/*
 * Frontend-owned usage session id, sent as X-Session-Id so the backend can
 * group event_logs rows (impressions, dwells, ...) of one tab visit.
 * Tab-scoped via sessionStorage: a new tab starts a new session, reloads
 * in the same tab keep it. Never used for authentication.
 */
export function getSessionId(): string {
  try {
    const stored = sessionStorage.getItem(SESSION_KEY)
    if (stored) return stored
    const id = newSessionId()
    try {
      sessionStorage.setItem(SESSION_KEY, id)
    } catch {
      // storage unavailable (e.g. private mode) — still return a usable id
    }
    return id
  } catch {
    return newSessionId()
  }
}
