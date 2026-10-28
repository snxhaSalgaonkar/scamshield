-- RakshaCall · 001 · Schema
-- Tables exactly as SHARED_CONTRACT.md §5. Run first, in the Supabase SQL editor.
-- Safe to re-run on an empty project only (it does not drop anything).

-- ===== profiles =====
create table if not exists public.profiles (
  id uuid primary key references auth.users on delete cascade,
  role text not null default 'citizen' check (role in ('citizen','authority')),
  device_id text unique,
  created_at timestamptz default now()
);

-- ===== reports (privacy-safe: NO transcript, NO victim PII) =====
create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  device_id text not null,                       -- opaque random UUID per install
  caller_number text not null,                   -- E.164, e.g. +919876543210
  number_prefix text not null,                   -- first 5 digits of the 10-digit number: +919876543210 → '98765'
  telecom_circle text,                           -- e.g. 'Delhi', 'Maharashtra' (approx., from number_series)
  risk_score int not null check (risk_score between 0 and 100),
  l1_score int not null default 0,
  l2_score int not null default 0,
  l3_score int not null default 0,
  scam_type text not null default 'other',
  matched_phrases jsonb not null default '[]',   -- ["don't tell your family", "digital arrest"] (PII-scrubbed)
  reasons jsonb not null default '[]',           -- ["OTP SMS received during call", "Number reported by 4 devices"]
  similarity jsonb,                              -- {"score":0.83,"matched":"drugs found in your parcel"} or null
  detected_via text[] not null default '{}',     -- {'sms','correlation'}
  action_taken text not null default 'none'
    check (action_taken in ('none','warn','countdown_cancelled','disconnected')),
  user_feedback text check (user_feedback in ('safe')),   -- citizen tapped "This was safe"
  status text not null default 'new'
    check (status in ('new','reviewed','escalated','false_positive')),
  call_started_at timestamptz,
  created_at timestamptz default now()
);
create index if not exists reports_created_at_idx on public.reports (created_at desc);
create index if not exists reports_caller_number_idx on public.reports (caller_number);
create index if not exists reports_number_prefix_idx on public.reports (number_prefix);
-- Extra indexes for the rate-limit trigger and the clusters/prefix lookups.
create index if not exists reports_device_created_idx on public.reports (device_id, created_at desc);
create index if not exists reports_prefix_created_idx on public.reports (number_prefix, created_at desc);

-- ===== numbers (aggregated reputation, maintained by trigger) =====
create table if not exists public.numbers (
  number text primary key,
  number_prefix text not null,
  telecom_circle text,
  first_seen timestamptz default now(),
  last_activity timestamptz default now(),
  report_count int default 0,
  reporter_count int default 0,      -- DISTINCT devices that reported (anti-poisoning)
  safe_count int default 0,          -- distinct devices that marked it safe
  max_risk_score int default 0,
  is_flagged boolean default false   -- true only when reporter_count >= 3 AND reporter_count > safe_count
);
create index if not exists numbers_prefix_idx on public.numbers (number_prefix);
create index if not exists numbers_flagged_idx on public.numbers (is_flagged) where is_flagged;

-- ===== number_series (prefix → operator/circle lookup, seeded) =====
create table if not exists public.number_series (
  prefix text primary key,          -- first 4 digits of the 10-digit number, e.g. '9876' (approx. circle; MNP makes it imperfect)
  operator text,
  telecom_circle text
);
