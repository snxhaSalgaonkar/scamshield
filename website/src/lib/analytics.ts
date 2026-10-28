// Pure functions over reports, shared by every page and by the tests (scripts/test-logic.ts).
import { AVG_LOSS_PREVENTED_INR } from './estimate'
import { bandOf } from './risk'
import type {
  Band,
  DetectedVia,
  FalsePositiveRate,
  NumberRecord,
  NumberSeries,
  Report,
  ScamType,
  Stats,
  StatsRange,
  TimeBucket,
} from './types'

export const HOUR = 3_600_000
export const DAY = 24 * HOUR

export const rangeMs: Record<StatsRange, number> = { '24h': DAY, '7d': 7 * DAY }

const ts = (r: Report) => new Date(r.created_at).getTime()

export function inWindow(reports: Report[], fromMs: number, toMs = Infinity): Report[] {
  return reports.filter((r) => {
    const t = ts(r)
    return t >= fromMs && t < toMs
  })
}

function countBy<K extends string>(items: Iterable<K>): Partial<Record<K, number>> {
  const out: Partial<Record<K, number>> = {}
  for (const k of items) out[k] = (out[k] ?? 0) + 1
  return out
}

/**
 * Officer-confirmed false positives + citizen "this was safe" feedback, over every report that
 * was reviewed by an officer or got citizen feedback. A report counted by both is counted once.
 */
export function falsePositiveRate(reports: Report[]): FalsePositiveRate {
  let reviewed = 0
  let falsePositives = 0
  for (const r of reports) {
    if (r.status !== 'new' || r.user_feedback !== null) reviewed++
    if (r.status === 'false_positive' || r.user_feedback === 'safe') falsePositives++
  }
  return { rate: reviewed ? falsePositives / reviewed : null, falsePositives, reviewed }
}

/** Hourly buckets for 24h, daily buckets for 7d, oldest first, ending at the current bucket. */
export function timeBuckets(reports: Report[], range: StatsRange, now = Date.now()): TimeBucket[] {
  const step = range === '24h' ? HOUR : DAY
  const n = range === '24h' ? 24 : 7
  const end = new Date(now)
  if (range === '24h') end.setMinutes(0, 0, 0)
  else end.setHours(0, 0, 0, 0)
  const first = end.getTime() - (n - 1) * step
  const counts = new Array<number>(n).fill(0)
  for (const r of reports) {
    const i = Math.floor((ts(r) - first) / step)
    if (i >= 0 && i < n) counts[i]++
  }
  return counts.map((count, i) => ({ ts: new Date(first + i * step).toISOString(), count }))
}

export function computeStats(reports: Report[], range: StatsRange, now = Date.now()): Stats {
  const rows = inWindow(reports, now - rangeMs[range], now + 60_000)
  const byBand: Record<Band, number> = { safe: 0, warn: 0, high: 0, scam: 0 }
  for (const r of rows) byBand[bandOf(r.risk_score)]++
  const disconnected = rows.filter((r) => r.action_taken === 'disconnected').length
  return {
    range,
    total: rows.length,
    byBand,
    byScamType: countBy<ScamType>(rows.map((r) => r.scam_type)),
    byDetectedVia: countBy<DetectedVia>(rows.flatMap((r) => r.detected_via)),
    disconnected,
    moneyProtectedEstimate: disconnected * AVG_LOSS_PREVENTED_INR,
    falsePositive: falsePositiveRate(rows),
    overTime: timeBuckets(rows, range, now),
    awaitingReview: rows.filter((r) => r.status === 'new').length,
  }
}

// ---------------------------------------------------------------------------
// Spike detection
// ---------------------------------------------------------------------------

export interface Spike {
  /** Stable key, used to remember dismissals. */
  id: string
  scamType: ScamType | null
  circle: string | null
  lastHour: number
  /** Average reports per hour over the 24 h before the last hour. */
  baselinePerHour: number
  /** lastHour / baseline; Infinity when there was no activity before. */
  ratio: number
}

