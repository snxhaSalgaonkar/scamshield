// Mirrors the FastAPI `/police/*` responses (camelCased by data.api.ts).
// Citizen content (SMS text, OTP values, audio) is never part of these types.

export type AlertStatus = 'new' | 'reviewed' | 'escalated' | 'false_positive'

export type ScamType = 'Digital Arrest' | 'OTP Theft' | 'KYC Scam' | 'False Positive'

export type RiskLevel = 'silent' | 'warning' | 'red'

export type AlertEventType =
  | 'call_start'
  | 'otp_received'
  | 'debit_sms'
  | 'forwarding_on'
  | 'merge_seen'
  | 'remote_app'
  | 'new_apk'
  | 'call_end'

export interface AlertEvent {
  type: AlertEventType
  /** ISO timestamp */
  ts: string
  /** Debit amount in INR, extracted on the phone. Only set for debit_sms. */
  amount?: number
}

export interface Alert {
  id: string
  callEventId: string
  /** Display-safe prefix only, e.g. "+91 98765 XXXXX". The full number never reaches police. */
  callerPrefix: string
  /** Short form of caller_hash, used to link alerts to a `numbers` row. */
  callerHash: string
  /** Opaque device identifier, no other personal info. */
  deviceId: string
  region: string
  /** Fused score, 0-100 */
  score: number
  /** L1 voice/keyword evidence. null when the optional voice layer was not used. */
  l1: number | null
  /** L2 caller reputation */
  l2: number
  /** L3 event correlation inside the call window */
  l3: number
  scamType: ScamType
  reasons: string[]
  events: AlertEvent[]
  callStart: string
  callEnd: string
  /** Citizen chose to submit this as a complaint (consent level `case_sharing`). */
  complaintSubmitted: boolean
  status: AlertStatus
  createdAt: string
}

export type NumberStatus = 'flagged' | 'watch' | 'expired'

export type NumberType = 'mobile' | 'international' | 'voip'

export interface NumberRecord {
  callerHash: string
  callerPrefix: string
  numberType: NumberType
  firstSeen: string
  reportCount: number
  callsPerHour: number
  status: NumberStatus
  lastActivity: string
  expiresAt: string
}

export interface TimeBucket {
  /** ISO timestamp of the bucket start */
  ts: string
  count: number
}

/** Live banner + feed charts, last 12 hours. */
export interface Stats {
  totalAlerts: number
  redFlags: number
  warnings: number
  low: number
  awaitingReview: number
  oldestAwaitingReview: string | null
  byType: Record<string, number>
  overTime: TimeBucket[]
}

/** Hotspots and trends over a longer window. */
export interface Trends {
  days: number
  totalAlerts: number
  avgScore: number
  redFlagShare: number
  falsePositiveRate: number
  byDay: TimeBucket[]
  byRegion: Record<string, number>
  /** 24 entries, index = hour of day (IST) */
  byHourOfDay: number[]
  /** 10 buckets: 0-9, 10-19, ... 90-100 */
  scoreHistogram: number[]
  /** region -> scam type -> count */
  typeByRegion: Record<string, Record<string, number>>
}

export type OfficerRole = 'officer' | 'admin'

export interface Officer {
  id: string
  name: string
  email: string
  role: OfficerRole
  lastLogin?: string
}

export interface Session {
  token: string
  officer: Officer
}

export interface AuditEntry {
  id: string
  officerName: string
  action: string
  target: string
  ts: string
}

export interface FusionConfig {
  thresholds: { warning: number; red: number }
  weights: { l1: number; l2: number; l3: number }
  eventPoints: Partial<Record<AlertEventType, number>>
  unknownCallerPoints: number
  comboRules: string[]
  postCallWindowMin: number
}

export interface AlertFilter {
  callerHash?: string
}

/** Everything the UI needs from a backend. Implemented by data.mock.ts and data.api.ts. */
export interface DataSource {
  login(email: string, password: string): Promise<Session>
  getAlerts(filter?: AlertFilter): Promise<Alert[]>
  /** Fetching a case writes an audit-log entry on the backend. */
  getAlert(id: string): Promise<Alert | null>
  /** Calls onInsert for each new alert; returns an unsubscribe function. */
  subscribeToAlerts(onInsert: (a: Alert) => void): () => void
  getFlaggedNumbers(): Promise<NumberRecord[]>
  getNumber(callerHash: string): Promise<NumberRecord | null>
  updateAlertStatus(id: string, status: AlertStatus, note?: string): Promise<void>
  getStats(): Promise<Stats>
  getTrends(days: number): Promise<Trends>
  getOfficers(): Promise<Officer[]>
  getConfig(): Promise<FusionConfig>
  getAuditLog(): Promise<AuditEntry[]>
  /** Mock only: inject a scam alert immediately, for live demos. */
  simulateAlert?(): void
}
