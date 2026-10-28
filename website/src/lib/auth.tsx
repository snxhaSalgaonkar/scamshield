import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { dataSource } from './data'
import { loadSession, saveSession } from './session'
import type { OfficerRole, Session } from './types'

interface AuthValue {
  session: Session | null
  signIn(email: string, password: string): Promise<Session>
  signOut(): void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(loadSession)

  const signIn = useCallback(async (email: string, password: string) => {
    const next = await dataSource.login(email, password)
    if (next.officer.role !== 'officer' && next.officer.role !== 'admin') {
      throw new Error('This account is not authorised for the authority console.')
    }
    saveSession(next)
    setSession(next)
    return next
  }, [])

  const signOut = useCallback(() => {
    saveSession(null)
    setSession(null)
  }, [])

  const value = useMemo(() => ({ session, signIn, signOut }), [session, signIn, signOut])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}

/** Route guard: sends signed-out users to /login, and non-admins away from admin pages. */
export function RequireAuth({ role, children }: { role?: OfficerRole; children: ReactNode }) {
  const { session } = useAuth()
  const location = useLocation()
  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  if (role === 'admin' && session.officer.role !== 'admin') return <Navigate to="/dashboard" replace />
  return <>{children}</>
}
