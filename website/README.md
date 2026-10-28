# RakshaCall Authority Console

Next.js (App Router) dashboard for police / cyber-crime officials, plus the `/api/analyze` semantic endpoint for the phone.
Shares one Supabase project with the Kotlin app (see `SHARED_CONTRACT.md`).

```bash
npm install
npm run dev        # http://localhost:3000 — mock mode, no backend needed (any email/password signs in)
```

Setup with real Supabase, what to paste where, and what to send the Kotlin team: **[SETUP.md](SETUP.md)**.

| Route | What it is |
|---|---|
| `/login` | Officer sign-in (Supabase email/password; non-authority accounts rejected) |
| `/dashboard` | Overview: stat cards (24 h / 7 d), charts, live feed, active spikes |
| `/dashboard/reports`, `/dashboard/reports/[id]` | Filterable list + CSV export; report detail (tactics, L1/L2/L3, pattern match, status) |
| `/dashboard/numbers` | Reported numbers, distinct reporters, live flagging |
| `/dashboard/clusters` | Number-series (gang) clusters, block recommendation |
| `/dashboard/map` | Reports by telecom circle |
| `/command` | Full-screen projector view |
| `/dev/simulate` | Demo simulator (`SIMULATOR_ENABLED=true`) |
| `POST /api/analyze` | Similarity model + optional LLM → `semantic_score` (contract §6) |
| `GET /api/export` | CSV export (authority only) |

`NEXT_PUBLIC_DATA_SOURCE=mock|supabase` switches the data source; mock mode mirrors the seed data and adds a report every 20 s.
The "money protected" figure is a placeholder (`src/lib/estimate.ts`); replace it with a sourced number before presenting.
