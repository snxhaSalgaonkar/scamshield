import { loadSession, saveSession } from './session'
import type {
  Alert,
  AlertEvent,
  AuditEntry,
  DataSource,
  FusionConfig,
  NumberRecord,
  Officer,
  Session,
  Stats,
  Trends,
} from './types'

// FastAPI backend (architecture doc, section 6). The backend speaks snake_case;
// everything is mapped to the camelCase types here so the UI never sees the wire format.

const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8000').replace(/\/$/, '')
const WS_URL = (import.meta.env.VITE_WS_URL ?? API_URL.replace(/^http/, 'ws')).replace(/\/$/, '')

class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = loadSession()?.token
  const res = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })
  if (res.status === 401) {
    // Token expired or revoked: drop the session and send the officer back to login.
    saveSession(null)
    window.location.assign('/login?error=expired')
    throw new ApiError(401, 'Session expired')
  }
  if (!res.ok) {
    let detail = res.statusText
    try {
      detail = (await res.json()).detail ?? detail
    } catch {
      // non-JSON error body
    }
    throw new ApiError(res.status, typeof detail === 'string' ? detail : JSON.stringify(detail))
  }
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T)
}

// --- wire types + mappers -------------------------------------------------

/* eslint-disable @typescript-eslint/no-explicit-any */
type Wire = Record<string, any>

const toEvent = (e: Wire): AlertEvent => ({ type: e.type, ts: e.ts, ...(e.amount != null ? { amount: e.amount } : {}) })

const toAlert = (a: Wire): Alert => ({
  id: String(a.id),
  callEventId: String(a.call_event_id),
  callerPrefix: a.caller_prefix,
  callerHash: a.caller_hash,
  deviceId: a.device_id,
  region: a.region ?? a.region_prefix ?? 'Unknown',
  score: a.score,
  l1: a.l1 ?? null,
  l2: a.l2,
  l3: a.l3,
  scamType: a.scam_type,
  reasons: Array.isArray(a.reasons) ? a.reasons : [],
  events: (a.events ?? []).map(toEvent),
  callStart: a.call_start ?? a.created_at,
  callEnd: a.call_end ?? a.created_at,
  complaintSubmitted: Boolean(a.complaint_submitted),
  status: a.status,
  createdAt: a.created_at,
})

const toNumber = (n: Wire): NumberRecord => ({
  callerHash: n.number_hash ?? n.caller_hash,
  callerPrefix: n.caller_prefix,
  numberType: n.number_type ?? 'mobile',
  firstSeen: n.first_seen,
  reportCount: n.report_count,
  callsPerHour: n.calls_per_hour,
  status: n.status,
  lastActivity: n.last_activity ?? n.first_seen,
  expiresAt: n.expires_at,
})

const toOfficer = (o: Wire): Officer => ({
  id: String(o.id),
  name: o.name,
  email: o.email,
  role: o.role,
  lastLogin: o.last_login ?? undefined,
})

const toStats = (s: Wire): Stats => ({
  totalAlerts: s.total_alerts,
  redFlags: s.red_flags,
  warnings: s.warnings,
  low: s.low,
  awaitingReview: s.awaiting_review,
  oldestAwaitingReview: s.oldest_awaiting_review ?? null,
  byType: s.by_type ?? {},
  overTime: s.over_time ?? [],
})

const toTrends = (t: Wire): Trends => ({
  days: t.days,
  totalAlerts: t.total_alerts,
  avgScore: t.avg_score,
  redFlagShare: t.red_flag_share,
  falsePositiveRate: t.false_positive_rate,
  byDay: t.by_day ?? [],
  byRegion: t.by_region ?? {},
  byHourOfDay: t.by_hour_of_day ?? Array(24).fill(0),
  scoreHistogram: t.score_histogram ?? Array(10).fill(0),
  typeByRegion: t.type_by_region ?? {},
})

const toConfig = (c: Wire): FusionConfig => ({
  thresholds: c.thresholds,
  weights: c.weights,
  eventPoints: c.event_points ?? {},
  unknownCallerPoints: c.unknown_caller_points,
  comboRules: c.combo_rules ?? [],
  postCallWindowMin: c.post_call_window_min,
})

const toAudit = (e: Wire): AuditEntry => ({
  id: String(e.id),
  officerName: e.officer_name ?? String(e.officer_id),
  action: e.action,
  target: e.target,
  ts: e.ts,
})

// --- live feed over WebSocket --------------------------------------------

function subscribeToAlerts(onInsert: (a: Alert) => void): () => void {
  let socket: WebSocket | null = null
  let closed = false
  let retry = 0
  let retryTimer: number | undefined

  const connect = () => {
    const token = loadSession()?.token ?? ''
    socket = new WebSocket(`${WS_URL}/police/ws?token=${encodeURIComponent(token)}`)
    socket.onopen = () => {
      retry = 0
    }
    socket.onmessage = (msg) => {
      try {
        const data = JSON.parse(msg.data)
        // Accept either {type: "alert", alert: {...}} or a bare alert object.
        const raw = data?.type === 'alert' ? (data.alert ?? data.data) : data
        if (raw?.id) onInsert(toAlert(raw))
      } catch {
        // ignore keep-alives / malformed frames
      }
    }
    socket.onclose = () => {
      if (closed) return
      // Reconnect with capped exponential backoff.
      retry += 1
      retryTimer = window.setTimeout(connect, Math.min(30_000, 1000 * 2 ** retry))
    }
  }

  connect()
  return () => {
    closed = true
    window.clearTimeout(retryTimer)
    socket?.close()
  }
}

// --- DataSource -----------------------------------------------------------

export const apiDataSource: DataSource = {
  async login(email, password) {
    const res = await request<Wire>('/police/login', { method: 'POST', body: JSON.stringify({ email, password }) })
    const session: Session = { token: res.access_token ?? res.token, officer: toOfficer(res.officer) }
    return session
  },

  async getAlerts(filter) {
    const qs = filter?.callerHash ? `?caller_hash=${encodeURIComponent(filter.callerHash)}` : ''
    return (await request<Wire[]>(`/police/alerts${qs}`)).map(toAlert)
  },

  async getAlert(id) {
    try {
      return toAlert(await request<Wire>(`/police/alerts/${encodeURIComponent(id)}`))
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null
      throw e
    }
  },

  subscribeToAlerts,

  async getFlaggedNumbers() {
    return (await request<Wire[]>('/police/numbers')).map(toNumber)
  },

  async getNumber(callerHash) {
    try {
      return toNumber(await request<Wire>(`/police/numbers/${encodeURIComponent(callerHash)}`))
    } catch (e) {
      if (e instanceof ApiError && e.status === 404) return null
      throw e
    }
  },

  async updateAlertStatus(id, status, note) {
    await request(`/police/alerts/${encodeURIComponent(id)}/feedback`, {
      method: 'POST',
      body: JSON.stringify({ status, note: note ?? null }),
    })
  },

  async getStats() {
    return toStats(await request<Wire>('/police/stats?hours=12'))
  },

  async getTrends(days) {
    return toTrends(await request<Wire>(`/police/stats/trends?days=${days}`))
  },

  async getOfficers() {
    return (await request<Wire[]>('/admin/officers')).map(toOfficer)
  },

  async getConfig() {
    return toConfig(await request<Wire>('/admin/config'))
  },

  async getAuditLog() {
    return (await request<Wire[]>('/admin/audit')).map(toAudit)
  },
}
