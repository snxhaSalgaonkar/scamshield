// One-line explanations for each layer on the report detail page, built from what the phone sent.
import type { NumberRecord, Report } from './types'

const firstMatch = (reasons: string[], re: RegExp) => reasons.find((r) => re.test(r))

export function explainL1(r: Report): string {
  const hit = firstMatch(r.reasons, /impersonation|script|demand|asked|urgency|secrecy|isolation/i)
  if (r.matched_phrases.length) {
    const n = r.matched_phrases.length
    return `${n} scam tactic${n > 1 ? 's' : ''} heard on the call${r.similarity ? ', matches a known scam pattern' : ''}${hit ? ` (${hit})` : ''}.`
  }
  if (r.l1_score >= 40) return hit ?? 'Scam language detected on the call.'
  return 'No scam language detected, or the optional voice layer was not used on this call.'
}

export function explainL2(r: Report, n?: NumberRecord | null): string {
  const hit = firstMatch(r.reasons, /reported|devices?|cluster|series|flagged/i)
  if (hit) return `${hit}.`
  if (r.l2_score >= 90) return 'Number already flagged by several independent devices.'
  if (r.l2_score >= 40) return 'Number or its number series has earlier reports.'
  if (n && n.reporter_count > 1) return `Little history at call time; now reported by ${n.reporter_count} devices.`
  return 'Little or no earlier history for this number at call time.'
}

export function explainL3(r: Report): string {
  const hit = firstMatch(r.reasons, /otp|sms|debit|app|merge|primer|upi|flood/i)
  if (hit) return `${hit}.`
  if (r.l3_score >= 90) return 'Strong correlation: OTP or debit SMS arrived during the call.'
  if (r.l3_score >= 40) return 'Some phone events lined up with the call.'
  return 'No suspicious SMS or app events during the call.'
}
