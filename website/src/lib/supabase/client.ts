import { createBrowserClient } from '@supabase/ssr'
import type { SupabaseClient } from '@supabase/supabase-js'
import { SUPABASE_ANON_KEY, SUPABASE_URL } from '@/lib/env'

let client: SupabaseClient | null = null

/** Browser client (session in cookies, shared with the server and proxy). One per tab. */
export function supabaseBrowser(): SupabaseClient {
  if (!SUPABASE_URL || !SUPABASE_ANON_KEY) {
    throw new Error('Supabase is not configured: set NEXT_PUBLIC_SUPABASE_URL and NEXT_PUBLIC_SUPABASE_ANON_KEY in .env.local')
  }
  return (client ??= createBrowserClient(SUPABASE_URL, SUPABASE_ANON_KEY))
}
