import type { Session } from './types'

// Session lives in sessionStorage so closing the tab signs the officer out.
const KEY = 'rakshacall.session'

export function loadSession(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

export function saveSession(session: Session | null) {
  try {
    if (session) sessionStorage.setItem(KEY, JSON.stringify(session))
    else sessionStorage.removeItem(KEY)
  } catch {
    // Storage blocked: the session just won't survive a reload.
  }
}
