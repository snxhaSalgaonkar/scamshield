// Public (NEXT_PUBLIC_*) values are inlined at build time, so they must be read with literal
// property access, never process.env[name].

export const DATA_SOURCE: 'mock' | 'supabase' = process.env.NEXT_PUBLIC_DATA_SOURCE === 'supabase' ? 'supabase' : 'mock'
export const isMock = DATA_SOURCE === 'mock'

export const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? ''
export const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? ''
export const supabaseConfigured = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY)
