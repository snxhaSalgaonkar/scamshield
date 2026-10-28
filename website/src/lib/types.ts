// Mirrors SHARED_CONTRACT.md §4–§5 exactly (snake_case, same strings). Do not rename fields here
// without changing the contract and telling the Kotlin team.

export const SCAM_TYPES = [
  'digital_arrest',
  'otp_theft',
  'kyc_bank_impersonation',
  'remote_access',
  'refund_lottery_qr',
  'utility_disconnection',
  'wrong_number_distraction',
  'voice_clone_impersonation',
  'other',
] as const
export type ScamType = (typeof SCAM_TYPES)[number]

export const DETECTED_VIA = ['voice', 'sms', 'reputation', 'correlation', 'manual_report'] as const
export type DetectedVia = (typeof DETECTED_VIA)[number]

export const ACTIONS_TAKEN = ['none', 'warn', 'countdown_cancelled', 'disconnected'] as const
export type ActionTaken = (typeof ACTIONS_TAKEN)[number]

export const REPORT_STATUSES = ['new', 'reviewed', 'escalated', 'false_positive'] as const
export type ReportStatus = (typeof REPORT_STATUSES)[number]

export type UserFeedback = 'safe'

/** 0–39 SAFE · 40–74 WARN · 75–89 HIGH · 90–100 SCAM (contract §4). */
export const BANDS = ['safe', 'warn', 'high', 'scam'] as const
export type Band = (typeof BANDS)[number]

export interface Similarity {
  score: number
  matched: string
}

/** One row of `reports`. No transcript, no victim PII. */
export interface Report {
  id: string
  device_id: string
  caller_number: string
  number_prefix: string
  telecom_circle: string | null
  risk_score: number
  l1_score: number
  l2_score: number
  l3_score: number
  scam_type: ScamType
  matched_phrases: string[]
  reasons: string[]
  similarity: Similarity | null
  detected_via: DetectedVia[]
  action_taken: ActionTaken
  user_feedback: UserFeedback | null
  status: ReportStatus
  call_started_at: string | null
  created_at: string
}

/** What the phone (or simulator) sends; the rest is defaulted / filled by triggers. */
export type ReportInsert = Omit<Report, 'id' | 'created_at' | 'number_prefix' | 'telecom_circle' | 'status' | 'user_feedback'> & {
  number_prefix?: string
  telecom_circle?: string | null
  status?: ReportStatus
  user_feedback?: UserFeedback | null
  created_at?: string
}

/** One row of `numbers` (maintained by trigger). */
export interface NumberRecord {
  number: string
  number_prefix: string
  telecom_circle: string | null
  first_seen: string
  last_activity: string
  report_count: number
  reporter_count: number
  safe_count: number
  max_risk_score: number
  is_flagged: boolean
}

/** One row of `number_series`. */
export interface NumberSeries {
  prefix: string
  operator: string | null
  telecom_circle: string | null
}

export type StatsRange = '24h' | '7d'

export interface TimeBucket {
  /** ISO timestamp of the bucket start */
  ts: string
  count: number
}

export interface Stats {
  range: StatsRange
  total: number
  byBand: Record<Band, number>
  byScamType: Partial<Record<ScamType, number>>
  byDetectedVia: Partial<Record<DetectedVia, number>>
  disconnected: number
  /** disconnected × AVG_LOSS_PREVENTED_INR — an estimate, always label it as one. */
  moneyProtectedEstimate: number
  falsePositive: FalsePositiveRate
  overTime: TimeBucket[]
  awaitingReview: number
}

export interface FalsePositiveRate {
  /** null when nothing has been reviewed or given feedback yet */
  rate: number | null
  falsePositives: number
  reviewed: number
}

export interface ReportFilters {
  /** ISO lower bound on created_at (inclusive) */
  from?: string
  /** ISO upper bound on created_at (exclusive) */
  to?: string
  scamType?: ScamType
  band?: Band
  status?: ReportStatus
  detectedVia?: DetectedVia
  circle?: string
  /** Matches anywhere in caller_number (digits only are compared). */
  number?: string
  /** Exact number_prefix */
  prefix?: string
  limit?: number
}

export interface ReportPage {
  rows: Report[]
  total: number
}

export type RealtimeStatus = 'connecting' | 'live' | 'offline'

/** Everything the UI needs from a backend. Implemented by data.supabase.ts and data.mock.ts. */
export interface DataSource {
  getReports(filters?: ReportFilters): Promise<Report[]>
  /** One page of reports, newest first, with the total count for the same filters. */
  getReportsPage(filters: ReportFilters, page: number, pageSize: number): Promise<ReportPage>
  getReport(id: string): Promise<Report | null>
  getReportsByNumber(number: string): Promise<Report[]>
  getReportsByPrefix(prefix: string): Promise<Report[]>
  updateReportStatus(id: string, status: ReportStatus): Promise<void>
  getNumbers(opts?: { flaggedOnly?: boolean }): Promise<NumberRecord[]>
  getNumber(number: string): Promise<NumberRecord | null>
  getNumberSeries(): Promise<NumberSeries[]>
  getStats(range: StatsRange): Promise<Stats>
  /** CSV of all reports matching the filters (authority only). */
  exportCsv(filters: ReportFilters): Promise<Blob>
  subscribeToReports(
    onChange: (r: Report, type: 'INSERT' | 'UPDATE') => void,
    onStatus?: (s: RealtimeStatus) => void,
  ): () => void
  subscribeToNumbers(onChange: (n: NumberRecord) => void): () => void
}
