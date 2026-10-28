// Phase 9: end-to-end checks against the REAL Supabase project (run after SETUP.md steps 1–2).
// Plays the phones (anonymous citizens) and the officer, for Scenarios A/B/C + feedback + realtime + RLS.
//   OFFICER_EMAIL=… OFFICER_PASSWORD=… npm run test:integration [-- 3]   (optional repeat count)
// Uses NEXT_PUBLIC_SUPABASE_URL / NEXT_PUBLIC_SUPABASE_ANON_KEY from .env.local. Inserts real rows (device ids start with "itest-").
import { readFileSync } from 'node:fs'
import { createClient, type SupabaseClient } from '@supabase/supabase-js'

for (const line of (() => { try { return readFileSync('.env.local', 'utf8').split(/\r?\n/) } catch { return [] } })()) {
  const m = /^([A-Z0-9_]+)=(.*)$/.exec(line.trim())
  if (m && !process.env[m[1]]) process.env[m[1]] = m[2]
}
const URL = process.env.NEXT_PUBLIC_SUPABASE_URL!
const ANON = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!
const { OFFICER_EMAIL, OFFICER_PASSWORD } = process.env
if (!URL || !ANON || !OFFICER_EMAIL || !OFFICER_PASSWORD) {
  console.error('Need NEXT_PUBLIC_SUPABASE_URL, NEXT_PUBLIC_SUPABASE_ANON_KEY (.env.local) and OFFICER_EMAIL, OFFICER_PASSWORD')
  process.exit(2)
}

let failures = 0
const ok = (n: string, d?: string) => console.log(`  ✓ ${n}${d ? ` (${d})` : ''}`)
const fail = (n: string, d?: string) => {
  failures++
  console.log(`  ✗ ${n}${d ? `: ${d}` : ''}`)
}
const expect = (n: string, cond: boolean, d?: string) => (cond ? ok(n, d) : fail(n, d))
const client = () => createClient(URL, ANON, { auth: { persistSession: false, autoRefreshToken: false } })

async function citizen(): Promise<{ sb: SupabaseClient; deviceId: string }> {
  const sb = client()
  const { data, error } = await sb.auth.signInAnonymously()
  if (error || !data.user) throw new Error(`anonymous sign-in failed (enable it in Auth settings): ${error?.message}`)
  const deviceId = `itest-${crypto.randomUUID()}`
  const p = await sb.from('profiles').insert({ id: data.user.id, device_id: deviceId })
  if (p.error) throw new Error(`profile insert: ${p.error.message}`)
  return { sb, deviceId }
}

const randomNumber = (series: string) => `+91${series}${Array.from({ length: 10 - series.length }, () => Math.floor(Math.random() * 10)).join('')}`

