// Runs supabase/*.sql against an in-process Postgres (PGlite) with a minimal stub of
// Supabase's auth schema + roles, then checks RLS, triggers and RPCs behave as the contract says.
//   npm run test:sql
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { PGlite } from '@electric-sql/pglite'

const sqlDir = process.argv[2] ?? join(dirname(fileURLToPath(import.meta.url)), '..', 'supabase')
const db = new PGlite()

let failures = 0
const ok = (name) => console.log(`  ✓ ${name}`)
const fail = (name, detail) => {
  failures++
  console.log(`  ✗ ${name}${detail ? `\n      ${detail}` : ''}`)
}
const expectEq = (name, actual, expected) =>
  JSON.stringify(actual) === JSON.stringify(expected) ? ok(name) : fail(name, `expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`)
async function expectError(name, fn, pattern) {
  try {
    await fn()
    fail(name, 'expected an error, got success')
  } catch (e) {
    if (pattern && !pattern.test(e.message)) fail(name, `wrong error: ${e.message}`)
    else ok(`${name} (${e.message.split('\n')[0].slice(0, 70)})`)
  }
}

// Supabase stub: roles, auth.users, auth.uid()/auth.role() from request.jwt.claims, default grants, publication.
await db.exec(`
  create role anon nologin;
  create role authenticated nologin;
  create role service_role nologin bypassrls;
  create schema auth;
  create table auth.users (id uuid primary key, email text);
  create function auth.uid() returns uuid language sql stable as $$
    select nullif(current_setting('request.jwt.claims', true)::json->>'sub', '')::uuid $$;
  create function auth.role() returns text language sql stable as $$
    select nullif(current_setting('request.jwt.claims', true), '')::json->>'role' $$;
  grant usage on schema auth to anon, authenticated, service_role;
  grant usage on schema public to anon, authenticated, service_role;
  alter default privileges in schema public grant all on tables to anon, authenticated, service_role;
  alter default privileges in schema public grant all on functions to anon, authenticated, service_role;
  create publication supabase_realtime;
`)

const fixture = join(process.cwd(), 'scripts', 'fixtures', 'demo_reports.sql')
for (const f of ['001_schema.sql', '002_rls.sql', '003_triggers_rpcs.sql', '004_number_series.sql']) {
  await db.exec(readFileSync(join(sqlDir, f), 'utf8'))
  console.log(`applied ${f}`)
}
await db.exec(readFileSync(fixture, 'utf8'))
console.log('applied test fixture demo_reports.sql')
// Re-running must be safe.
for (const f of ['002_rls.sql', '003_triggers_rpcs.sql', '004_number_series.sql']) await db.exec(readFileSync(join(sqlDir, f), 'utf8'))
await db.exec(readFileSync(fixture, 'utf8'))
console.log('re-applied 002–004 and the fixture (idempotency)')

const one = async (sql, params) => (await db.query(sql, params)).rows[0]
const all = async (sql, params) => (await db.query(sql, params)).rows

/** Run fn as a given Supabase user (role + JWT claims), inside a transaction that is committed. */
async function as(role, uid, fn) {
  await db.exec('begin')
  try {
    await db.query(`select set_config('request.jwt.claims', $1, true)`, [JSON.stringify(uid ? { sub: uid, role } : { role })])
    await db.exec(`set local role ${role}`)
    const out = await fn()
    await db.exec('commit')
    return out
  } catch (e) {
    await db.exec('rollback')
    throw e
  }
}

const CITIZEN = '11111111-1111-4111-8111-111111111111'
const CITIZEN2 = '22222222-2222-4222-8222-222222222222'
const OFFICER = '33333333-3333-4333-8333-333333333333'
await db.query(`insert into auth.users (id, email) values ($1, null), ($2, null), ($3, 'officer@test')`, [CITIZEN, CITIZEN2, OFFICER])
await db.query(`insert into public.profiles (id, role) values ($1, 'authority')`, [OFFICER])

console.log('\nTest fixture')
expectEq('45 fixture reports', Number((await one(`select count(*) from reports where device_id like '5eed%'`)).count), 45)
expectEq('number_series has ≥ 60 prefixes', Number((await one(`select count(*) from number_series`)).count) >= 60, true)
expectEq('every fixture report got number_prefix + circle from the trigger',
  Number((await one(`select count(*) from reports where number_prefix = '' or telecom_circle is null`)).count), 0)
expectEq('98112 cluster: ≥ 6 reports, ≥ 3 numbers',
  await one(`select count(*)::int >= 6 as r, count(distinct caller_number)::int >= 3 as n from reports where number_prefix = '98112'`), { r: true, n: true })
expectEq('70421 cluster: ≥ 5 reports',
  (await one(`select count(*)::int as c from reports where number_prefix = '70421'`)).c >= 5, true)
