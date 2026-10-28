// Realistic report builders shared by mock mode and the /dev/simulate demo simulator.
// Risk scores always come from fuse() so they match what the phone would produce.
import { fuse } from './fusion'
import type { ActionTaken, DetectedVia, ReportInsert, ScamType, Similarity } from './types'

export type Rng = () => number

/** Small seeded PRNG (mulberry32) so mock data is stable across reloads. */
export function seededRng(seed: number): Rng {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

const pick = <T,>(rng: Rng, xs: readonly T[]): T => xs[Math.floor(rng() * xs.length)]
const between = (rng: Rng, lo: number, hi: number) => lo + rng() * (hi - lo)
function sample<T>(rng: Rng, xs: readonly T[], n: number): T[] {
  const pool = [...xs]
  const out: T[] = []
  while (out.length < n && pool.length) out.push(pool.splice(Math.floor(rng() * pool.length), 1)[0])
  return out
}

export function uuid(rng?: Rng): string {
  if (!rng && typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  const r = rng ?? Math.random
  const hex = Array.from({ length: 32 }, () => Math.floor(r() * 16).toString(16))
  hex[12] = '4'
  hex[16] = ((parseInt(hex[16], 16) & 0x3) | 0x8).toString(16)
  const h = hex.join('')
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`
}

interface Template {
  /** Layer ranges, 0–1 */
  l1: [number, number]
  l2: [number, number]
  l3: [number, number]
  phrases: string[]
  reasons: string[]
  similar: string[]
  via: DetectedVia[]
}

const T: Record<ScamType, Template> = {
  digital_arrest: {
    l1: [0.82, 0.95], l2: [0.2, 0.6], l3: [0, 0.45],
    phrases: ['CBI officer', 'digital arrest', "don't disconnect the call", 'money laundering case', "don't tell your family", 'drugs found in your parcel', 'arrest warrant', 'stay on video call', 'narcotics bureau'],
    reasons: ['Authority impersonation (CBI/police)', 'Isolation demand: stay on the video call', 'Secrecy demand', "Asked to move money for 'verification'"],
    similar: ['you are under digital arrest', 'drugs found in your parcel', 'an arrest warrant has been issued in your name', 'you are involved in a money laundering case'],
    via: ['voice'],
  },
  otp_theft: {
    l1: [0, 0.2], l2: [0.1, 0.4], l3: [0.9, 0.97],
    phrases: [],
    reasons: ['OTP SMS received during unknown call', 'Primer SMS 2 min before call', 'Debit SMS 40 s after OTP'],
    similar: [],
    via: ['sms', 'correlation'],
  },
  kyc_bank_impersonation: {
    l1: [0.7, 0.88], l2: [0.2, 0.5], l3: [0.2, 0.9],
    phrases: ['your KYC has expired', 'account will be blocked today', 'share the OTP to verify', 'calling from SBI head office', 'PAN card details', 'read me the OTP'],
    reasons: ['Bank impersonation', 'Asked for OTP', 'Urgency: account blocked today'],
    similar: ['your kyc is pending, account will be blocked', 'share the otp you received', 'your debit card will be blocked today'],
    via: ['voice'],
  },
  remote_access: {
    l1: [0.55, 0.8], l2: [0.1, 0.5], l3: [0.9, 0.96],
    phrases: ['install AnyDesk', 'tell me the code on your screen', 'download this app for refund'],
    reasons: ['Remote-access app opened during call', 'Screen-share code requested'],
    similar: ['install anydesk so i can help you', 'download this app to process your refund'],
    via: ['voice', 'correlation'],
  },
  refund_lottery_qr: {
    l1: [0.6, 0.86], l2: [0.1, 0.45], l3: [0.2, 0.7],
    phrases: ['scan this QR to receive refund', 'you have won a lottery', 'pay processing fee', 'enter your UPI PIN to receive money'],
    reasons: ['Refund-by-QR script', 'Upfront fee demand', 'UPI collect request during call'],
    similar: ['scan this qr code to receive your refund', 'you have won kbc lottery, pay the processing fee'],
    via: ['voice'],
  },
  utility_disconnection: {
    l1: [0.7, 0.86], l2: [0.2, 0.5], l3: [0, 0.4],
    phrases: ['your electricity will be disconnected tonight', 'pay the pending bill now', 'gas connection will be stopped'],
    reasons: ['Utility-disconnection script', 'Urgency: tonight'],
    similar: ['your electricity connection will be cut tonight', 'your electricity bill is not updated'],
    via: ['voice'],
  },
  wrong_number_distraction: {
    l1: [0, 0.2], l2: [0.15, 0.45], l3: [0.94, 0.97],
    phrases: [],
    reasons: ['Bank OTP SMS received during call', "Caller kept victim talking: 'sorry, wrong number'"],
    similar: [],
    via: ['sms', 'correlation'],
  },
  voice_clone_impersonation: {
    l1: [0.6, 0.75], l2: [0.05, 0.3], l3: [0.5, 0.92],
    phrases: ["mummy I'm in trouble", "don't tell papa", 'send money urgently', 'accident'],
    reasons: ['Family-emergency script', 'Urgent money request', 'UPI app opened during call'],
    similar: ['i am in trouble please send money urgently', 'there has been an accident, send money now'],
    via: ['voice', 'correlation'],
  },
  other: {
    l1: [0.3, 0.55], l2: [0.3, 0.5], l3: [0.2, 0.5],
    phrases: [],
    reasons: ['Unknown caller', 'Long call with unknown number'],
    similar: [],
    via: ['reputation'],
  },
}

function actionFor(risk: number, rng: Rng): ActionTaken {
  if (risk >= 90) return 'disconnected'
  if (risk >= 75) return rng() < 0.7 ? 'disconnected' : 'countdown_cancelled'
  if (risk >= 40) return 'warn'
  return 'none'
}

export interface MakeOpts {
  number: string
  deviceId?: string
  /** Override layer scores (0–1). */
  l1?: number
  l2?: number
  l3?: number
  createdAt?: Date
}

/** One plausible report of the given type. Always scores ≥ 40 (the phone only reports ≥ 40). */
export function makeReport(type: ScamType, rng: Rng, o: MakeOpts): ReportInsert {
  const t = T[type]
  const l1 = o.l1 ?? between(rng, ...t.l1)
  const l2 = o.l2 ?? between(rng, ...t.l2)
  const l3 = o.l3 ?? between(rng, ...t.l3)
  const risk = Math.max(40, fuse(l1, l2, l3))
  const via = new Set<DetectedVia>(t.via)
  if (l2 >= 0.4) via.add('reputation')
  if (l3 >= 0.5) via.add('correlation')
  if (t.phrases.length && l1 >= 0.5) via.add('voice')
  const phrases = l1 >= 0.5 ? sample(rng, t.phrases, 2 + Math.floor(rng() * 2)) : []
  const similarity: Similarity | null =
    l1 >= 0.5 && t.similar.length ? { score: Math.round(between(rng, 0.68, 0.93) * 100) / 100, matched: pick(rng, t.similar) } : null
  const created = o.createdAt ?? new Date()
  return {
    device_id: o.deviceId ?? uuid(rng),
    caller_number: o.number,
    risk_score: risk,
    l1_score: Math.round(l1 * 100),
    l2_score: Math.round(l2 * 100),
    l3_score: Math.round(l3 * 100),
    scam_type: type,
    matched_phrases: phrases,
    reasons: sample(rng, t.reasons, Math.min(t.reasons.length, 1 + Math.floor(rng() * 2))),
    similarity,
    detected_via: [...via],
    action_taken: actionFor(risk, rng),
    call_started_at: new Date(created.getTime() - between(rng, 60, 600) * 1000).toISOString(),
    created_at: created.toISOString(),
  }
}

/** Random 10-digit number in a 4- or 5-digit series. */
export const numberInSeries = (rng: Rng, series: string) =>
  `+91${series}${Array.from({ length: 10 - series.length }, () => Math.floor(rng() * 10)).join('')}`

// ---------------------------------------------------------------------------
// Demo simulator scenarios (also used by mock mode's "simulate" buttons)
// ---------------------------------------------------------------------------

/** Numbers the live demo relies on (also seeded in 004_seed.sql). */
export const DEMO_NUMBERS = {
  /** Scenario B: pre-reported by 2 devices; the demo phone (or simulator) adds the 3rd. */
  scenarioB: '+919811255501',
  /** KYC number already reported twice. */
  kyc: '+917428390011',
} as const

export const SIM_SCENARIOS = [
  { id: 'digital_arrest', label: 'Digital arrest call (voice)', detail: 'CBI/police script heard on the call → L1 high → disconnected. Uses the 70421 gang series.' },
  { id: 'wrong_number_otp', label: 'Wrong-number OTP theft (sms + correlation)', detail: `No scam words; OTP SMS arrives during the call → L3 fires. Uses Scenario B number ${DEMO_NUMBERS.scenarioB} (becomes flagged).` },
  { id: 'kyc_reputation', label: 'KYC scam (reputation)', detail: `Number already reported by other devices → L2 high. Uses ${DEMO_NUMBERS.kyc}.` },
  { id: 'delivery_otp', label: 'Delivery OTP (warning only)', detail: "OTP 'share with delivery agent' during a call → WARN, call continues. Scenario C." },
  { id: 'burst_series', label: 'Burst: 6 reports from one number series', detail: 'New 93541-XXXXX series, 4 numbers, 6 devices → a cluster appears live.' },
  { id: 'burst_spike', label: 'Burst: spike in one circle', detail: '6 digital-arrest reports in Maharashtra within a minute → spike alert banner.' },
] as const

export type SimScenarioId = (typeof SIM_SCENARIOS)[number]['id']

export const isSimScenario = (s: unknown): s is SimScenarioId => SIM_SCENARIOS.some((x) => x.id === s)

export function buildScenario(id: SimScenarioId, rng: Rng = Math.random, now = new Date()): ReportInsert[] {
  const at = (secondsAgo: number) => new Date(now.getTime() - secondsAgo * 1000)
  switch (id) {
    case 'digital_arrest':
      return [makeReport('digital_arrest', rng, { number: numberInSeries(rng, '70421'), l1: 0.93, l2: 0.45, l3: 0.1, createdAt: now })]
    case 'wrong_number_otp':
      return [makeReport('wrong_number_distraction', rng, { number: DEMO_NUMBERS.scenarioB, l1: 0.05, l2: 0.4, l3: 0.95, createdAt: now })]
    case 'kyc_reputation':
      return [makeReport('kyc_bank_impersonation', rng, { number: DEMO_NUMBERS.kyc, l1: 0.55, l2: 0.92, l3: 0.3, createdAt: now })]
    case 'delivery_otp':
      return [
        {
          ...makeReport('otp_theft', rng, { number: numberInSeries(rng, '9820'), l1: 0, l2: 0.1, l3: 0.6, createdAt: now }),
          reasons: ["OTP SMS during call ('share with delivery agent')", 'Delivery-OTP pattern: warning only'],
          action_taken: 'warn',
        },
      ]
    case 'burst_series': {
      const numbers = Array.from({ length: 4 }, () => numberInSeries(rng, '93541'))
      return Array.from({ length: 6 }, (_, i) =>
        makeReport(i % 3 === 0 ? 'wrong_number_distraction' : 'otp_theft', rng, { number: numbers[i % numbers.length], createdAt: at((6 - i) * 8) }),
      )
    }
    case 'burst_spike':
      return Array.from({ length: 6 }, (_, i) =>
        makeReport('digital_arrest', rng, { number: numberInSeries(rng, pick(rng, ['9423', '9822', '9890', '9850'])), createdAt: at((6 - i) * 7) }),
      )
  }
}

// ---------------------------------------------------------------------------
// Background traffic for mock mode
// ---------------------------------------------------------------------------

const TYPE_WEIGHTS: [ScamType, number][] = [
  ['digital_arrest', 22],
  ['otp_theft', 18],
  ['kyc_bank_impersonation', 16],
  ['wrong_number_distraction', 9],
  ['remote_access', 9],
  ['refund_lottery_qr', 9],
  ['utility_disconnection', 8],
  ['voice_clone_impersonation', 6],
  ['other', 3],
]

export function randomScamType(rng: Rng): ScamType {
  const total = TYPE_WEIGHTS.reduce((s, [, w]) => s + w, 0)
  let x = rng() * total
  for (const [t, w] of TYPE_WEIGHTS) if ((x -= w) < 0) return t
  return 'other'
}
