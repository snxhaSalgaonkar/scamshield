# RakshaCall Authority Console

Next.js (App Router) dashboard for police / cyber-crime officials. It shows only real reports: the
ScamShield Android app files a report into the shared Supabase project whenever a call is flagged.

```bash
npm install
cp .env.example .env.local   # fill in the Supabase values (see SETUP.md)
npm run dev                  # http://localhost:3000
```

| Route | What it is |
|---|---|
| `/login` | Officer sign-in (Supabase email/password; non-authority accounts rejected) |
| `/dashboard` | Overview: summary cards (24 h / 7 d), charts, live feed, spikes |
| `/dashboard/reports`, `/dashboard/reports/[id]` | Filterable list + CSV export; report detail (tactics, L1/L2/L3, status) |
| `/dashboard/numbers` | Reported numbers, distinct reporters, flagging (3+ devices) |
| `POST /api/analyze` | Similarity model + optional LLM → `semantic_score` |
| `GET /api/export` | CSV export (authority only) |

How the phone connects: `app/app/src/main/java/com/scamguard/app/remote/CentralReporter.kt`.