expectEq('Scenario B number reported by exactly 2 devices, not flagged',
  await one(`select reporter_count, is_flagged from numbers where number = '+919811255501'`), { reporter_count: 2, is_flagged: false })
expectEq('flagged numbers are the 3+ device ones',
  (await all(`select number from numbers where is_flagged order by number`)).map((r) => r.number), ['+918448312276', '+919423556677'])
expectEq('3 false positives, 2 marked safe',
  await one(`select count(*) filter (where status='false_positive')::int as fp, count(*) filter (where user_feedback='safe')::int as safe from reports`), { fp: 3, safe: 2 })
expectEq('delivery-OTP number: safe_count 2, not flagged',
  await one(`select reporter_count, safe_count, is_flagged from numbers where number = '+919167001234'`), { reporter_count: 2, safe_count: 2, is_flagged: false })
expectEq('numbers.report_count matches reports',
  Number((await one(`select count(*) from numbers n where report_count <> (select count(*) from reports r where r.caller_number = n.number)`)).count), 0)

console.log('\nanon (not signed in)')
await expectError('anon cannot read reports', () => as('anon', null, () => db.query('select * from reports')), /permission denied/)
await expectError('anon cannot call get_number_reputation', () => as('anon', null, () => db.query(`select get_number_reputation('+919811255501')`)), /permission denied/)

console.log('\nCitizen')
await expectError('cannot create an authority profile', () =>
  as('authenticated', CITIZEN, () => db.query(`insert into profiles (id, role, device_id) values ($1, 'authority', 'dev-a')`, [CITIZEN])), /row-level security/)
await as('authenticated', CITIZEN, () => db.query(`insert into profiles (id, device_id) values ($1, 'dev-a')`, [CITIZEN]))
ok('creates own citizen profile with device_id')
await as('authenticated', CITIZEN2, () => db.query(`insert into profiles (id) values ($1)`, [CITIZEN2]))
await as('authenticated', CITIZEN2, () => db.query(`update profiles set device_id = 'dev-b' where id = $1`, [CITIZEN2]))
expectEq('can set own device_id once', (await one(`select device_id from profiles where id = $1`, [CITIZEN2])).device_id, 'dev-b')
const changed = await as('authenticated', CITIZEN2, () => db.query(`update profiles set device_id = 'dev-hijack' where id = $1`, [CITIZEN2]))
expectEq('cannot change device_id afterwards', changed.affectedRows ?? 0, 0)
await expectError('cannot change own role', () => as('authenticated', CITIZEN, () => db.query(`update profiles set role = 'authority' where id = $1`, [CITIZEN])), /permission denied/)

await expectError('cannot insert a report for another device_id', () =>
  as('authenticated', CITIZEN, () => db.query(`insert into reports (device_id, caller_number, number_prefix, risk_score) values ('5eed0000-0000-4000-8000-000000000001', '+919999999999', '99999', 50)`)), /row-level security/)
await expectError('cannot insert a report pre-marked as reviewed', () =>
  as('authenticated', CITIZEN, () => db.query(`insert into reports (device_id, caller_number, number_prefix, risk_score, status) values ('dev-a', '+919999999999', '99999', 50, 'reviewed')`)), /row-level security/)

const r1 = await as('authenticated', CITIZEN, () =>
  one(`insert into reports (device_id, caller_number, number_prefix, risk_score, scam_type, detected_via, action_taken, created_at)
       values ('dev-a', '98765 43210', '', 62, 'kyc_bank_impersonation', '{voice}', 'warn', now() - interval '3 days') returning id, caller_number, number_prefix, telecom_circle, created_at > now() - interval '1 minute' as fresh`))
expectEq('trigger normalises number, fills prefix + circle, blocks backdating',
  { n: r1.caller_number, p: r1.number_prefix, c: r1.telecom_circle, fresh: r1.fresh }, { n: '+919876543210', p: '98765', c: 'Punjab', fresh: true })

expectEq('sees only own reports', await as('authenticated', CITIZEN, async () => Number((await one('select count(*) from reports')).count)), 1)
expectEq('cannot read numbers table (0 rows)', await as('authenticated', CITIZEN, async () => Number((await one('select count(*) from numbers')).count)), 0)
expectEq('can read number_series', await as('authenticated', CITIZEN, async () => Number((await one('select count(*) from number_series')).count) > 0), true)
await expectError('cannot set user_feedback directly', () => as('authenticated', CITIZEN, () => db.query(`update reports set user_feedback = 'safe' where id = $1`, [r1.id])), /permission denied/)
const st = await as('authenticated', CITIZEN, () => db.query(`update reports set status = 'reviewed'`))
expectEq('cannot change status (0 rows)', st.affectedRows ?? 0, 0)

