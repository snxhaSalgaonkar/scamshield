# Supabase and deployment setup

This guide configures the RakshaCall web console. The Android app currently works offline and does not send reports to Supabase.

## 1. Create and configure Supabase

Create a Supabase project. In its **SQL Editor**, run these files in order:

1. `supabase/001_schema.sql` — tables and indexes.
2. `supabase/002_rls.sql` — row-level security and policies.
3. `supabase/003_triggers_rpcs.sql` — report triggers, RPCs, and Realtime publication.
4. `supabase/004_seed.sql` — optional demo data; safe to rerun to refresh seed data.

Create an officer account:

1. Open **Authentication → Users → Add user → Create new user**.
2. Enter the officer's email and password, and enable **Auto Confirm User**.
3. Edit `supabase/005_authority_users.sql` so the `where u.email in (...)` list contains the exact email address(es) of the Auth user(s). Run it in the SQL Editor.
4. Confirm the result lists each officer email with `role = authority`.

An email in a database table is not a Supabase Auth account. A login succeeds only when the user exists under **Authentication → Users**, uses that Auth password, and has an `authority` profile. Reset the password in Supabase if it is unknown. Do not put passwords in SQL or commit them.

Anonymous sign-ins are only needed if you later connect citizen devices to Supabase. They are not needed for officer email/password login. At present, the Android app has no internet permission and does not use this cloud integration.

## 2. Configure local development

From this directory, copy the example file:

```powershell
Copy-Item .env.example .env.local
```

Set the values from **Supabase → Project Settings → API**:

```dotenv
NEXT_PUBLIC_DATA_SOURCE=supabase
NEXT_PUBLIC_SUPABASE_URL=https://<project-ref>.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=<anon-or-publishable-key>
SUPABASE_SERVICE_ROLE_KEY=<service-role-secret>
SIMULATOR_ENABLED=true
LLM_PROVIDER=none
```

Use the project base URL only. For example, `https://<project-ref>.supabase.co` is valid; do not append `/rest/v1`, `/auth/v1`, a trailing route, query, or fragment. The service-role key is server-only and must never be exposed in browser code or committed. `.env.local` is ignored by Git.

`NEXT_PUBLIC_DATA_SOURCE` must be exactly `supabase` or `mock`. If it is omitted, the app defaults to mock mode; invalid values produce a configuration error. Mock mode is for local demos only and accepts any email/password at its login screen.

Install dependencies and start the app:

```bash
npm install
npm run dev
```

Open `http://localhost:3000`. For a production build, run `npm run build` then `npm start`.

### Optional semantic LLM

The `/api/analyze` endpoint can use the local similarity model without a hosted LLM. To enable an LLM, set `LLM_PROVIDER` to `anthropic` or `gemini`, provide `LLM_API_KEY`, and set `LLM_MODEL` to a model available to that account. Leave `LLM_PROVIDER=none` to avoid a third-party model call. Send only PII-scrubbed text to this endpoint.

## 3. Deploy to Vercel

1. Import the repository and set **Root Directory** to `website`.
2. Add the environment variables above in the Vercel project settings. For Supabase mode, `NEXT_PUBLIC_DATA_SOURCE`, `NEXT_PUBLIC_SUPABASE_URL`, and `NEXT_PUBLIC_SUPABASE_ANON_KEY` must all refer to the same Supabase project.
3. Deploy or redeploy after changing any `NEXT_PUBLIC_*` value; Next.js embeds these values at build time.
4. Create and authorize officer users as described in step 1.

`SUPABASE_SERVICE_ROLE_KEY` is used only by the server-side demo simulator. Keep it configured as a Vercel server environment variable and never name it `NEXT_PUBLIC_*`.

## 4. Optional integration checks

From `website/`:

```bash
npm run lint
npm run typecheck
npm test
```

`npm test` runs SQL and logic checks. `npm run test:integration` targets a configured Supabase project and requires `OFFICER_EMAIL` and `OFFICER_PASSWORD` for an authorized test account. In PowerShell, set them for the current shell before running it:

```powershell
$env:OFFICER_EMAIL = 'officer@example.gov'
$env:OFFICER_PASSWORD = '<test-account-password>'
npm run test:integration
```

`npm run test:analyze` exercises the semantic analyzer. Avoid running integration tests against production data; use a separate test project.

## 5. Current Android integration boundary

The database includes policies and RPCs designed for a future citizen-device integration. The Android app currently has no `INTERNET` permission and uses a local-only gateway. It does not insert reports, query number reputation, or call `/api/analyze`. Dashboard reports currently come from seed data, the web simulator, or data entered through other authorized database workflows. See the [feature audit](../FEATURE_AUDIT.md) before treating cloud ingestion as an implemented feature.
