# RakshaCall Authority Console

RakshaCall is the Next.js web console in ScamShield. It supports mock data for demos and Supabase for officer authentication, reports, and live database-backed views. The companion Android app currently has no internet permission, so it does not upload incident reports to this console.

## Local development

Requirements: Node.js/npm compatible with the lockfile. From this directory:

```bash
npm install
npm run dev
```

Open `http://localhost:3000`. The default data source is mock mode if `NEXT_PUBLIC_DATA_SOURCE` is unset or set to `mock`. Mock login accepts any email and password and is for local demos only.

For Supabase setup, officer provisioning, environment variables, SQL migration order, and Vercel deployment, follow [SETUP.md](SETUP.md).

## Routes

| Route | Description |
|---|---|
| `/` | Public ScamShield landing page |
| `/login` | Officer sign-in; Supabase mode requires an Auth user with `profiles.role = 'authority'` |
| `/dashboard` | Summary statistics, charts, active spikes, and live feed |
| `/dashboard/reports` | Searchable/filterable report list and CSV export |
| `/dashboard/reports/[id]` | Report detail and status review |
| `/dashboard/numbers` | Report history and aggregated number reputation |
| `/dashboard/clusters` | Number-series grouping for officer review |
| `/dashboard/map` | Reports grouped by telecom circle |
| `/command` | Full-screen command display |
| `/dev/simulate` | Demo scenarios; enabled in mock mode or with `SIMULATOR_ENABLED=true` |
| `POST /api/analyze` | PII-scrubbed text analysis endpoint; requires a bearer token in Supabase mode |
| `GET /api/export` | CSV export; officer-only in Supabase mode |

## Commands

```bash
npm run lint
npm run typecheck
npm test
```

`npm test` runs `test:sql` and `test:logic`. Other suites are available as `npm run test:integration` and `npm run test:analyze`; their prerequisites and usage are in [SETUP.md](SETUP.md).

## Data source and credentials

Set `NEXT_PUBLIC_DATA_SOURCE` to exactly `mock` or `supabase`. The Supabase URL must be the project base URL, without an API path. Public URL and anon key are embedded at build time. `SUPABASE_SERVICE_ROLE_KEY` is server-only and must never be exposed to browser code or committed.

In Supabase mode, a valid email/password alone is not sufficient: the user must exist in Supabase Authentication and have a matching `public.profiles` row with role `authority`. See [SETUP.md](SETUP.md) for the complete setup.

## Known limitation

The site can display seeded, simulated, or database-backed records. The Android app's `LocalOnlyGateway` and lack of internet permission mean that live phone-to-Supabase report ingestion is not implemented yet. The displayed “money protected” estimate is a placeholder in `src/lib/estimate.ts` and should be sourced before external presentations.
