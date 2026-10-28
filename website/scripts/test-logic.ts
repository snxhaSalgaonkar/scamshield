// Unit checks for the pure logic shared by every page:  npm run test:logic
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import {
  aggregateNumbers,
  circleActivity,
  computeStats,
  detectSpikes,
  falsePositiveRate,
  groupClusters,
  HOUR,
  timeBuckets,
} from '../src/lib/analytics'
import { reportsToCsv } from '../src/lib/csv'
import { applyFilters, filtersFromParams, filtersToParams, matchesFilters } from '../src/lib/filters'
import { caseRef, deviceHash, formatPhone, maskPhone } from '../src/lib/format'
import { fuse } from '../src/lib/fusion'
import { mockDataSource, simulateLocally } from '../src/lib/data.mock'
import { circleOf, NUMBER_SERIES, prefixOf } from '../src/lib/numberSeries'
import { bandOf, layersInAgreement } from '../src/lib/risk'
import { buildScenario, DEMO_NUMBERS, SIM_SCENARIOS, seededRng } from '../src/lib/scenarios'
import { groupTactics, tacticOf } from '../src/lib/tactics'
import { ACTIONS_TAKEN, DETECTED_VIA, SCAM_TYPES, type Report } from '../src/lib/types'
import { CIRCLE_NAMES } from '../src/lib/circles'

let failures = 0
function eq(name: string, actual: unknown, expected: unknown) {
  const a = JSON.stringify(actual)
  const e = JSON.stringify(expected)
  if (a === e) console.log(`  ✓ ${name}`)
  else {
    failures++
    console.log(`  ✗ ${name}\n      expected ${e}\n      got      ${a}`)
  }
}
const truthy = (name: string, v: unknown) => eq(name, Boolean(v), true)

const NOW = new Date('2026-10-28T12:30:00+05:30').getTime()
let seq = 0
function r(p: Partial<Report> & { hoursAgo?: number }): Report {
  const { hoursAgo = 1, ...rest } = p
  seq++
  return {
    id: `00000000-0000-4000-8000-${String(seq).padStart(12, '0')}`,
    device_id: `dev-${seq}`,
    caller_number: '+919876543210',
    number_prefix: '98765',
    telecom_circle: 'Punjab',
    risk_score: 80,
    l1_score: 0,
    l2_score: 15,
    l3_score: 90,
    scam_type: 'otp_theft',
    matched_phrases: [],
    reasons: [],
    similarity: null,
    detected_via: ['sms', 'correlation'],
    action_taken: 'disconnected',
    user_feedback: null,
    status: 'new',
    call_started_at: null,
    created_at: new Date(NOW - hoursAgo * HOUR).toISOString(),
    ...rest,
  }
}

console.log('Fusion (contract §4)')
eq('bank OTP during unknown call: L3 .9, L1 0, L2 .15 → 80 (F1, plain sum would be 34)', fuse(0, 0.15, 0.9), 80)
eq('F2: L3 .9 + L1 .5 → 90', fuse(0.5, 0.1, 0.9), 90)
eq('F2b: L3 .95 alone → 90', fuse(0, 0, 0.95), 90)
eq('F3: L2 .9 + L1 .4 → 80', fuse(0.4, 0.9, 0), 80)
eq('F4: L1 .8 alone → 75', fuse(0.8, 0, 0), 75)
eq('F5: L1 .8 + L2 .4 → 90', fuse(0.8, 0.4, 0), 90)
eq('F6: L2 .4 → 40 (WARN)', fuse(0, 0.4, 0), 40)
eq('below every floor: weighted sum', fuse(0.2, 0.2, 0.2), 20)
eq('trusted caller: floors F1–F5 off, ×0.5', fuse(0, 0.15, 0.9, true), 20)
eq('bands 39/40/74/75/89/90', [39, 40, 74, 75, 89, 90].map(bandOf), ['safe', 'warn', 'warn', 'high', 'high', 'scam'])
eq('layers in agreement counts ≥ 40', layersInAgreement({ l1_score: 40, l2_score: 39, l3_score: 95 }), 2)

