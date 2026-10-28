# RakshaCall dashboard: setup guide (what to paste where)

## 1. Create the Supabase project
1. supabase.com → New project → region **Mumbai (ap-south-1)**.
2. **SQL Editor** → New query → paste and **Run** each file, in this order:
   1. `supabase/001_schema.sql`
   2. `supabase/002_rls.sql`
   3. `supabase/003_triggers_rpcs.sql` (also enables Realtime on `reports` + `numbers`)
   4. `supabase/004_seed.sql` (demo data; safe to re-run before the demo, it refreshes timestamps)
3. **Authentication → Sign In / Providers** → turn on **Allow anonymous sign-ins** (citizen phones use it).
4. **Authentication → Users → Add user → Create new user** twice (two officers, tick *Auto Confirm User*).
5. Edit the two emails in `supabase/005_authority_users.sql`, paste it in the SQL Editor, Run. It should list both emails with role `authority`.
6. Check: **Database → Publications → supabase_realtime** shows `reports` and `numbers` ticked.

## 2. Configure the dashboard
`cp .env.example .env.local`, then fill in from **Project Settings → API**:
```
NEXT_PUBLIC_DATA_SOURCE=supabase
NEXT_PUBLIC_SUPABASE_URL=<Project URL>
NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon / publishable key>
SUPABASE_SERVICE_ROLE_KEY=<service_role / secret key>   # server only
LLM_PROVIDER=anthropic          # or gemini, or none
LLM_API_KEY=<optional>
LLM_MODEL=claude-haiku-5-5
SIMULATOR_ENABLED=true
```
Then `npm install`, `npm run build`, `npm start` (or `npm run dev`). Sign in with an officer from step 1.4.
`NEXT_PUBLIC_*` and `SIMULATOR_ENABLED` are read at **build** time, so rebuild after changing them.

## 3. Send to the Kotlin team
```
SUPABASE_URL=<Project URL>
SUPABASE_ANON_KEY=<anon key>
ANALYZE_API_URL=https://<your-vercel-app>.vercel.app/api/analyze   (or the ngrok URL)
```
What the app must do (all enforced by RLS):
- On first launch: anonymous sign-in, then insert its profile once: `profiles.insert({ id: <auth uid>, device_id: <random uuid> })`.
- Insert reports with `device_id` = that same value. Max 20 per device per hour. `number_prefix`/`telecom_circle` may be left empty (the trigger fills them).
- Pre-ring lookup: `rpc("get_number_reputation", { p_number: "+919811255501" })` returns one object.
- "This was safe": `rpc("mark_report_safe", { p_report_id: "<uuid>" })`.
- Demo numbers: Scenario B is `+919811255501` (already reported by 2 seed devices; the phone's report makes it flagged).

Sample insert body:
```json
{ "device_id": "<own device_id>", "caller_number": "+919811255501", "number_prefix": "98112",
  "risk_score": 90, "l1_score": 5, "l2_score": 40, "l3_score": 95, "scam_type": "wrong_number_distraction",
  "matched_phrases": [], "reasons": ["Bank OTP SMS received during call"], "similarity": null,
  "detected_via": ["sms", "correlation"], "action_taken": "disconnected", "call_started_at": "2026-10-28T10:00:00Z" }
```

## 4. Deploy (Vercel)
Import the repo, set **Root Directory = `website`**, add the env vars from step 2, deploy.
The similarity model (~120 MB) downloads on cold start into `/tmp`. If the first `/api/analyze` call after a cold start is too slow,
run locally instead: `npm run build && npm start`, then `ngrok http 3000`, and send that URL to the Kotlin team.

## 5. Phase 9: integration test against real Supabase
After steps 1–2: `OFFICER_EMAIL=… OFFICER_PASSWORD=… npm run test:integration -- 3`
(PowerShell: `$env:OFFICER_EMAIL='…'; $env:OFFICER_PASSWORD='…'; npm run test:integration -- 3`).
It plays three phones and the officer: Scenario A/B/C, the false-positive mark, "This was safe", realtime arrival under 2 s, and RLS, 3 rounds.
Then run the same scenarios with the real Kotlin app and watch the dashboard. Pitch kit: `docs/PITCH.md`.

## 6. Tests
- `npm test`: SQL (RLS, triggers, RPCs on an in-process Postgres) + logic tests.
- `npm run test:analyze`: 15 scam / 10 genuine lines through the semantic model (add `-- <url> <token>` to hit a running endpoint).
- Demo without a phone: `/dev/simulate` (flask icon in the header).
