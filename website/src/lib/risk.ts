import type { ActionTaken, Band, ReportStatus } from './types'

// Same thresholds as the phone (contract §4): 0–39 SAFE · 40–74 WARN · 75–89 HIGH · 90–100 SCAM.
export const THRESHOLDS = { warn: 40, high: 75, scam: 90 } as const

export function bandOf(score: number): Band {
  if (score >= THRESHOLDS.scam) return 'scam'
  if (score >= THRESHOLDS.high) return 'high'
  if (score >= THRESHOLDS.warn) return 'warn'
  return 'safe'
}

/** Inclusive score range for a band, for filtering. */
export const bandRange: Record<Band, [number, number]> = {
  safe: [0, 39],
  warn: [40, 74],
  high: [75, 89],
  scam: [90, 100],
}

export const bandLabel: Record<Band, string> = {
  safe: 'Safe',
  warn: 'Warning',
  high: 'High',
  scam: 'Scam',
}

/** Colour is used only for risk: green < 40, amber 40–74, red ≥ 75 (darker red for SCAM). */
export const bandColor: Record<Band, string> = {
  safe: '#067647',
  warn: '#b54708',
  high: '#c0261b',
  scam: '#8f1610',
}

export const statusLabel: Record<ReportStatus, string> = {
  new: 'New',
  reviewed: 'Reviewed',
  escalated: 'Escalated',
  false_positive: 'False positive',
}

export const actionLabel: Record<ActionTaken, string> = {
  none: 'No action',
  warn: 'Warning shown',
  countdown_cancelled: 'Countdown cancelled',
  disconnected: 'Disconnected',
}

/** Layers scoring ≥ 40 (contract §4): 1 = low, 2 = medium, 3 = high confidence. */
export function layersInAgreement(r: { l1_score: number; l2_score: number; l3_score: number }): number {
  return [r.l1_score, r.l2_score, r.l3_score].filter((v) => v >= 40).length
}

export const confidenceLabel = (n: number) => (n >= 3 ? 'High' : n === 2 ? 'Medium' : 'Low')