console.log('\nFormatting')
eq('formatPhone', formatPhone('+919876543210'), '+91 98765 43210')
eq('maskPhone', maskPhone('+919876543210'), '+91 98XXX XX210')
eq('formatPhone leaves non-Indian numbers', formatPhone('+447700900123'), '+447700900123')
eq('deviceHash is stable and opaque', [deviceHash('abc'), deviceHash('abc') === deviceHash('abd')], ['dev-1a47e90b', false])
eq('caseRef', caseRef({ id: '3f9a1c22-0000-4000-8000-000000000000', created_at: '2026-10-28T00:00:00Z' }), 'RC/CYB/2026/3F9A1C')
eq('prefixOf / circleOf', [prefixOf('+919811255501'), circleOf('+919811255501'), circleOf('+910000000000')], ['98112', 'Delhi', null])

console.log('\nFalse-positive rate')
eq('none reviewed → null', falsePositiveRate([r({}), r({})]).rate, null)
eq(
  '(false_positive + safe) / (reviewed or feedback); overlap counted once',
  falsePositiveRate([
    r({ status: 'false_positive' }),
    r({ status: 'false_positive', user_feedback: 'safe' }),
    r({ user_feedback: 'safe' }),
    r({ status: 'reviewed' }),
    r({ status: 'escalated' }),
    r({}),
  ]),
  { rate: 0.6, falsePositives: 3, reviewed: 5 },
)

console.log('\nStats')
const statRows = [
  r({ hoursAgo: 0.5, risk_score: 95, action_taken: 'disconnected', scam_type: 'digital_arrest', detected_via: ['voice'] }),
  r({ hoursAgo: 3, risk_score: 45, action_taken: 'warn' }),
  r({ hoursAgo: 30, risk_score: 80 }),
  r({ hoursAgo: 200, risk_score: 80 }),
]
const s24 = computeStats(statRows, '24h', NOW)
const s7 = computeStats(statRows, '7d', NOW)
eq('24h total / 7d total (8-day-old row excluded)', [s24.total, s7.total], [2, 3])
eq('24h bands', s24.byBand, { safe: 0, warn: 1, high: 0, scam: 1 })
eq('24h by detected_via', s24.byDetectedVia, { voice: 1, sms: 1, correlation: 1 })
eq('disconnected + money estimate', [s24.disconnected, s24.moneyProtectedEstimate], [1, 85000])
eq('24 hourly / 7 daily buckets', [s24.overTime.length, s7.overTime.length], [24, 7])
eq('bucket counts sum to total', [s24.overTime.reduce((a, b) => a + b.count, 0), s7.overTime.reduce((a, b) => a + b.count, 0)], [2, 3])
eq('timeBuckets ignore rows outside range', timeBuckets([r({ hoursAgo: 25 })], '24h', NOW).reduce((a, b) => a + b.count, 0), 0)

console.log('\nSpike detection')
const quiet = Array.from({ length: 24 }, (_, i) => r({ hoursAgo: 2 + i, scam_type: 'kyc_bank_impersonation', telecom_circle: 'Delhi' }))
const burst = Array.from({ length: 5 }, (_, i) =>
  r({ hoursAgo: 0.1 + i * 0.1, scam_type: 'digital_arrest', telecom_circle: 'Maharashtra' }),
)
const spikes = detectSpikes([...quiet, ...burst, r({ hoursAgo: 5, scam_type: 'digital_arrest', telecom_circle: 'Maharashtra' })], NOW)
eq('one spike: digital arrest × Maharashtra, 5 in last hour (type-only & circle-only deduped)', spikes.map((s) => [s.scamType, s.circle, s.lastHour]), [
  ['digital_arrest', 'Maharashtra', 5],
])
eq('ratio vs 1 report in previous 24 h = 120×', Math.round(spikes[0].ratio), 120)
eq('steady traffic is not a spike (1/h baseline, 1 in last hour)', detectSpikes(quiet, NOW).length, 0)
eq('2 reports is below the minimum of 3', detectSpikes(burst.slice(0, 2), NOW).length, 0)
const steady = Array.from({ length: 48 }, (_, i) => r({ hoursAgo: 1.2 + i * 0.5, scam_type: 'otp_theft', telecom_circle: 'Bihar' }))
eq('3 in last hour vs 2/h baseline (1.5×) is not a spike', detectSpikes([...steady, ...[0.2, 0.4, 0.6].map((h) => r({ hoursAgo: h, scam_type: 'otp_theft', telecom_circle: 'Bihar' }))], NOW).length, 0)

