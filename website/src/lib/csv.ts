import { bandOf } from './risk'
import { deviceHash } from './format'
import type { Report } from './types'

const COLUMNS: [string, (r: Report) => string | number | null][] = [
  ['id', (r) => r.id],
  ['created_at', (r) => r.created_at],
  ['caller_number', (r) => r.caller_number],
  ['number_prefix', (r) => r.number_prefix],
  ['telecom_circle', (r) => r.telecom_circle],
  ['risk_score', (r) => r.risk_score],
  ['band', (r) => bandOf(r.risk_score)],
  ['l1_score', (r) => r.l1_score],
  ['l2_score', (r) => r.l2_score],
  ['l3_score', (r) => r.l3_score],
  ['scam_type', (r) => r.scam_type],
  ['detected_via', (r) => r.detected_via.join(';')],
  ['action_taken', (r) => r.action_taken],
  ['status', (r) => r.status],
  ['user_feedback', (r) => r.user_feedback],
  ['matched_phrases', (r) => r.matched_phrases.join('; ')],
  ['reasons', (r) => r.reasons.join('; ')],
  ['similarity_score', (r) => r.similarity?.score ?? null],
  ['similarity_matched', (r) => r.similarity?.matched ?? null],
  // Opaque tag only; the raw device_id is not exported.
  ['device', (r) => deviceHash(r.device_id)],
  ['call_started_at', (r) => r.call_started_at],
]

/** RFC 4180 quoting, plus a leading ' on values a spreadsheet would run as a formula. */
function cell(v: string | number | null): string {
  if (v === null || v === undefined) return ''
  let s = String(v)
  if (/^[=+\-@\t\r]/.test(s) && typeof v === 'string' && !/^\+\d+$/.test(s)) s = `'${s}`
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

export function reportsToCsv(reports: Report[]): string {
  const lines = [COLUMNS.map(([h]) => h).join(',')]
  for (const r of reports) lines.push(COLUMNS.map(([, get]) => cell(get(r))).join(','))
  return lines.join('\r\n') + '\r\n'
}

export const csvFilename = (now = new Date()) => `rakshacall-reports-${now.toISOString().slice(0, 16).replace(/[:T]/g, '-')}.csv`
