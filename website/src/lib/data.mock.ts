// In-memory DataSource for demos with zero backend. Seeded (stable across reloads) with the same
// story as supabase/004_seed.sql — flagged numbers, two gang clusters, Scenario B pre-reported by
// two devices, false positives — plus background traffic, and a new report every ~20 s.
import { aggregateNumber, aggregateNumbers, computeStats, DAY, HOUR } from './analytics'
import { reportsToCsv } from './csv'
import { matchesFilters } from './filters'
import { circleOf, NUMBER_SERIES, prefixOf } from './numberSeries'
import { buildScenario, DEMO_NUMBERS, makeReport, numberInSeries, randomScamType, seededRng, uuid, type Rng, type SimScenarioId } from './scenarios'
import type { DataSource, NumberRecord, RealtimeStatus, Report, ReportInsert, ReportStatus, ScamType } from './types'

const LIVE_EVERY_MS = 20_000

function toReport(ins: ReportInsert, rng?: Rng): Report {
  return {
    ...ins,
    id: uuid(rng),
    number_prefix: ins.number_prefix || prefixOf(ins.caller_number),
    telecom_circle: ins.telecom_circle || circleOf(ins.caller_number),
    status: ins.status ?? 'new',
    user_feedback: ins.user_feedback ?? null,
    created_at: ins.created_at ?? new Date().toISOString(),
  }
}

const SERIES_FOR_BACKGROUND = NUMBER_SERIES.map((s) => s.prefix)

function seed(now: number): Report[] {
  const rng = seededRng(1930)
  const devices = Array.from({ length: 40 }, () => uuid(rng))
  const out: Report[] = []
  const add = (type: ScamType, number: string, hoursAgo: number, extra: Partial<Report> = {}, device?: string) =>
    out.push({
      ...toReport(makeReport(type, rng, { number, deviceId: device ?? devices[Math.floor(rng() * devices.length)], createdAt: new Date(now - hoursAgo * HOUR) }), rng),
      ...extra,
    })

  // Flagged digital-arrest numbers: 4 and 3 distinct devices.
  ;[150, 120, 70, 30].forEach((h, i) => add('digital_arrest', '+918448312276', h, { status: i === 0 ? 'escalated' : i === 1 ? 'reviewed' : 'new' }, devices[i]))
  ;[18, 9, 4].forEach((h, i) => add('digital_arrest', '+919423556677', h, {}, devices[10 + i]))

  // Cluster 70421-XXXXX (digital arrest, 3 numbers, 5 reports).
  ;[
    ['+917042110023', 140],
    ['+917042110023', 100],
    ['+917042136781', 60],
    ['+917042158890', 20],
    ['+917042136781', 5],
  ].forEach(([n, h], i) => add('digital_arrest', n as string, h as number, {}, devices[4 + i]))

  // Cluster 98112-XXXXX (wrong-number OTP gang, 4 numbers, 7 reports); Scenario B number has 2 devices.
  ;[
    [DEMO_NUMBERS.scenarioB, 50, 'wrong_number_distraction'],
    [DEMO_NUMBERS.scenarioB, 26, 'wrong_number_distraction'],
    ['+919811200417', 90, 'otp_theft'],
    ['+919811200417', 44, 'otp_theft'],
    ['+919811231908', 12, 'otp_theft'],
    ['+919811276642', 8, 'wrong_number_distraction'],
    ['+919811276642', 3, 'otp_theft'],
  ].forEach(([n, h, t], i) => add(t as ScamType, n as string, h as number, {}, devices[14 + i]))

  // KYC number reported twice (simulator's "reputation" scenario adds a 3rd).
  ;[130, 80].forEach((h, i) => add('kyc_bank_impersonation', DEMO_NUMBERS.kyc, h, {}, devices[22 + i]))

  // Officer-confirmed false positives and citizen "this was safe" (delivery OTP).
  add('other', '+919000112233', 145, { status: 'false_positive', risk_score: 40, action_taken: 'warn' })
  add('kyc_bank_impersonation', '+917890123456', 95, { status: 'false_positive' })
  add('otp_theft', '+919845000111', 118, { status: 'false_positive' })
  ;[66, 34].forEach((h, i) =>
    add('otp_theft', '+919167001234', h, {
      user_feedback: 'safe',
      risk_score: 40,
      l3_score: 85,
      action_taken: 'warn',
      reasons: ["OTP SMS during call ('share with delivery agent')"],
    }, devices[30 + i]),
  )

  // Background traffic: ~95 reports over 7 days, busier in the daytime.
  for (let i = 0; i < 95; i++) {
    let hoursAgo = rng() * 7 * 24
    const hourOfDay = new Date(now - hoursAgo * HOUR).getHours()
    if (hourOfDay < 8 && rng() < 0.6) hoursAgo += 10 // push most night-time calls into the day
    const series = SERIES_FOR_BACKGROUND[Math.floor(rng() * SERIES_FOR_BACKGROUND.length)]
    add(randomScamType(rng), numberInSeries(rng, series), Math.min(hoursAgo, 7 * 24 - 1))
  }

  // A few reviewed / escalated among older background rows.
  for (const r of out) {
    if (r.status !== 'new' || r.user_feedback) continue
    const age = now - new Date(r.created_at).getTime()
    if (age > 2 * DAY && rng() < 0.45) r.status = rng() < 0.2 ? 'escalated' : 'reviewed'
  }
  return out.sort((a, b) => b.created_at.localeCompare(a.created_at))
}