console.log('\nClusters')
const clusterRows = [
  ...['+919811200001', '+919811200002', '+919811200003'].map((n) => r({ caller_number: n, number_prefix: '98112', telecom_circle: 'Delhi', hoursAgo: 10 })),
  ...Array.from({ length: 5 }, () => r({ caller_number: '+917042100001', number_prefix: '70421', scam_type: 'digital_arrest', hoursAgo: 50 })),
  r({ caller_number: '+919999900001', number_prefix: '99999', hoursAgo: 3 }),
  r({ caller_number: '+919999900002', number_prefix: '99999', hoursAgo: 3 }),
  ...['+918888800001', '+918888800002', '+918888800003'].map((n) => r({ caller_number: n, number_prefix: '88888', hoursAgo: 24 * 8 })),
]
const clusters = groupClusters(clusterRows, NUMBER_SERIES, NOW)
eq('≥3 numbers or ≥5 reports qualify; 2 numbers / old reports do not', clusters.map((c) => c.prefix).sort(), ['70421', '98112'])
const c98 = clusters.find((c) => c.prefix === '98112')!
eq('98112: 3 numbers, 3 reports, 3 devices, operator from number_series, trend up', [c98.numbers.length, c98.reportCount, c98.deviceCount, c98.operator, c98.trend], [3, 3, 3, 'Vi', 'up'])
eq('70421: top type digital_arrest, trend down', [clusters.find((c) => c.prefix === '70421')!.topScamType, clusters.find((c) => c.prefix === '70421')!.trend], ['digital_arrest', 'down'])

console.log('\nnumbers aggregate (mirrors refresh_number())')
const agg = aggregateNumbers([
  r({ caller_number: '+911111111111', device_id: 'a' }),
  r({ caller_number: '+911111111111', device_id: 'a' }),
  r({ caller_number: '+911111111111', device_id: 'b' }),
  r({ caller_number: '+912222222222', device_id: 'a' }),
  r({ caller_number: '+912222222222', device_id: 'b' }),
  r({ caller_number: '+912222222222', device_id: 'c', risk_score: 99 }),
  r({ caller_number: '+913333333333', device_id: 'a', user_feedback: 'safe' }),
  r({ caller_number: '+913333333333', device_id: 'b', user_feedback: 'safe' }),
  r({ caller_number: '+913333333333', device_id: 'c', user_feedback: 'safe' }),
])
const byN = Object.fromEntries(agg.map((n) => [n.number, n]))
eq('3 reports from 2 devices → reporter_count 2, not flagged', [byN['+911111111111'].report_count, byN['+911111111111'].reporter_count, byN['+911111111111'].is_flagged], [3, 2, false])
eq('3 devices → flagged, max risk', [byN['+912222222222'].is_flagged, byN['+912222222222'].max_risk_score], [true, 99])
eq('3 devices, all marked safe → not flagged', [byN['+913333333333'].safe_count, byN['+913333333333'].is_flagged], [3, false])
eq('circleActivity sorts by count', circleActivity(clusterRows.slice(0, 8)).map((c) => c.circle), ['Punjab', 'Delhi'])

