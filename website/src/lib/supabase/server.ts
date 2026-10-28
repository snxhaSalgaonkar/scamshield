import 'server-only'
import { createServerClient } from '@supabase/ssr'
import { createClient, type SupabaseClient } from '@supabase/supabase-js'
import { cookies } from 'next/headers'
import { SUPABASE_ANON_KEY, SUPABASE_URL } from '@/lib/env'

/** Per-request client acting as the signed-in officer (RLS applies). */
export async function supabaseServer(): Promise<SupabaseClient> {
  const store = await cookies()
  return createServerClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    cookies: {
      getAll: () => store.getAll(),
      setAll: (toSet) => {
        try {
          toSet.forEach(({ name, value, options }) => store.set(name, value, options))
        } catch {
          // Called from a context that can't set cookies; the proxy refreshes the session instead.
        }
      },
    },
  })
}

/** Service-role client: bypasses RLS. Server only, simulator only. */
export function supabaseAdmin(): SupabaseClient {
  const key = process.env.SUPABASE_SERVICE_ROLE_KEY
  if (!SUPABASE_URL || !key) throw new Error('SUPABASE_SERVICE_ROLE_KEY is not set')
  return createClient(SUPABASE_URL, key, { auth: { persistSession: false, autoRefreshToken: false } })
}

/** Stateless anon client, e.g. to verify a phone's bearer token. */
export function supabaseAnon(): SupabaseClient {
  return createClient(SUPABASE_URL, SUPABASE_ANON_KEY, { auth: { persistSession: false, autoRefreshToken: false } })
}

/**
 * Who is calling, from the session cookie. `authority` is true only when the profile row says so.
 * Route handlers use this to enforce "authority only".
 */
export async function currentOfficer(): Promise<{ id: string; email: string | null; authority: boolean } | null> {
  const supabase = await supabaseServer()
  const { data, error } = await supabase.auth.getUser()
  if (error || !data.user) return null
  const { data: profile } = await supabase.from('profiles').select('role').eq('id', data.user.id).maybeSingle()
  return { id: data.user.id, email: data.user.email ?? null, authority: profile?.role === 'authority' }
}