const rep = await as('authenticated', CITIZEN, async () => (await one(`select get_number_reputation('+91 98112 55501') as r`)).r)
expectEq('get_number_reputation (pre-demo)', rep, { report_count: 2, reporter_count: 2, safe_count: 0, is_flagged: false, max_risk_score: 90, prefix_report_count: 7 })
const unknown = await as('authenticated', CITIZEN, async () => (await one(`select get_number_reputation('+910000000000') as r`)).r)
expectEq('get_number_reputation for unknown number is all zeros', unknown, { report_count: 0, reporter_count: 0, safe_count: 0, is_flagged: false, max_risk_score: 0, prefix_report_count: 0 })

console.log('\nScenario B: 3rd device reports the number → flagged')
await as('authenticated', CITIZEN, () =>
  db.query(`insert into reports (device_id, caller_number, number_prefix, risk_score, l3_score, scam_type, detected_via, action_taken)
            values ('dev-a', '+919811255501', '98112', 90, 95, 'wrong_number_distraction', '{sms,correlation}', 'disconnected')`))
expectEq('numbers row: 3 reporters, flagged', await one(`select reporter_count, is_flagged from numbers where number = '+919811255501'`), { reporter_count: 3, is_flagged: true })

console.log('\nmark_report_safe')
await as('authenticated', CITIZEN, () => db.query(`select mark_report_safe($1)`, [r1.id]))
expectEq('own report marked safe, numbers.safe_count updated', await one(`select safe_count, is_flagged from numbers where number = '+919876543210'`), { safe_count: 1, is_flagged: false })
const someoneElses = (await one(`select id from reports where device_id like '5eed%' limit 1`)).id
await expectError('cannot mark someone else’s report safe', () => as('authenticated', CITIZEN, () => db.query(`select mark_report_safe($1)`, [someoneElses])), /not yours/)
// Safe votes can un-flag: 3 reporters, 3 safe → not flagged.
await db.exec(`update reports set user_feedback = 'safe' where caller_number = '+919423556677'`)
expectEq('3 reporters / 3 safe → unflagged by feedback trigger', await one(`select safe_count, is_flagged from numbers where number = '+919423556677'`), { safe_count: 3, is_flagged: false })
await db.exec(`update reports set user_feedback = null where caller_number = '+919423556677'`)
expectEq('feedback removed → flagged again', (await one(`select is_flagged from numbers where number = '+919423556677'`)).is_flagged, true)

console.log('\nRate limit')
let accepted = 0
let rejected = null
for (let i = 0; i < 25 && !rejected; i++) {
  try {
    await as('authenticated', CITIZEN2, () => db.query(`insert into reports (device_id, caller_number, number_prefix, risk_score) values ('dev-b', $1, '', 45)`, [`+91700000${String(i).padStart(4, '0')}`]))
    accepted++
  } catch (e) {
    rejected = e.message
  }
}
expectEq('20 reports/hour accepted, 21st rejected', { accepted, rejected: /rate_limited/.test(rejected ?? '') }, { accepted: 20, rejected: true })

console.log('\nAuthority')
const total = Number((await one('select count(*) from reports')).count)
expectEq('reads all reports', await as('authenticated', OFFICER, async () => Number((await one('select count(*) from reports')).count)), total)
expectEq('reads all numbers', await as('authenticated', OFFICER, async () => Number((await one('select count(*) from numbers')).count) > 10), true)
await as('authenticated', OFFICER, () => db.query(`update reports set status = 'escalated' where id = $1`, [r1.id]))
expectEq('can update status', (await one(`select status from reports where id = $1`, [r1.id])).status, 'escalated')
await expectError('cannot update other columns', () => as('authenticated', OFFICER, () => db.query(`update reports set risk_score = 1 where id = $1`, [r1.id])), /permission denied/)
await expectError('cannot delete reports', () => as('authenticated', OFFICER, () => db.query(`delete from reports where id = $1`, [r1.id])), /permission denied/)
await expectError('cannot write numbers directly', () => as('authenticated', OFFICER, () => db.query(`update numbers set is_flagged = false`)), /permission denied/)
expectEq('is_authority() true for officer', await as('authenticated', OFFICER, async () => (await one('select is_authority() as a')).a), true)
expectEq('is_authority() false for citizen', await as('authenticated', CITIZEN, async () => (await one('select is_authority() as a')).a), false)

console.log('\nRealtime')
expectEq('reports + numbers in supabase_realtime publication',
  (await all(`select tablename from pg_publication_tables where pubname = 'supabase_realtime' order by tablename`)).map((r) => r.tablename), ['numbers', 'reports'])

console.log(failures ? `\n${failures} check(s) FAILED` : '\nAll SQL checks passed')
process.exit(failures ? 1 : 0)
