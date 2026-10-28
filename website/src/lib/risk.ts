import { DEFAULT_CONFIG } from './rules'
import type { AlertStatus, NumberStatus, NumberType, RiskLevel } from './types'

// Same thresholds as the app and the backend config table: <40 silent, 40-74 warning, >=75 red flag.
export function riskLevel(score: number): RiskLevel {
  if (score >= DEFAULT_CONFIG.thresholds.red) return 'red'
  if (score >= DEFAULT_CONFIG.thresholds.warning) return 'warning'
  return 'silent'
}

export const riskLabel: Record<RiskLevel, string> = {
  silent: 'Low',
  warning: 'Warning',
  red: 'Red flag',
}

export const statusLabel: Record<AlertStatus, string> = {
  new: 'New',
  reviewed: 'Reviewed',
  escalated: 'Escalated',
  false_positive: 'False positive',
}

export const numberStatusLabel: Record<NumberStatus, string> = {
  flagged: 'Flagged',
  watch: 'Watch',
  expired: 'Expired',
}

export const numberTypeLabel: Record<NumberType, string> = {
  mobile: 'Mobile',
  international: 'International',
  voip: 'VoIP',
}
