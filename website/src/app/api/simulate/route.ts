import { NextResponse, type NextRequest } from 'next/server'
import { isMock, supabaseConfigured } from '@/lib/env'
import { buildScenario, isSimScenario } from '@/lib/scenarios'
import { currentOfficer, supabaseAdmin } from '@/lib/supabase/server'

export const runtime = 'nodejs'

/**
 * POST /api/simulate { scenario } — demo safety net. Inserts realistic reports with the service role
 * (the same triggers run: numbers, flagging, rate limit). Signed-in authority + SIMULATOR_ENABLED=true only.
 */
export async function POST(request: NextRequest) {
  if (process.env.SIMULATOR_ENABLED !== 'true') return NextResponse.json({ error: 'Simulator disabled (SIMULATOR_ENABLED is not true)' }, { status: 404 })
  if (isMock || !supabaseConfigured) return NextResponse.json({ error: 'Mock mode simulates in the browser' }, { status: 400 })

  const officer = await currentOfficer()
  if (!officer) return NextResponse.json({ error: 'Not signed in' }, { status: 401 })
  if (!officer.authority) return NextResponse.json({ error: 'Authority role required' }, { status: 403 })

  const body = (await request.json().catch(() => null)) as { scenario?: unknown } | null
  if (!isSimScenario(body?.scenario)) return NextResponse.json({ error: 'Unknown scenario' }, { status: 400 })

  const rows = buildScenario(body.scenario)
  const { error } = await supabaseAdmin().from('reports').insert(rows)
  if (error) return NextResponse.json({ error: error.message }, { status: 500 })
  return NextResponse.json({ inserted: rows.length })
}
