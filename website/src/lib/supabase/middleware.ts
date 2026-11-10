import { createServerClient } from '@supabase/ssr'
import { NextResponse, type NextRequest } from 'next/server'
import { SUPABASE_ANON_KEY, SUPABASE_URL } from '@/lib/env'

/**
 * Refreshes the Supabase session cookie and reports whether the caller is a signed-in authority.
 * Must return `response` (or copy its cookies) so refreshed tokens reach the browser.
 */
export async function updateSession(request: NextRequest) {
  let response = NextResponse.next({ request })
  try {
    if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
      return { response, user: null, authority: false }
    }
    const supabase = createServerClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
      cookies: {
        getAll: () => request.cookies.getAll(),
        setAll: (toSet, headers) => {
          toSet.forEach(({ name, value }) => request.cookies.set(name, value))
          response = NextResponse.next({ request })
          toSet.forEach(({ name, value, options }) => response.cookies.set(name, value, options))
          Object.entries(headers ?? {}).forEach(([k, v]) => response.headers.set(k, v))
        },
      },
    })

    const { data } = await supabase.auth.getUser()
    const user = data?.user ?? null
    let authority = false
    if (user) {
      const { data: profile } = await supabase.from('profiles').select('role').eq('id', user.id).maybeSingle()
      authority = profile?.role === 'authority'
    }
    return { response, user, authority }
  } catch (err) {
    console.error('updateSession error:', err)
    return { response, user: null, authority: false }
  }
}
