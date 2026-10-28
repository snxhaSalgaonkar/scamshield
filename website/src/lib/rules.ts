import type { AlertEvent, AlertEventType, FusionConfig } from './types'

// Default fusion config from the architecture doc (section 7). The backend owns the
// real values in its config table; the UI only uses these for labels and the mock engine.
export const DEFAULT_CONFIG: FusionConfig = {
  thresholds: { warning: 40, red: 75 },
  weights: { l1: 0.25, l2: 0.25, l3: 0.5 },
  eventPoints: {
    otp_received: 25,
    debit_sms: 30,
    forwarding_on: 35,
    merge_seen: 25,
    remote_app: 20,
    new_apk: 15,
  },
  unknownCallerPoints: 10,
  comboRules: [
    'OTP + debit within 5 minutes',
    'Call forwarding + OTP',
    'Call merge + OTP',
    'Remote-access app + debit',
  ],
  postCallWindowMin: 10,
}

export const eventLabel: Record<AlertEventType, string> = {
  call_start: 'Unknown caller connected',
  otp_received: 'OTP SMS received',
  debit_sms: 'Debit SMS received',
  forwarding_on: 'Call forwarding turned on',
  merge_seen: 'Second call merged in',
  remote_app: 'Remote-access app opened',
  new_apk: 'New APK installed (outside Play Store)',
  call_end: 'Call ended',
}

const COMBO_DEFS: { rule: string; a: AlertEventType; b: AlertEventType; withinMin?: number }[] = [
  { rule: DEFAULT_CONFIG.comboRules[0], a: 'otp_received', b: 'debit_sms', withinMin: 5 },
  { rule: DEFAULT_CONFIG.comboRules[1], a: 'forwarding_on', b: 'otp_received' },
  { rule: DEFAULT_CONFIG.comboRules[2], a: 'merge_seen', b: 'otp_received' },
  { rule: DEFAULT_CONFIG.comboRules[3], a: 'remote_app', b: 'debit_sms' },
]

/** Combo rules that force an automatic red flag, evaluated on the case's events. */
export function firedCombos(events: AlertEvent[]): string[] {
  const first = (t: AlertEventType) => events.find((e) => e.type === t)
  return COMBO_DEFS.filter(({ a, b, withinMin }) => {
    const ea = first(a)
    const eb = first(b)
    if (!ea || !eb) return false
    if (withinMin === undefined) return true
    return Math.abs(new Date(eb.ts).getTime() - new Date(ea.ts).getTime()) <= withinMin * 60_000
  }).map((c) => c.rule)
}
