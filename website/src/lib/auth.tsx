'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { isMock } from './env'
import { supabaseBrowser } from './supabase/client'

export interface Officer {
  id: string
  email: string
  /** Display name derived from the email ("asha.verma@…" → "Asha Verma"). */
  name: string
}

interface AuthValue {
  officer: Officer | null
  /** false until the stored session has been read */
  ready: boolean
  signIn(email: string, password: string): Promise<Officer>
  signOut(): Promise<void>
}

export const RESTRICTED_MESSAGE = 'Access restricted to authorised officials.'

const MOCK_KEY = 'rakshacall.mock-session'

const nameFromEmail = (email: string) =>
  email
    .split('@')[0]
    .split(/[._-]+/)
    .filter(Boolean)
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(' ') || email

const toOfficer = (id: string, email: string): Officer => ({ id, email, name: nameFromEmail(email) })

function readMock(): Officer | null {
  try {
    const raw = sessionStorage.getItem(MOCK_KEY)
    return raw ? (JSON.parse(raw) as Officer) : null
  } catch {
    return null
  }
}

function writeMock(o: Officer | null) {
  try {
    if (o) sessionStorage.setItem(MOCK_KEY, JSON.stringify(o))
    else sessionStorage.removeItem(MOCK_KEY)
  } catch {
    // storage blocked: session just won't survive a reload
  }
}

/** Signs in with Supabase and rejects anyone whose profile is not role 'authority'. */
async function supabaseSignIn(email: string, password: string): Promise<Officer> {
  const sb = supabaseBrowser()
  const { data, error } = await sb.auth.signInWithPassword({ email, password })
  if (error || !data.user) {
    throw new Error(error?.message === 'Invalid login credentials' ? 'Wrong email or password.' : (error?.message ?? 'Sign-in failed.'))
  }
  const { data: profile, error: profileError } = await sb.from('profiles').select('role').eq('id', data.user.id).maybeSingle()
  if (profileError || profile?.role !== 'authority') {
    await sb.auth.signOut()
    throw new Error(RESTRICTED_MESSAGE)
  }
  return toOfficer(data.user.id, data.user.email ?? email)
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [officer, setOfficer] = useState<Officer | null>(null)
  const [ready, setReady] = useState(false)

  useEffect(() => {
    if (isMock) {
      setOfficer(readMock())
      setReady(true)
      return
    }
    const sb = supabaseBrowser()
    let alive = true
    ;(async () => {
      const { data } = await sb.auth.getUser()
      let next: Officer | null = null
      if (data.user) {
        // A stored session only counts if it belongs to an authority (a citizen session is dropped).
        const { data: profile } = await sb.from('profiles').select('role').eq('id', data.user.id).maybeSingle()
        if (profile?.role === 'authority') next = toOfficer(data.user.id, data.user.email ?? '')
        else await sb.auth.signOut()
      }
      if (!alive) return
      setOfficer(next)
      setReady(true)
    })()
    const { data: sub } = sb.auth.onAuthStateChange((event) => {
      if (event === 'SIGNED_OUT') setOfficer(null)
    })
    return () => {
      alive = false
      sub.subscription.unsubscribe()
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    let next: Officer
    if (isMock) {
      // Demo: any password works; an email containing "citizen" shows the role rejection.
      if (/citizen/i.test(email)) throw new Error(RESTRICTED_MESSAGE)
      next = toOfficer(`mock-${email}`, email)
      writeMock(next)
    } else {
      next = await supabaseSignIn(email, password)
    }
    setOfficer(next)
    return next
  }, [])

  const signOut = useCallback(async () => {
    if (isMock) writeMock(null)
    else await supabaseBrowser().auth.signOut()
    setOfficer(null)
  }, [])

  const value = useMemo(() => ({ officer, ready, signIn, signOut }), [officer, ready, signIn, signOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
