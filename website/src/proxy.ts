import { NextResponse, type NextRequest } from 'next/server'
import { supabaseConfigured } from '@/lib/env'
import { updateSession } from '@/lib/supabase/middleware'

/**
 * Guards the officer-only pages: a Supabase session AND profiles.role = 'authority'.
 * API routes check auth themselves (they must answer 401/403, not redirect).
 */
export async function proxy(request: NextRequest) {
  if (!supabaseConfigured) return NextResponse.next()

  const { response, user, authority } = await updateSession(request)
  if (user && authority) return response

  const url = request.nextUrl.clone()
  url.pathname = '/login'
  url.search = ''
  if (user && !authority) url.searchParams.set('error', 'restricted')
  else url.searchParams.set('next', request.nextUrl.pathname + request.nextUrl.search)
  const redirect = NextResponse.redirect(url)
  // Keep any refreshed auth cookies.
  response.cookies.getAll().forEach((c) => redirect.cookies.set(c))
  return redirect
}

export const config = {
  matcher: ['/dashboard/:path*'],
}
