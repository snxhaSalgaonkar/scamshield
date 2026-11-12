// Public (NEXT_PUBLIC_*) values are inlined at build time, so they must be read with literal
// property access, never process.env[name].

const configuredDataSource = process.env.NEXT_PUBLIC_DATA_SOURCE?.trim().toLowerCase()

if (configuredDataSource && configuredDataSource !== 'mock' && configuredDataSource !== 'supabase') {
  throw new Error(
    `Invalid NEXT_PUBLIC_DATA_SOURCE "${configuredDataSource}". Use exactly "mock" or "supabase".`,
  )
}

export const DATA_SOURCE: 'mock' | 'supabase' = configuredDataSource === 'supabase' ? 'supabase' : 'mock'
export const isMock = DATA_SOURCE === 'mock'

export const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? ''
export const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? ''

function getSupabaseUrlError(value: string): string | null {
  if (!value.trim()) return 'NEXT_PUBLIC_SUPABASE_URL is missing. Set it to the Supabase project URL in website/.env.local and Vercel.'
  try {
    const url = new URL(value.trim())
    const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1'
    if (url.protocol !== 'https:' && !(local && url.protocol === 'http:')) {
      return 'NEXT_PUBLIC_SUPABASE_URL must use HTTPS (HTTP is allowed for localhost only).'
    }
    if (url.username || url.password || url.search || url.hash || (url.pathname !== '/' && url.pathname !== '')) {
      return 'NEXT_PUBLIC_SUPABASE_URL must be the project base URL only, such as https://<project-ref>.supabase.co. Remove paths like /auth/v1 or /rest/v1.'
    }
    return null
  } catch {
    return 'NEXT_PUBLIC_SUPABASE_URL is not a valid URL. Use the project URL from Supabase → Project Settings → API.'
  }
}

export const supabaseUrlError = getSupabaseUrlError(SUPABASE_URL)
export const supabaseConfigError =
  supabaseUrlError ?? (!SUPABASE_ANON_KEY ? 'NEXT_PUBLIC_SUPABASE_ANON_KEY is missing. Set the project anon/publishable key in website/.env.local and Vercel.' : null)
export const supabaseConfigured = !supabaseConfigError
