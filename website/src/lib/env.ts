// Public (NEXT_PUBLIC_*) values are inlined at build time, so they must be read with literal
// property access, never process.env[name].

export const SUPABASE_URL = process.env.NEXT_PUBLIC_SUPABASE_URL ?? ''
export const SUPABASE_ANON_KEY = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY ?? ''
export const supabaseConfigured = Boolean(SUPABASE_URL && SUPABASE_ANON_KEY)
