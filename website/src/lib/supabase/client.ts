import { createBrowserClient } from '@supabase/ssr'
import type { SupabaseClient } from '@supabase/supabase-js'
import { SUPABASE_ANON_KEY, SUPABASE_URL, supabaseConfigError } from '@/lib/env'

let client: SupabaseClient | null = null

/** Browser client (session in cookies, shared with the server and proxy). One per tab. */
export function supabaseBrowser(): SupabaseClient {
  if (supabaseConfigError) throw new Error(supabaseConfigError)
  return (client ??= createBrowserClient(SUPABASE_URL, SUPABASE_ANON_KEY))
}