export const SPIKE_MIN_REPORTS = 3
export const SPIKE_MIN_RATIO = 3

/**
 * Spike = last-hour count ≥ 3 and ≥ 3× the average hourly count over the previous 24 h.
 * Checked per scam type × circle, per scam type and per circle. A type-only or circle-only spike is
 * dropped when a more specific type × circle spike already explains it.
 */
export function detectSpikes(reports: Report[], now = Date.now()): Spike[] {
  const lastHour = inWindow(reports, now - HOUR, now + 60_000)
  const prev = inWindow(reports, now - 25 * HOUR, now - HOUR)

  const groupings: { kind: 'tc' | 't' | 'c'; key: (r: Report) => string | null }[] = [
    { kind: 'tc', key: (r) => (r.telecom_circle ? `${r.scam_type}|${r.telecom_circle}` : null) },
    { kind: 't', key: (r) => `${r.scam_type}|` },
    { kind: 'c', key: (r) => (r.telecom_circle ? `|${r.telecom_circle}` : null) },
  ]

  const spikes: Spike[] = []
  for (const { kind, key } of groupings) {
    const recent = new Map<string, number>()
    const before = new Map<string, number>()
    for (const r of lastHour) {
      const k = key(r)
      if (k) recent.set(k, (recent.get(k) ?? 0) + 1)
    }
    for (const r of prev) {
      const k = key(r)
      if (k) before.set(k, (before.get(k) ?? 0) + 1)
    }
    for (const [k, count] of recent) {
      const baselinePerHour = (before.get(k) ?? 0) / 24
      const ratio = baselinePerHour ? count / baselinePerHour : Infinity
      if (count < SPIKE_MIN_REPORTS || ratio < SPIKE_MIN_RATIO) continue
      const [type, circle] = k.split('|')
      const spike: Spike = {
        id: `${kind}:${k}`,
        scamType: (type || null) as ScamType | null,
        circle: circle || null,
        lastHour: count,
        baselinePerHour,
        ratio,
      }
      const explained =
        kind !== 'tc' &&
        spikes.some(
          (s) => s.scamType && s.circle && (kind === 't' ? s.scamType === spike.scamType : s.circle === spike.circle) && s.lastHour * 2 >= count,
        )
      if (!explained) spikes.push(spike)
    }
  }
  return spikes.sort((a, b) => b.lastHour - a.lastHour)
}

// ---------------------------------------------------------------------------
// Number-series clusters
// ---------------------------------------------------------------------------

export interface Cluster {
  prefix: string
  numbers: string[]
  reportCount: number
  deviceCount: number
  topScamType: ScamType
  circle: string | null
  operator: string | null
  firstSeen: string
  lastSeen: string
  maxRisk: number
  /** Reports in the last 24 h vs the average day of the 6 days before. */
  trend: 'up' | 'down' | 'flat'
  last24h: number
}

export const CLUSTER_MIN_NUMBERS = 3
export const CLUSTER_MIN_REPORTS = 5

/** Most frequent value; on a tie, the value that reached the top count first. */
function mode<T>(values: T[]): T | undefined {
  const counts = new Map<T, number>()
  let best: T | undefined
  let bestN = 0
  for (const v of values) {
    const n = (counts.get(v) ?? 0) + 1
    counts.set(v, n)
    if (n > bestN) {
      best = v
      bestN = n
    }
  }
  return best
}

/**
 * Groups the last 7 days of reports by number_prefix (first 5 digits). A prefix is a cluster when
 * ≥ 3 distinct numbers or ≥ 5 reports came from it — scammers rotating through a block of SIMs.
 */