async function round(i: number) {
  console.log(`\nRound ${i}`)
  const officer = client()
  const login = await officer.auth.signInWithPassword({ email: OFFICER_EMAIL!, password: OFFICER_PASSWORD! })
  if (login.error) throw new Error(`officer login: ${login.error.message}`)

  // Realtime: officer subscribes before the phones report.
  const arrived = new Map<string, number>()
  const channel = officer
    .channel(`itest-${i}`)
    .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'reports' }, (p) => arrived.set((p.new as { id: string }).id, Date.now()))
  await new Promise<void>((res, rej) => {
    const t = setTimeout(() => rej(new Error('realtime subscribe timed out')), 10_000)
    channel.subscribe((s) => s === 'SUBSCRIBED' && (clearTimeout(t), res()))
  })
  ok('officer realtime subscribed')

  const [a, b, c] = await Promise.all([citizen(), citizen(), citizen()])
  ok('3 anonymous citizen devices registered')

  // Scenario A — digital arrest (voice)
  const t0 = Date.now()
  const ra = await a.sb.from('reports').insert({
    device_id: a.deviceId, caller_number: randomNumber('70421'), number_prefix: '', risk_score: 90, l1_score: 92, l2_score: 45, l3_score: 10,
    scam_type: 'digital_arrest', matched_phrases: ['CBI officer', 'digital arrest', "don't disconnect the call"],
    reasons: ['Authority impersonation (CBI)', 'Isolation demand'], similarity: { score: 0.88, matched: 'you are under digital arrest' },
    detected_via: ['voice'], action_taken: 'disconnected', call_started_at: new Date().toISOString(),
  }).select().single()
  expect('Scenario A: phone inserts report', !ra.error, ra.error?.message)
  if (ra.data) {
    expect('trigger filled prefix + circle', ra.data.number_prefix === '70421' && !!ra.data.telecom_circle, `${ra.data.number_prefix} / ${ra.data.telecom_circle}`)
    const seen = await officer.from('reports').select('matched_phrases, similarity, l1_score').eq('id', ra.data.id).single()
    expect('officer sees phrases, similarity, L1', (seen.data?.matched_phrases as string[] | undefined)?.length === 3 && seen.data?.l1_score === 92)
    for (let w = 0; w < 30 && !arrived.has(ra.data.id); w++) await new Promise((r) => setTimeout(r, 100))
    const ms = arrived.has(ra.data.id) ? arrived.get(ra.data.id)! - t0 : null
    expect('report arrives on officer realtime < 2 s', ms !== null && ms < 2000, ms === null ? 'not received' : `${ms} ms`)
  }

  // Scenario B — wrong-number OTP, 3 devices → flagged (fresh number each round so it's repeatable)
  const bNumber = randomNumber('98112')
  for (const dev of [a, b, c]) {
    const r = await dev.sb.from('reports').insert({
      device_id: dev.deviceId, caller_number: bNumber, number_prefix: '98112', risk_score: 90, l1_score: 5, l2_score: 40, l3_score: 95,
      scam_type: 'wrong_number_distraction', reasons: ['Bank OTP SMS received during call'], detected_via: ['sms', 'correlation'], action_taken: 'disconnected',
    })
    if (r.error) fail('Scenario B insert', r.error.message)
  }
  const num = await officer.from('numbers').select('reporter_count, is_flagged').eq('number', bNumber).single()
  expect('Scenario B: 3 devices → number flagged', num.data?.reporter_count === 3 && num.data?.is_flagged === true, JSON.stringify(num.data))
  const rep = await c.sb.rpc('get_number_reputation', { p_number: bNumber })
  expect('phone pre-ring lookup sees is_flagged', (rep.data as { is_flagged?: boolean } | null)?.is_flagged === true, JSON.stringify(rep.data))

  // Scenario C — delivery OTP: warning only
  const rc = await b.sb.from('reports').insert({
    device_id: b.deviceId, caller_number: randomNumber('9820'), number_prefix: '', risk_score: 40, l3_score: 60, scam_type: 'otp_theft',
    reasons: ["OTP SMS during call ('share with delivery agent')"], detected_via: ['sms', 'correlation'], action_taken: 'warn',
  }).select('id, action_taken').single()
  expect('Scenario C: WARN report, no disconnect', rc.data?.action_taken === 'warn', rc.error?.message)

  // Officer marks false positive; citizen marks safe
  if (rc.data) {
    const up = await officer.from('reports').update({ status: 'false_positive' }).eq('id', rc.data.id).select('status').single()
    expect('officer sets status false_positive', up.data?.status === 'false_positive', up.error?.message)
    const safe = await b.sb.rpc('mark_report_safe', { p_report_id: rc.data.id })
    expect('citizen "This was safe" via RPC', !safe.error, safe.error?.message)
    const n = await officer.from('reports').select('caller_number').eq('id', rc.data.id).single()
    const sn = await officer.from('numbers').select('safe_count').eq('number', n.data!.caller_number).single()
    expect('numbers.safe_count incremented', sn.data?.safe_count === 1)
  }

  // RLS
  const own = await a.sb.from('reports').select('device_id')
  expect('citizen reads only own reports', !!own.data && own.data.every((r) => r.device_id === a.deviceId), `${own.data?.length} rows`)
  const nums = await a.sb.from('numbers').select('number')
  expect('citizen cannot read numbers', (nums.data?.length ?? 0) === 0)
  const sneaky = await a.sb.from('reports').insert({ device_id: b.deviceId, caller_number: '+919999999999', number_prefix: '99999', risk_score: 50 })
  expect('citizen cannot report as another device', !!sneaky.error)

  await officer.removeChannel(channel)
}

const rounds = Number(process.argv[2] ?? 1)
for (let i = 1; i <= rounds; i++) {
  try {
    await round(i)
  } catch (e) {
    fail(`round ${i} aborted`, e instanceof Error ? e.message : String(e))
  }
}
console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll integration checks passed')
console.log('Clean up test rows later with:  delete from numbers where number in (select caller_number from reports where device_id like \'itest-%\'); delete from reports where device_id like \'itest-%\';')
process.exit(failures ? 1 : 0)