console.log('\nFilters')
const fr = r({ scam_type: 'digital_arrest', risk_score: 92, status: 'escalated', detected_via: ['voice'], telecom_circle: 'Delhi', caller_number: '+919811255501', number_prefix: '98112' })
eq('all filters match', matchesFilters(fr, { scamType: 'digital_arrest', band: 'scam', status: 'escalated', detectedVia: 'voice', circle: 'Delhi', number: '98112 555', prefix: '98112' }), true)
eq('band mismatch', matchesFilters(fr, { band: 'high' }), false)
eq('detected_via mismatch', matchesFilters(fr, { detectedVia: 'sms' }), false)
eq('date range excludes', matchesFilters(fr, { from: new Date(NOW).toISOString() }), false)
const parsed = filtersFromParams(new URLSearchParams('scamType=otp_theft&band=evil&status=new&number=+91 98765&prefix=12&circle=Delhi&from=nope'))
eq('filtersFromParams drops invalid values', parsed, { scamType: 'otp_theft', status: 'new', circle: 'Delhi', number: '9198765' })
eq('filtersToParams round-trips', filtersFromParams(filtersToParams(parsed)), parsed)
const calls: string[] = []
const fake = new Proxy({} as Record<string, unknown>, {
  get: (_t, k: string) => (...args: unknown[]) => {
    calls.push(`${k}(${args.map((a) => JSON.stringify(a)).join(',')})`)
    return fake
  },
})
applyFilters(fake as never, { band: 'warn', detectedVia: 'sms', number: '+91 98-765' })
eq('applyFilters builds the PostgREST query', calls, ['gte("risk_score",40)', 'lte("risk_score",74)', 'contains("detected_via",["sms"])', 'ilike("caller_number","%9198765%")'])

console.log('\nCSV')
const csv = reportsToCsv([r({ reasons: ['He said "pay", now'], matched_phrases: ['=HYPERLINK("x")'], device_id: 'secret-device' })])
const [header, row] = csv.trim().split('\r\n')
truthy('header has contract columns', header.startsWith('id,created_at,caller_number,number_prefix,telecom_circle,risk_score,band'))
truthy('quotes are escaped', row.includes('"He said ""pay"", now"'))
truthy('formula injection neutralised', row.includes(`"'=HYPERLINK(""x"")"`))
truthy('raw device_id never exported', !csv.includes('secret-device') && row.includes(deviceHash('secret-device')))
truthy('phone numbers stay readable', row.includes(',+919876543210,'))

console.log('\nTactics')
eq('tactic categories', ['CBI officer', "don't disconnect the call", 'share the OTP', 'install AnyDesk', 'scan this QR to receive refund', 'digital arrest', 'hello'].map(tacticOf), [
  'authority', 'isolation', 'credentials', 'remote_access', 'money', 'urgency', 'other',
])
eq('groupTactics keeps category order', groupTactics(['digital arrest', 'CBI officer']).map((g) => g.tactic), ['authority', 'urgency'])

console.log('\nnumber_series matches supabase/004_seed.sql')
const seedSql = readFileSync(join(process.cwd(), 'supabase', '004_seed.sql'), 'utf8')
const sqlSeries = [...seedSql.matchAll(/\('(\d{4})','([^']+)','([^']+)'\)/g)].map((m) => `${m[1]}|${m[2]}|${m[3]}`).sort()
eq('same rows', sqlSeries, NUMBER_SERIES.map((s) => `${s.prefix}|${s.operator}|${s.telecom_circle}`).sort())
truthy('every circle is a known telecom circle', NUMBER_SERIES.every((s) => CIRCLE_NAMES.includes(s.telecom_circle!)))

