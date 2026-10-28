import { riskLevel, statusLabel } from './risk'
import { DEFAULT_CONFIG, eventLabel, firedCombos } from './rules'
import { loadSession } from './session'
import type {
  Alert,
  AlertEvent,
  AlertEventType,
  AlertStatus,
  AuditEntry,
  DataSource,
  NumberRecord,
  NumberType,
  Officer,
  ScamType,
  Stats,
  TimeBucket,
  Trends,
} from './types'

const MIN = 60_000
const HOUR = 60 * MIN
const DAY = 24 * HOUR
const LIVE_INTERVAL_MS = 20_000

// Seeded PRNG so the demo looks the same on every reload.
function mulberry32(seed: number) {
  return () => {
    seed |= 0
    seed = (seed + 0x6d2b79f5) | 0
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}
const random = mulberry32(1930)
const rand = (lo: number, hi: number) => Math.round(lo + random() * (hi - lo))
const pick = <T,>(xs: T[]): T => xs[Math.floor(random() * xs.length)]
const clamp = (n: number) => Math.max(0, Math.min(100, Math.round(n)))

// --- callers (one per `numbers` row) -------------------------------------

interface Caller {
  hash: string
  prefix: string
  type: NumberType
  reports: number
  callsPerHour: number
  firstSeenHoursAgo: number
  status: NumberRecord['status']
}

const CALLERS: Caller[] = [
  { hash: 'a3f9c1d2', prefix: '+91 98765 XXXXX', type: 'mobile', reports: 47, callsPerHour: 18.5, firstSeenHoursAgo: 160, status: 'flagged' },
  { hash: '2d6f77b8', prefix: '+92 300 XXXXXXX', type: 'international', reports: 31, callsPerHour: 12, firstSeenHoursAgo: 150, status: 'flagged' },
  { hash: 'c91d8a30', prefix: '+91 88990 XXXXX', type: 'mobile', reports: 22, callsPerHour: 9.5, firstSeenHoursAgo: 140, status: 'flagged' },
  { hash: '19bd5c04', prefix: '+44 20 XXXX XXXX', type: 'voip', reports: 14, callsPerHour: 6, firstSeenHoursAgo: 120, status: 'flagged' },
  { hash: '7be04e5a', prefix: '+91 70123 XXXXX', type: 'mobile', reports: 9, callsPerHour: 4.5, firstSeenHoursAgo: 96, status: 'flagged' },
  { hash: '4c0a93fe', prefix: '+91 93210 XXXXX', type: 'mobile', reports: 6, callsPerHour: 3, firstSeenHoursAgo: 70, status: 'watch' },
  { hash: 'e5a1b946', prefix: '+1 202 XXX XXXX', type: 'voip', reports: 4, callsPerHour: 2, firstSeenHoursAgo: 40, status: 'watch' },
  { hash: '8f2e6d17', prefix: '+91 62840 XXXXX', type: 'mobile', reports: 2, callsPerHour: 1, firstSeenHoursAgo: 165, status: 'expired' },
]

// Weighted so the hotspot view shows a pattern.
const REGIONS = [
  'Mumbai', 'Mumbai', 'Mumbai', 'Delhi NCR', 'Delhi NCR', 'Delhi NCR', 'Bengaluru', 'Bengaluru',
  'Pune', 'Hyderabad', 'Kolkata', 'Chennai', 'Nagpur',
]

const DEBIT_AMOUNTS = [9_999, 24_999, 49_999, 75_000, 1_20_000, 2_50_000]

// --- scam templates -------------------------------------------------------

interface Template {
  type: ScamType
  /** [event, fraction of call duration, extra seconds after that point] */
  events: [AlertEventType, number, number][]
  durationSec: [number, number]
  voiceLayerChance: number
  callers: number[]
}

const TEMPLATES: Record<ScamType, Template> = {
  'Digital Arrest': {
    type: 'Digital Arrest',
    events: [
      ['call_start', 0, 0],
      ['remote_app', 0.3, 0],
      ['merge_seen', 0.5, 0],
      ['call_end', 1, 0],
      // Debit arrives after the scammer hangs up, inside the post-call window.
      ['debit_sms', 1, 180],
    ],
    durationSec: [420, 1500],
    voiceLayerChance: 0.5,
    callers: [0, 1, 3],
  },
  'OTP Theft': {
    type: 'OTP Theft',
    events: [
      ['call_start', 0, 0],
      ['otp_received', 0.55, 0],
      ['debit_sms', 0.8, 0],
      ['call_end', 1, 0],
    ],
    durationSec: [150, 420],
    voiceLayerChance: 0.3,
    callers: [0, 2, 4, 6],
  },
  'KYC Scam': {
    type: 'KYC Scam',
    events: [
      ['call_start', 0, 0],
      ['forwarding_on', 0.4, 0],
      ['call_end', 1, 0],
      ['new_apk', 1, 240],
    ],
    durationSec: [180, 600],
    voiceLayerChance: 0.4,
    callers: [2, 4, 5, 6],
  },
  'False Positive': {
    type: 'False Positive',
    events: [
      ['call_start', 0, 0],
      ['call_end', 1, 0],
    ],
    durationSec: [20, 120],
    voiceLayerChance: 0.2,
    callers: [5, 6, 7],
  },
}

// --- fusion (mirrors the backend rules in the architecture doc) -----------

function reputationScore(c: Caller): number {
  return clamp(c.reports * 1.5 + c.callsPerHour * 2 + (c.type === 'mobile' ? 0 : 10))
}

let idCounter = 0

function makeAlert(template: Template, callerIndex: number, createdAt: number, status: AlertStatus): Alert {
  idCounter += 1
  const id = `alr_${String(idCounter).padStart(4, '0')}`
  const caller = CALLERS[callerIndex]
  const durationSec = rand(...template.durationSec)
  const callStart = createdAt - durationSec * 1000 - 5 * MIN

  const events: AlertEvent[] = template.events.map(([type, f, extra]) => ({
    type,
    ts: new Date(callStart + (f * durationSec + extra) * 1000).toISOString(),
    ...(type === 'debit_sms' ? { amount: pick(DEBIT_AMOUNTS) } : {}),
  }))

  const cfg = DEFAULT_CONFIG
  const longCall = durationSec >= 300
  const eventPoints = events.reduce((sum, e) => sum + (cfg.eventPoints[e.type] ?? 0), 0)
  const l3 = clamp(cfg.unknownCallerPoints + eventPoints + (longCall ? 10 : 0))
  const l2 = reputationScore(caller)
  const l1 = random() < template.voiceLayerChance ? (template.type === 'False Positive' ? rand(5, 25) : rand(55, 95)) : null

  let score = l1 === null ? clamp(l2 * 0.35 + l3 * 0.65) : clamp(l1 * cfg.weights.l1 + l2 * cfg.weights.l2 + l3 * cfg.weights.l3)
  const combos = firedCombos(events)
  if (combos.length) score = Math.max(score, rand(80, 96))

  const reasons = ['Caller not in contacts']
  for (const e of events) {
    if (cfg.eventPoints[e.type]) reasons.push(eventLabel[e.type])
  }
  if (longCall) reasons.push(`Long call (${Math.round(durationSec / 60)} min)`)
  if (caller.reports >= 10) reasons.push(`Number reported ${caller.reports} times`)
  if (caller.type !== 'mobile') reasons.push(caller.type === 'voip' ? 'VoIP number' : 'International number')
  if (!longCall && template.type === 'False Positive') reasons.push('Short call, no correlated phone events')
  combos.forEach((c) => reasons.push(`Combo rule: ${c}`))

  const callEnd = callStart + durationSec * 1000
  return {
    id,
    callEventId: `cal_${id.slice(4)}`,
    callerPrefix: caller.prefix,
    callerHash: caller.hash,
    deviceId: `dev_${Math.floor(random() * 0xffffffff).toString(16).padStart(8, '0')}`,
    region: pick(REGIONS),
    score,
    l1,
    l2,
    l3,
    scamType: template.type,
    reasons,
    events,
    callStart: new Date(callStart).toISOString(),
    callEnd: new Date(callEnd).toISOString(),
    complaintSubmitted: template.type !== 'False Positive' && random() < 0.35,
    status,
    createdAt: new Date(createdAt).toISOString(),
  }
}

function alertFor(type: ScamType, createdAt: number, status: AlertStatus, callerIndex?: number) {
  const t = TEMPLATES[type]
  return makeAlert(t, callerIndex ?? pick(t.callers), createdAt, status)
}

// --- seed data ------------------------------------------------------------

function seedAlerts(): Alert[] {
  const now = Date.now()
  // ~15 alerts in the last 12 hours: what the live feed opens on.
  const recent: [number, ScamType, AlertStatus][] = [
    [4, 'Digital Arrest', 'new'],
    [11, 'OTP Theft', 'new'],
    [26, 'KYC Scam', 'new'],
    [41, 'Digital Arrest', 'escalated'],
    [58, 'False Positive', 'false_positive'],
    [83, 'OTP Theft', 'reviewed'],
    [112, 'Digital Arrest', 'escalated'],
    [150, 'KYC Scam', 'reviewed'],
    [196, 'OTP Theft', 'new'],
    [247, 'False Positive', 'false_positive'],
    [301, 'Digital Arrest', 'reviewed'],
    [365, 'KYC Scam', 'reviewed'],
    [440, 'OTP Theft', 'escalated'],
    [530, 'Digital Arrest', 'reviewed'],
    [655, 'KYC Scam', 'reviewed'],
  ]
  const rows = recent.map(([ago, type, status]) => alertFor(type, now - ago * MIN, status))

  // ~110 older, already-handled alerts over the previous week, for the Trends page.
  const types: ScamType[] = ['Digital Arrest', 'OTP Theft', 'OTP Theft', 'KYC Scam', 'KYC Scam', 'False Positive']
  const busyHours = [10, 11, 11, 12, 13, 15, 16, 17, 18, 18, 19, 20, 21, 9, 14, 22, 23, 2]
  for (let i = 0; i < 110; i++) {
    const day = rand(1, 6)
    const date = new Date(now - day * DAY)
    date.setHours(pick(busyHours), rand(0, 59), 0, 0)
    const type = pick(types)
    const status: AlertStatus =
      type === 'False Positive' ? 'false_positive' : random() < 0.2 ? 'escalated' : random() < 0.1 ? 'false_positive' : 'reviewed'
    rows.push(alertFor(type, date.getTime(), status))
  }
  return rows.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

function seedNumbers(alertRows: Alert[]): NumberRecord[] {
  const now = Date.now()
  return CALLERS.map((c) => {
    const latest = alertRows.find((a) => a.callerHash === c.hash)
    return {
      callerHash: c.hash,
      callerPrefix: c.prefix,
      numberType: c.type,
      reportCount: c.reports,
      callsPerHour: c.callsPerHour,
      firstSeen: new Date(now - c.firstSeenHoursAgo * HOUR).toISOString(),
      lastActivity: latest?.createdAt ?? new Date(now - c.firstSeenHoursAgo * HOUR).toISOString(),
      expiresAt: new Date(now + (c.status === 'expired' ? -HOUR : 14 * DAY)).toISOString(),
      status: c.status,
    }
  })
}

const OFFICERS: Officer[] = [
  { id: 'off_01', name: 'Insp. A. Kulkarni', email: 'a.kulkarni@cyber.police.gov.in', role: 'admin' },
  { id: 'off_02', name: 'SI R. Sharma', email: 'r.sharma@cyber.police.gov.in', role: 'officer' },
  { id: 'off_03', name: 'SI P. Iyer', email: 'p.iyer@cyber.police.gov.in', role: 'officer' },
]

// --- in-memory store ------------------------------------------------------

let alerts: Alert[] = seedAlerts()
const numbers: NumberRecord[] = seedNumbers(alerts)
const audit: AuditEntry[] = [
  { id: 'aud_0003', officerName: 'SI R. Sharma', action: 'Set status: Escalated', target: alerts[3].id, ts: new Date(Date.now() - 35 * MIN).toISOString() },
  { id: 'aud_0002', officerName: 'SI R. Sharma', action: 'Viewed case', target: alerts[3].id, ts: new Date(Date.now() - 38 * MIN).toISOString() },
  { id: 'aud_0001', officerName: 'Insp. A. Kulkarni', action: 'Signed in', target: '-', ts: new Date(Date.now() - 3 * HOUR).toISOString() },
]
const listeners = new Set<(a: Alert) => void>()
let timer: ReturnType<typeof setInterval> | undefined

function logAudit(action: string, target: string) {
  const officerName = loadSession()?.officer.name ?? 'Unknown officer'
  audit.unshift({ id: `aud_${String(audit.length + 1).padStart(4, '0')}`, officerName, action, target, ts: new Date().toISOString() })
}

function pushAlert(type: ScamType) {
  const alert = alertFor(type, Date.now(), 'new')
  // Freshly raised alert: the call just ended, so shift its timeline to "now".
  alerts = [alert, ...alerts]
  const record = numbers.find((n) => n.callerHash === alert.callerHash)
  if (record) {
    record.reportCount += 1
    record.lastActivity = alert.createdAt
  }
  listeners.forEach((fn) => fn(alert))
}

function emitFakeAlert() {
  // Bias towards scams so the live demo is interesting, with the odd false positive.
  const r = random()
  pushAlert(r < 0.12 ? 'False Positive' : r < 0.45 ? 'Digital Arrest' : r < 0.75 ? 'OTP Theft' : 'KYC Scam')
}

// --- aggregations ---------------------------------------------------------

function hourlyBuckets(count: number): TimeBucket[] {
  const hourStart = Math.floor(Date.now() / HOUR) * HOUR
  return Array.from({ length: count }, (_, i) => ({ ts: new Date(hourStart - (count - 1 - i) * HOUR).toISOString(), count: 0 }))
}

function buildStats(): Stats {
  const since = Date.now() - 12 * HOUR
  const recent = alerts.filter((a) => new Date(a.createdAt).getTime() >= since)
  const overTime = hourlyBuckets(12)
  const byType: Record<string, number> = {}
  const counts = { red: 0, warning: 0, silent: 0 }
  let awaiting: Alert[] = []

  for (const a of recent) {
    byType[a.scamType] = (byType[a.scamType] ?? 0) + 1
    counts[riskLevel(a.score)] += 1
    if (a.status === 'new') awaiting.push(a)
    const t = new Date(a.createdAt).getTime()
    const bucket = overTime.find((b) => t >= new Date(b.ts).getTime() && t < new Date(b.ts).getTime() + HOUR)
    if (bucket) bucket.count += 1
  }
  awaiting = awaiting.sort((a, b) => a.createdAt.localeCompare(b.createdAt))

  return {
    totalAlerts: recent.length,
    redFlags: counts.red,
    warnings: counts.warning,
    low: counts.silent,
    awaitingReview: awaiting.length,
    oldestAwaitingReview: awaiting[0]?.createdAt ?? null,
    byType,
    overTime,
  }
}

function buildTrends(days: number): Trends {
  const since = Date.now() - days * DAY
  const rows = alerts.filter((a) => new Date(a.createdAt).getTime() >= since)
  const dayStart = new Date()
  dayStart.setHours(0, 0, 0, 0)
  const byDay: TimeBucket[] = Array.from({ length: days }, (_, i) => ({
    ts: new Date(dayStart.getTime() - (days - 1 - i) * DAY).toISOString(),
    count: 0,
  }))
  const byRegion: Record<string, number> = {}
  const byHourOfDay = Array<number>(24).fill(0)
  const scoreHistogram = Array<number>(10).fill(0)
  const typeByRegion: Record<string, Record<string, number>> = {}
  let scoreSum = 0
  let red = 0
  let fp = 0

  for (const a of rows) {
    const d = new Date(a.createdAt)
    const bucket = byDay.find((b) => d.getTime() >= new Date(b.ts).getTime() && d.getTime() < new Date(b.ts).getTime() + DAY)
    if (bucket) bucket.count += 1
    byRegion[a.region] = (byRegion[a.region] ?? 0) + 1
    byHourOfDay[d.getHours()] += 1
    scoreHistogram[Math.min(9, Math.floor(a.score / 10))] += 1
    typeByRegion[a.region] ??= {}
    typeByRegion[a.region][a.scamType] = (typeByRegion[a.region][a.scamType] ?? 0) + 1
    scoreSum += a.score
    if (riskLevel(a.score) === 'red') red += 1
    if (a.status === 'false_positive') fp += 1
  }

  const n = rows.length || 1
  return {
    days,
    totalAlerts: rows.length,
    avgScore: Math.round(scoreSum / n),
    redFlagShare: red / n,
    falsePositiveRate: fp / n,
    byDay,
    byRegion,
    byHourOfDay,
    scoreHistogram,
    typeByRegion,
  }
}

// --- DataSource -----------------------------------------------------------

const latency = <T,>(value: T, ms = 150): Promise<T> => new Promise((resolve) => setTimeout(() => resolve(value), ms))

function titleCase(s: string) {
  return s.replace(/[._-]+/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase())
}

export const mockDataSource: DataSource = {
  async login(email, password) {
    if (!email || !password) throw new Error('Enter your email and password.')
    // Demo stub: any credentials work; an email containing "admin" gets the admin role.
    const role = email.toLowerCase().includes('admin') ? 'admin' : 'officer'
    const officer: Officer = {
      id: 'off_demo',
      name: titleCase(email.split('@')[0]),
      email,
      role,
      lastLogin: new Date().toISOString(),
    }
    await latency(null, 300)
    audit.unshift({ id: `aud_${String(audit.length + 1).padStart(4, '0')}`, officerName: officer.name, action: 'Signed in', target: '-', ts: new Date().toISOString() })
    return { token: 'mock-token', officer }
  },

  getAlerts(filter) {
    const rows = filter?.callerHash ? alerts.filter((a) => a.callerHash === filter.callerHash) : alerts
    return latency([...rows])
  },

  getAlert(id) {
    const alert = alerts.find((a) => a.id === id) ?? null
    if (alert) logAudit('Viewed case', id)
    return latency(alert)
  },

  subscribeToAlerts(onInsert) {
    listeners.add(onInsert)
    // Only run the fake feed while someone is listening (also keeps React strict-mode remounts tidy).
    if (!timer) timer = setInterval(emitFakeAlert, LIVE_INTERVAL_MS)
    return () => {
      listeners.delete(onInsert)
      if (listeners.size === 0 && timer) {
        clearInterval(timer)
        timer = undefined
      }
    }
  },

  getFlaggedNumbers() {
    return latency([...numbers].sort((a, b) => b.reportCount - a.reportCount))
  },

  getNumber(callerHash) {
    return latency(numbers.find((n) => n.callerHash === callerHash) ?? null)
  },

  async updateAlertStatus(id, status, note) {
    alerts = alerts.map((a) => (a.id === id ? { ...a, status } : a))
    logAudit(`Set status: ${statusLabel[status]}${note ? ` (note: ${note})` : ''}`, id)
    await latency(undefined, 100)
  },

  getStats() {
    return latency(buildStats())
  },

  getTrends(days) {
    return latency(buildTrends(days))
  },

  getOfficers() {
    return latency(OFFICERS)
  },

  getConfig() {
    return latency(DEFAULT_CONFIG)
  },

  getAuditLog() {
    return latency([...audit])
  },

  simulateAlert() {
    pushAlert(random() < 0.5 ? 'Digital Arrest' : 'OTP Theft')
  },
}
