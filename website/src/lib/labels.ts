import type { DetectedVia, ScamType } from './types'

export const scamTypeLabel: Record<ScamType, string> = {
  digital_arrest: 'Digital Arrest',
  otp_theft: 'OTP Theft',
  kyc_bank_impersonation: 'KYC / Bank Impersonation',
  remote_access: 'Remote Access',
  refund_lottery_qr: 'Refund / Lottery / QR',
  utility_disconnection: 'Utility Disconnection',
  wrong_number_distraction: 'Wrong-Number Distraction',
  voice_clone_impersonation: 'Voice-Clone Impersonation',
  other: 'Other',
}

/** Short form for chart axes and tight table cells. */
export const scamTypeShort: Record<ScamType, string> = {
  digital_arrest: 'Digital arrest',
  otp_theft: 'OTP theft',
  kyc_bank_impersonation: 'KYC / bank',
  remote_access: 'Remote access',
  refund_lottery_qr: 'Refund / QR',
  utility_disconnection: 'Utility bill',
  wrong_number_distraction: 'Wrong number',
  voice_clone_impersonation: 'Voice clone',
  other: 'Other',
}

export const detectedViaLabel: Record<DetectedVia, string> = {
  voice: 'Voice',
  sms: 'SMS',
  reputation: 'Reputation',
  correlation: 'Correlation',
  manual_report: 'Manual report',
}

export const detectedViaHelp: Record<DetectedVia, string> = {
  voice: 'Scam phrases heard on the call (on-device speech-to-text, opt-in)',
  sms: 'OTP, debit, primer or link SMS around the call',
  reputation: 'Number already reported by other devices or part of a hot number series',
  correlation: 'Events lined up with the call: OTP during call, OTP flood, call merge',
  manual_report: 'Citizen reported the call by hand',
}

/** Layer names used on every page (contract §4). */
export const LAYERS = [
  { key: 'l1_score', code: 'L1', name: 'Content', help: 'Keywords, conversation stage, semantic similarity, LLM verdict' },
  { key: 'l2_score', code: 'L2', name: 'Reputation', help: 'Number history, distinct reporters, number-series cluster, trust list' },
  { key: 'l3_score', code: 'L3', name: 'Correlation', help: 'OTP/debit SMS during call, primer SMS before call, OTP flood, call merge' },
] as const