console.log('\nScenarios')
for (const s of SIM_SCENARIOS) {
  const rows = buildScenario(s.id, seededRng(7), new Date(NOW))
  const valid = rows.every(
    (x) =>
      SCAM_TYPES.includes(x.scam_type) &&
      ACTIONS_TAKEN.includes(x.action_taken) &&
      x.detected_via.every((v) => DETECTED_VIA.includes(v)) &&
      x.risk_score >= 40 &&
      x.risk_score <= 100 &&
      /^\+91\d{10}$/.test(x.caller_number) &&
      new Date(x.created_at!).getTime() <= NOW,
  )
  truthy(`${s.id}: ${rows.length} valid report(s)`, valid && rows.length > 0)
}
eq('delivery OTP stays a warning', buildScenario('delivery_otp', seededRng(1)).map((x) => [bandOf(x.risk_score), x.action_taken]), [['warn', 'warn']])
eq('wrong-number OTP → SCAM band via sms + correlation', buildScenario('wrong_number_otp', seededRng(1)).map((x) => [x.risk_score, x.detected_via.sort()]), [[90, ['correlation', 'reputation', 'sms']]])
const burstRows = buildScenario('burst_series', seededRng(3), new Date(NOW)).map((x, i) => ({ ...r({}), ...x, id: String(i), number_prefix: prefixOf(x.caller_number), telecom_circle: circleOf(x.caller_number) }) as Report)
eq('burst_series forms a 93541 cluster', groupClusters(burstRows, NUMBER_SERIES, NOW).map((c) => c.prefix), ['93541'])
const spikeRows = buildScenario('burst_spike', seededRng(3), new Date(NOW)).map((x, i) => ({ ...r({}), ...x, id: String(i), number_prefix: prefixOf(x.caller_number), telecom_circle: circleOf(x.caller_number) }) as Report)
eq('burst_spike triggers a Maharashtra digital-arrest spike', detectSpikes(spikeRows, NOW).map((s) => [s.scamType, s.circle]), [['digital_arrest', 'Maharashtra']])

console.log('\nMock data source (same story as the seed)')
const numbers = await mockDataSource.getNumbers()
const nb = (n: string) => numbers.find((x) => x.number === n)
eq('Scenario B number: 2 reporters, not flagged', [nb(DEMO_NUMBERS.scenarioB)?.reporter_count, nb(DEMO_NUMBERS.scenarioB)?.is_flagged], [2, false])
eq('seeded flagged numbers', [nb('+918448312276')?.is_flagged, nb('+919423556677')?.is_flagged], [true, true])
const mockReports = await mockDataSource.getReports()
const mockStats = await mockDataSource.getStats('7d')
truthy(`~140 reports over 7 days (${mockReports.length})`, mockReports.length >= 120 && mockReports.length <= 160)
truthy(`false-positive rate is not zero (${mockStats.falsePositive.falsePositives}/${mockStats.falsePositive.reviewed})`, (mockStats.falsePositive.rate ?? 0) > 0)
const mockClusters = groupClusters(mockReports, NUMBER_SERIES).map((c) => c.prefix)
truthy(`clusters include 98112 and 70421 (${mockClusters.join(', ')})`, mockClusters.includes('98112') && mockClusters.includes('70421'))
truthy('every mock report has a known circle', mockReports.every((x) => x.telecom_circle && CIRCLE_NAMES.includes(x.telecom_circle)))
simulateLocally('wrong_number_otp')
eq('simulating Scenario B flags the number (3rd device)', (await mockDataSource.getNumber(DEMO_NUMBERS.scenarioB))?.is_flagged, true)
const page = await mockDataSource.getReportsPage({ scamType: 'digital_arrest' }, 0, 50)
truthy('paged query returns ≤ 50 rows and the full total', page.rows.length <= 50 && page.total >= page.rows.length && page.rows.every((x) => x.scam_type === 'digital_arrest'))
const one = page.rows[0]
await mockDataSource.updateReportStatus(one.id, 'false_positive')
eq('updateReportStatus persists', (await mockDataSource.getReport(one.id))?.status, 'false_positive')

console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll logic checks passed')
process.exit(failures ? 1 : 0)
