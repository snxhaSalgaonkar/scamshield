import { NextResponse, type NextRequest } from 'next/server'
import { csvFilename, reportsToCsv } from '@/lib/csv'
import { supabaseConfigured } from '@/lib/env'
import { applyFilters, filtersFromParams } from '@/lib/filters'
import { currentOfficer, supabaseServer } from '@/lib/supabase/server'
import type { Report } from '@/lib/types'

export const runtime = 'nodejs'

/** Hard cap so one export can't pull the whole table. */
const MAX_ROWS = 10_000
const BATCH = 1000

/** GET /api/export?scamType=…&band=…  → CSV of matching reports. Authority only; uses the officer's own session (RLS applies). */
export async function GET(request: NextRequest) {
  if (!supabaseConfigured) return NextResponse.json({ error: 'Supabase is not configured' }, { status: 500 })
  const officer = await currentOfficer()
  if (!officer) return NextResponse.json({ error: 'Not signed in' }, { status: 401 })
  if (!officer.authority) return NextResponse.json({ error: 'Authority role required' }, { status: 403 })

  const filters = filtersFromParams(request.nextUrl.searchParams)
  const supabase = await supabaseServer()
  const rows: Report[] = []
  for (let from = 0; from < MAX_ROWS; from += BATCH) {
    const { data, error } = await applyFilters(supabase.from('reports').select('*'), filters)
      .order('created_at', { ascending: false })
      .range(from, Math.min(from + BATCH, MAX_ROWS) - 1)
    if (error) return NextResponse.json({ error: error.message }, { status: 500 })
    rows.push(...((data ?? []) as Report[]))
    if (!data || data.length < BATCH) break
  }

  return new NextResponse(reportsToCsv(rows), {
    headers: {
      'Content-Type': 'text/csv; charset=utf-8',
      'Content-Disposition': `attachment; filename="${csvFilename()}"`,
      'Cache-Control': 'no-store',
    },
  })
}