export function groupClusters(reports: Report[], series: NumberSeries[] = [], now = Date.now()): Cluster[] {
  const recent = inWindow(reports, now - 7 * DAY, now + 60_000)
  const byPrefix = new Map<string, Report[]>()
  for (const r of recent) byPrefix.set(r.number_prefix, [...(byPrefix.get(r.number_prefix) ?? []), r])
  const operatorOf = new Map(series.map((s) => [s.prefix, s.operator]))

  const clusters: Cluster[] = []
  for (const [prefix, rows] of byPrefix) {
    const numbers = [...new Set(rows.map((r) => r.caller_number))]
    if (numbers.length < CLUSTER_MIN_NUMBERS && rows.length < CLUSTER_MIN_REPORTS) continue
    const times = rows.map(ts).sort((a, b) => a - b)
    const last24h = rows.filter((r) => ts(r) >= now - DAY).length
    const avgPrevDay = (rows.length - last24h) / 6
    const trend = last24h > avgPrevDay * 1.2 && last24h > 0 ? 'up' : last24h < avgPrevDay * 0.8 ? 'down' : 'flat'
    clusters.push({
      prefix,
      numbers,
      reportCount: rows.length,
      deviceCount: new Set(rows.map((r) => r.device_id)).size,
      topScamType: mode(rows.map((r) => r.scam_type)) ?? 'other',
      circle: mode(rows.map((r) => r.telecom_circle).filter((c): c is string => !!c)) ?? null,
      operator: operatorOf.get(prefix.slice(0, 4)) ?? null,
      firstSeen: new Date(times[0]).toISOString(),
      lastSeen: new Date(times[times.length - 1]).toISOString(),
      maxRisk: Math.max(...rows.map((r) => r.risk_score)),
      trend,
      last24h,
    })
  }
  return clusters.sort((a, b) => b.reportCount - a.reportCount || b.numbers.length - a.numbers.length)
}

// ---------------------------------------------------------------------------
// numbers aggregate (mock mode; mirrors refresh_number() in 003_triggers_rpcs.sql)
// ---------------------------------------------------------------------------

export const FLAG_MIN_REPORTERS = 3

export function aggregateNumber(number: string, rows: Report[]): NumberRecord {
  const sorted = [...rows].sort((a, b) => ts(a) - ts(b))
  const latest = sorted[sorted.length - 1]
  const reporters = new Set(rows.map((r) => r.device_id)).size
  const safe = new Set(rows.filter((r) => r.user_feedback === 'safe').map((r) => r.device_id)).size
  return {
    number,
    number_prefix: latest.number_prefix,
    telecom_circle: [...sorted].reverse().find((r) => r.telecom_circle)?.telecom_circle ?? null,
    first_seen: sorted[0].created_at,
    last_activity: latest.created_at,
    report_count: rows.length,
    reporter_count: reporters,
    safe_count: safe,
    max_risk_score: Math.max(...rows.map((r) => r.risk_score)),
    is_flagged: reporters >= FLAG_MIN_REPORTERS && reporters > safe,
  }
}

export function aggregateNumbers(reports: Report[]): NumberRecord[] {
  const byNumber = new Map<string, Report[]>()
  for (const r of reports) byNumber.set(r.caller_number, [...(byNumber.get(r.caller_number) ?? []), r])
  return [...byNumber.entries()].map(([n, rows]) => aggregateNumber(n, rows))
}

// ---------------------------------------------------------------------------
// Per-circle totals (map page)
// ---------------------------------------------------------------------------

export interface CircleActivity {
  circle: string
  count: number
  topScamType: ScamType
  highRisk: number
}

export function circleActivity(reports: Report[]): CircleActivity[] {
  const byCircle = new Map<string, Report[]>()
  for (const r of reports) {
    if (!r.telecom_circle) continue
    byCircle.set(r.telecom_circle, [...(byCircle.get(r.telecom_circle) ?? []), r])
  }
  return [...byCircle.entries()]
    .map(([circle, rows]) => ({
      circle,
      count: rows.length,
      topScamType: mode(rows.map((r) => r.scam_type)) ?? 'other',
      highRisk: rows.filter((r) => r.risk_score >= 75).length,
    }))
    .sort((a, b) => b.count - a.count)
}