class MockStore {
  reports: Report[] = []
  private reportListeners = new Set<(r: Report, type: 'INSERT' | 'UPDATE') => void>()
  private numberListeners = new Set<(n: NumberRecord) => void>()
  private timer: ReturnType<typeof setInterval> | null = null
  private liveRng = seededRng(Date.now() % 100_000)

  constructor() {
    this.reports = seed(Date.now())
  }

  insert(ins: ReportInsert[]) {
    const fresh = ins.map((i) => toReport(i))
    this.reports = [...fresh, ...this.reports].sort((a, b) => b.created_at.localeCompare(a.created_at))
    for (const r of fresh) {
      this.reportListeners.forEach((l) => l(r, 'INSERT'))
      this.emitNumber(r.caller_number)
    }
  }

  update(id: string, patch: Partial<Report>) {
    const i = this.reports.findIndex((r) => r.id === id)
    if (i < 0) throw new Error('Report not found')
    const next = { ...this.reports[i], ...patch }
    this.reports = this.reports.map((r) => (r.id === id ? next : r))
    this.reportListeners.forEach((l) => l(next, 'UPDATE'))
  }

  numberOf(number: string): NumberRecord | null {
    const rows = this.reports.filter((r) => r.caller_number === number)
    return rows.length ? aggregateNumber(number, rows) : null
  }

  private emitNumber(number: string) {
    const n = this.numberOf(number)
    if (n) this.numberListeners.forEach((l) => l(n))
  }

  onReport(l: (r: Report, type: 'INSERT' | 'UPDATE') => void) {
    this.reportListeners.add(l)
    this.ensureTimer()
    return () => {
      this.reportListeners.delete(l)
      this.stopTimerIfIdle()
    }
  }

  onNumber(l: (n: NumberRecord) => void) {
    this.numberListeners.add(l)
    return () => this.numberListeners.delete(l)
  }

  /** One random live report; sometimes reuses an existing number so reputation visibly grows. */
  private tick = () => {
    const rng = this.liveRng
    const reuse = rng() < 0.25 ? this.reports[Math.floor(rng() * Math.min(40, this.reports.length))] : null
    const type = reuse?.scam_type ?? randomScamType(rng)
    const number = reuse?.caller_number ?? numberInSeries(rng, SERIES_FOR_BACKGROUND[Math.floor(rng() * SERIES_FOR_BACKGROUND.length)])
    this.insert([makeReport(type, rng, { number })])
  }

  private ensureTimer() {
    if (this.timer || typeof window === 'undefined') return
    this.timer = setInterval(this.tick, LIVE_EVERY_MS)
  }

  private stopTimerIfIdle() {
    if (this.reportListeners.size === 0 && this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  }
}

let store: MockStore | null = null
const db = () => (store ??= new MockStore())

const wait = <T,>(v: T, ms = 120) => new Promise<T>((res) => setTimeout(() => res(v), ms))

export const mockDataSource: DataSource = {
  async getReports(filters = {}) {
    const rows = db().reports.filter((r) => matchesFilters(r, filters))
    return wait(filters.limit ? rows.slice(0, filters.limit) : rows)
  },
  async getReportsPage(filters, page, pageSize) {
    const rows = db().reports.filter((r) => matchesFilters(r, filters))
    return wait({ rows: rows.slice(page * pageSize, (page + 1) * pageSize), total: rows.length })
  },
  async getReport(id) {
    return wait(db().reports.find((r) => r.id === id) ?? null)
  },
  async getReportsByNumber(number) {
    return wait(db().reports.filter((r) => r.caller_number === number))
  },
  async getReportsByPrefix(prefix) {
    return wait(db().reports.filter((r) => r.number_prefix === prefix))
  },
  async updateReportStatus(id: string, status: ReportStatus) {
    db().update(id, { status })
    return wait(undefined)
  },
  async getNumbers(opts = {}) {
    const all = aggregateNumbers(db().reports)
    return wait(opts.flaggedOnly ? all.filter((n) => n.is_flagged) : all)
  },
  async getNumber(number) {
    return wait(db().numberOf(number))
  },
  async getNumberSeries() {
    return wait(NUMBER_SERIES)
  },
  async getStats(range) {
    return wait(computeStats(db().reports, range))
  },
  async exportCsv(filters) {
    const rows = db().reports.filter((r) => matchesFilters(r, filters))
    return wait(new Blob([reportsToCsv(rows)], { type: 'text/csv;charset=utf-8' }))
  },
  subscribeToReports(onChange, onStatus?: (s: RealtimeStatus) => void) {
    const off = db().onReport(onChange)
    const t = setTimeout(() => onStatus?.('live'), 300)
    return () => {
      clearTimeout(t)
      off()
    }
  },
  subscribeToNumbers(onChange) {
    return db().onNumber(onChange)
  },
}

/** Mock-mode simulator: same scenarios as /api/simulate, inserted locally. */
export function simulateLocally(id: SimScenarioId): number {
  const rows = buildScenario(id)
  db().insert(rows)
  return rows.length
}
