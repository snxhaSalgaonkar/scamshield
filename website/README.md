# RakshaCall Authority Console

Police and cyber-crime officers' web console for **RakshaCall**. Citizens' Android phones detect fraud calls
(digital arrest, OTP theft, fake KYC) with a three-layer risk engine and report events and scores to a FastAPI
backend. This console shows those alerts live.

It never shows SMS text, OTP values or call audio, because the phone never sends them.

Stack: React 19, Vite, TypeScript, Tailwind CSS 4, shadcn/ui (Base UI), Recharts, TanStack Query, React Router.

## Run it

```bash
npm install
npm run dev          # http://localhost:5173
```

It starts in **mock mode**: about 125 generated alerts over the past week, and a new one every 20 seconds.
Sign in with any email and password. Put `admin` in the email (for example `admin@cyber.demo`) to see
the Administration page.

## Pages

| Route | What it shows |
|---|---|
| `/login` | Officer sign-in |
| `/dashboard` | Live feed: shift summary, charts, filterable alert table. Pause holds new alerts; red flags raise a notification |
| `/dashboard/cases/:id` | Case file: score dial, L1/L2/L3 breakdown, phone-event timeline with points and combo rules, caller reputation, status change and escalation (1930 complaint summary) |
| `/dashboard/numbers` | Flagged-number register, sortable; click a number to filter the feed |
| `/dashboard/trends` | Hotspots by region, time of day, score distribution, daily volume, type-by-region matrix |
| `/dashboard/admin` | Officers, fusion-engine config, audit log (admin role only) |
| `/command` | Full-screen **command wall** for the projector: city beacons, incoming log, siren on red flags |

### Demo tips

- **Simulate scam call** on the live feed, or press **S** on the command wall, injects a red-flag alert at once.
- Put `/command` on the projector. Each red flag flares its city and runs the siren strip.
- Escalating a case stamps the case file and writes to the audit log (see Administration).

## Switching to real data

Every page reads through one interface, `DataSource` in [src/lib/types.ts](src/lib/types.ts).
[src/lib/data.ts](src/lib/data.ts) picks the implementation:

- [src/lib/data.mock.ts](src/lib/data.mock.ts): in-memory demo data (default)
- [src/lib/data.api.ts](src/lib/data.api.ts): the FastAPI backend, including the `/police/ws` WebSocket

Once the backend is running:

1. `cp .env.example .env.local`
2. Set `VITE_DATA_SOURCE=api` and `VITE_API_URL` to the backend URL.
3. Restart `npm run dev`.

No other code changes are needed. The backend must allow CORS from the dashboard's origin.

### API contract the dashboard expects

All `/police/*` and `/admin/*` calls send `Authorization: Bearer <token>`. A `401` signs the officer out.
Fields are snake_case; `data.api.ts` maps them.

| Call | Response |
|---|---|
| `POST /police/login` `{email, password}` | `{access_token, officer: {id, name, email, role}}` where `role` is `officer` or `admin` |
| `GET /police/alerts?caller_hash=` | `Alert[]` (newest first) |
| `GET /police/alerts/{id}` | `Alert`. The backend writes the audit-log entry for the view |
| `POST /police/alerts/{id}/feedback` `{status, note}` | `204`; status is `new`, `reviewed`, `escalated` or `false_positive` |
| `GET /police/numbers` | `Number[]` |
| `GET /police/numbers/{caller_hash}` | `Number` |
| `GET /police/stats?hours=12` | `{total_alerts, red_flags, warnings, low, awaiting_review, oldest_awaiting_review, by_type: {type: n}, over_time: [{ts, count}]}` (12 hourly buckets) |
| `GET /police/stats/trends?days=7` | `{days, total_alerts, avg_score, red_flag_share, false_positive_rate, by_day: [{ts, count}], by_region: {region: n}, by_hour_of_day: [24], score_histogram: [10], type_by_region: {region: {type: n}}}` |
| `WS /police/ws?token=` | One message per new alert: `{"type": "alert", "alert": Alert}` (a bare `Alert` also works) |
| `GET /admin/officers` | `[{id, name, email, role, last_login}]` |
| `GET /admin/config` | `{thresholds: {warning, red}, weights: {l1, l2, l3}, event_points: {otp_received: 25, ...}, unknown_caller_points, combo_rules: [string], post_call_window_min}` |
| `GET /admin/audit` | `[{id, officer_name, action, target, ts}]` |

`Alert`, joined from `alerts`, `call_events`, `devices` and the call's SMS and signal events:

```jsonc
{
  "id": "…", "call_event_id": "…", "device_id": "…",
  "caller_hash": "a3f9c1d2", "caller_prefix": "+91 98765 XXXXX", "region": "Mumbai",
  "score": 91, "l1": null, "l2": 70, "l3": 85,          // l1 is null when the voice layer was off
  "scam_type": "OTP Theft", "reasons": ["OTP SMS received", "..."],
  "events": [{"type": "otp_received", "ts": "…"}, {"type": "debit_sms", "ts": "…", "amount": 49999}],
  "call_start": "…", "call_end": "…", "complaint_submitted": false,
  "status": "new", "created_at": "…"
}
```

The event `type` values are `call_start`, `otp_received`, `debit_sms`, `forwarding_on`, `merge_seen`,
`remote_app`, `new_apk` and `call_end`.

`Number`: `{number_hash, caller_prefix, number_type: "mobile"|"international"|"voip", first_seen, report_count, calls_per_hour, status: "flagged"|"watch"|"expired", last_activity, expires_at}`.

## Notes

- The "loss possibly averted" figure is a placeholder (`AVG_LOSS_PREVENTED_INR` in [src/lib/estimate.ts](src/lib/estimate.ts)).
  Replace it with a sourced figure before presenting.
- The crest is a generic badge, not any real state or national emblem. This is a prototype and should not pass for an official system.
- The command wall places cities by latitude and longitude on a plain grid. It deliberately draws no borders.
