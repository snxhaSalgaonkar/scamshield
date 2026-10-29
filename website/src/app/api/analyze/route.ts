import { NextResponse, type NextRequest } from 'next/server'
import { supabaseConfigured } from '@/lib/env'
import { analyze, AnalyzeBody } from '@/lib/semantic/analyze'
import { allow } from '@/lib/semantic/rateLimit'
import { supabaseAnon } from '@/lib/supabase/server'

export const runtime = 'nodejs'
export const maxDuration = 30

const RATE_PER_MIN = 30

/** Who is calling: the Supabase user behind the phone's access token, or null. */
async function caller(request: NextRequest): Promise<string | null> {
  const token = /^Bearer\s+(.+)$/i.exec(request.headers.get('authorization') ?? '')?.[1]?.trim()
  if (!token) return null
  if (!supabaseConfigured) return null
  const { data, error } = await supabaseAnon().auth.getUser(token)
  return error || !data.user ? null : data.user.id
}

/**
 * POST /api/analyze — semantic score for a PII-scrubbed transcript chunk (SHARED_CONTRACT.md §6).
 * The text is never logged or stored.
 */
export async function POST(request: NextRequest) {
  const userId = await caller(request)
  if (!userId) return NextResponse.json({ error: 'Invalid or missing bearer token' }, { status: 401 })
  if (!allow(userId, RATE_PER_MIN)) {
    return NextResponse.json({ error: 'Too many requests: max 30 per minute' }, { status: 429, headers: { 'Retry-After': '60' } })
  }

  const parsed = AnalyzeBody.safeParse(await request.json().catch(() => null))
  if (!parsed.success) {
    return NextResponse.json({ error: 'Body must be {"text": string (1–2000 chars), "lang_hint": "en"|"hi"|"hinglish"|null}' }, { status: 400 })
  }

  try {
    return NextResponse.json(await analyze(parsed.data.text), { headers: { 'Cache-Control': 'no-store' } })
  } catch {
    // Model failed to load (e.g. no network on first start). The phone falls back to local rules.
    return NextResponse.json({ error: 'Semantic model unavailable' }, { status: 503 })
  }
}
