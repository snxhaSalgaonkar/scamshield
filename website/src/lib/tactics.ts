// Groups the PII-scrubbed matched_phrases a phone reports into the tactic categories shown on the
// report detail page. Keyword match only; anything unrecognised falls into "Other".

export const TACTICS = ['authority', 'isolation', 'credentials', 'money', 'remote_access', 'urgency', 'other'] as const
export type Tactic = (typeof TACTICS)[number]

export const tacticLabel: Record<Tactic, string> = {
  authority: 'Authority impersonation',
  isolation: 'Isolation / secrecy',
  credentials: 'Credential theft',
  money: 'Money demand',
  remote_access: 'Remote access',
  urgency: 'Urgency / threat',
  other: 'Other',
}

const RULES: [Tactic, RegExp][] = [
  // Order matters: first match wins.
  ['remote_access', /anydesk|teamviewer|quick ?support|screen ?share|remote|install|download|apk|\bapp\b|code on your screen/i],
  ['money', /transfer|\bpay\b|send money|refund|lottery|prize|\bfee\b|\bupi\b(?! pin)|\bqr\b|cashback|winner/i],
  ['credentials', /\botp\b|\bpin\b|password|cvv|card number|\bpan\b|\bkyc\b|verify|verification/i],
  ['isolation', /don'?t (tell|disconnect|cut|hang)|do not (tell|disconnect)|stay on|video call|secret|alone|anyone/i],
  ['authority', /\bcbi\b|police|\bncb\b|narcotics|customs|\bed officer|enforcement|\brbi\b|court|warrant|cyber ?crime|officer|department|head office|\btrai\b|income tax/i],
  ['urgency', /arrest|block|disconnect|\bcut\b|tonight|today|immediately|urgent|froze|freeze|seized|\bcase\b|crime|laundering|drugs|parcel|accident|trouble|expired|stopped|\bbill\b/i],
]

export function tacticOf(phrase: string): Tactic {
  return RULES.find(([, re]) => re.test(phrase))?.[0] ?? 'other'
}

/** Phrases grouped by tactic, in TACTICS order, empty groups dropped. */
export function groupTactics(phrases: string[]): { tactic: Tactic; phrases: string[] }[] {
  const groups = new Map<Tactic, string[]>()
  for (const p of phrases) {
    const t = tacticOf(p)
    groups.set(t, [...(groups.get(t) ?? []), p])
  }
  return TACTICS.filter((t) => groups.has(t)).map((t) => ({ tactic: t, phrases: groups.get(t)! }))
}
