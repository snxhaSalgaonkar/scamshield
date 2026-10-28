import { digitsOf } from './format'
import { bandRange } from './risk'
import { BANDS, DETECTED_VIA, REPORT_STATUSES, SCAM_TYPES, type Report, type ReportFilters } from './types'

/** In-memory version of the filters (mock mode, and checking realtime rows against a view). */
export function matchesFilters(r: Report, f: ReportFilters): boolean {
  const t = new Date(r.created_at).getTime()
  if (f.from && t < new Date(f.from).getTime()) return false
  if (f.to && t >= new Date(f.to).getTime()) return false
  if (f.scamType && r.scam_type !== f.scamType) return false
  if (f.band) {
    const [lo, hi] = bandRange[f.band]
    if (r.risk_score < lo || r.risk_score > hi) return false
  }
  if (f.status && r.status !== f.status) return false
  if (f.detectedVia && !r.detected_via.includes(f.detectedVia)) return false
  if (f.circle && r.telecom_circle !== f.circle) return false
  if (f.prefix && r.number_prefix !== f.prefix) return false
  if (f.number && !r.caller_number.includes(digitsOf(f.number))) return false
  return true
}

/** Minimal surface of a PostgREST filter builder that we use, so the same code runs server- and client-side. */
interface FilterableQuery {
  gte(column: string, value: unknown): FilterableQuery
  lt(column: string, value: unknown): FilterableQuery
  lte(column: string, value: unknown): FilterableQuery
  eq(column: string, value: unknown): FilterableQuery
  contains(column: string, value: unknown): FilterableQuery
  ilike(column: string, pattern: string): FilterableQuery
}

/**
 * Adds the filters to a Supabase query and returns the same builder type. (Unconstrained generic on
 * purpose: constraining Q to the PostgREST builder type makes TypeScript's inference explode.)
 */
export function applyFilters<Q>(query: Q, f: ReportFilters): Q {
  let q = query as unknown as FilterableQuery
  if (f.from) q = q.gte('created_at', f.from)
  if (f.to) q = q.lt('created_at', f.to)
  if (f.scamType) q = q.eq('scam_type', f.scamType)
  if (f.band) {
    const [lo, hi] = bandRange[f.band]
    q = q.gte('risk_score', lo).lte('risk_score', hi)
  }
  if (f.status) q = q.eq('status', f.status)
  if (f.detectedVia) q = q.contains('detected_via', [f.detectedVia])
  if (f.circle) q = q.eq('telecom_circle', f.circle)
  if (f.prefix) q = q.eq('number_prefix', f.prefix)
  const digits = f.number ? digitsOf(f.number) : ''
  if (digits) q = q.ilike('caller_number', `%${digits}%`)
  return q as unknown as Q
}

// ---- URL <-> filters (reports page + /api/export share these) ----

const KEYS = ['from', 'to', 'scamType', 'band', 'status', 'detectedVia', 'circle', 'number', 'prefix'] as const

const isIso = (s: string) => !Number.isNaN(new Date(s).getTime())

/** Parses and validates filters from a query string; unknown or malformed values are dropped. */
export function filtersFromParams(p: URLSearchParams): ReportFilters {
  const f: ReportFilters = {}
  const from = p.get('from')
  const to = p.get('to')
  if (from && isIso(from)) f.from = new Date(from).toISOString()
  if (to && isIso(to)) f.to = new Date(to).toISOString()
  const scamType = p.get('scamType')
  if (scamType && (SCAM_TYPES as readonly string[]).includes(scamType)) f.scamType = scamType as ReportFilters['scamType']
  const band = p.get('band')
  if (band && (BANDS as readonly string[]).includes(band)) f.band = band as ReportFilters['band']
  const status = p.get('status')
  if (status && (REPORT_STATUSES as readonly string[]).includes(status)) f.status = status as ReportFilters['status']
  const via = p.get('detectedVia')
  if (via && (DETECTED_VIA as readonly string[]).includes(via)) f.detectedVia = via as ReportFilters['detectedVia']
  const circle = p.get('circle')
  if (circle) f.circle = circle.slice(0, 64)
  const number = p.get('number')
  if (number && digitsOf(number)) f.number = digitsOf(number).slice(0, 15)
  const prefix = p.get('prefix')
  if (prefix && /^\d{5}$/.test(prefix)) f.prefix = prefix
  return f
}

export function filtersToParams(f: ReportFilters): URLSearchParams {
  const p = new URLSearchParams()
  for (const k of KEYS) {
    const v = f[k]
    if (v) p.set(k, String(v))
  }
  return p
}
