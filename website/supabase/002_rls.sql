-- RakshaCall · 002 · Row-level security
-- Policies exactly as SHARED_CONTRACT.md §5 (RLS table). Run after 001_schema.sql.
--
--   table          citizen (anonymous sign-in)                         authority
--   reports        INSERT own device_id; SELECT own;                   SELECT all; UPDATE status only
--                  user_feedback only via mark_report_safe() RPC
--   numbers        no direct access (use get_number_reputation RPC)    SELECT all
--   number_series  SELECT                                              SELECT
--   profiles       SELECT / INSERT own (role 'citizen' only) /         SELECT own
--                  UPDATE own device_id (only while it is still empty)

-- ---------- helpers ----------
-- SECURITY DEFINER so they can read profiles without tripping profiles' own RLS.

create or replace function public.is_authority()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.role = 'authority'
  );
$$;

create or replace function public.my_device_id()
returns text
language sql
stable
security definer
set search_path = ''
as $$
  select p.device_id from public.profiles p where p.id = auth.uid();
$$;

revoke all on function public.is_authority() from public;
revoke all on function public.my_device_id() from public;
grant execute on function public.is_authority() to authenticated;
grant execute on function public.my_device_id() to authenticated;

-- ---------- enable RLS ----------
alter table public.profiles      enable row level security;
alter table public.reports       enable row level security;
alter table public.numbers       enable row level security;
alter table public.number_series enable row level security;

-- ---------- table / column privileges ----------
-- Supabase grants everything to anon/authenticated by default. Narrow it down so
-- RLS is not the only line of defence (e.g. authority can update `status` and nothing else).
revoke all on public.profiles, public.reports, public.numbers, public.number_series from anon, authenticated;

grant select, insert            on public.profiles      to authenticated;
grant update (device_id)        on public.profiles      to authenticated;
grant select, insert            on public.reports       to authenticated;
grant update (status)           on public.reports       to authenticated;
grant select                    on public.numbers       to authenticated;
grant select                    on public.number_series to authenticated;

-- ---------- profiles ----------
drop policy if exists "profiles: select own" on public.profiles;
create policy "profiles: select own" on public.profiles
  for select to authenticated
  using (id = (select auth.uid()));

drop policy if exists "profiles: insert own citizen" on public.profiles;
create policy "profiles: insert own citizen" on public.profiles
  for insert to authenticated
  with check (id = (select auth.uid()) and role = 'citizen');

-- A citizen may set their device_id once (if the profile was created without one).
-- Changing it later would let one install pose as many devices and poison reputation.
drop policy if exists "profiles: set own device_id once" on public.profiles;
create policy "profiles: set own device_id once" on public.profiles
  for update to authenticated
  using (id = (select auth.uid()) and role = 'citizen' and device_id is null)
  with check (id = (select auth.uid()) and role = 'citizen');

-- ---------- reports ----------
drop policy if exists "reports: citizen insert own device" on public.reports;
create policy "reports: citizen insert own device" on public.reports
  for insert to authenticated
  with check (
    device_id = (select public.my_device_id())
    and status = 'new'
    and user_feedback is null
  );

drop policy if exists "reports: citizen select own" on public.reports;
create policy "reports: citizen select own" on public.reports
  for select to authenticated
  using (device_id = (select public.my_device_id()));

drop policy if exists "reports: authority select all" on public.reports;
create policy "reports: authority select all" on public.reports
  for select to authenticated
  using ((select public.is_authority()));

-- Column grant above limits this to the `status` column.
drop policy if exists "reports: authority update status" on public.reports;
create policy "reports: authority update status" on public.reports
  for update to authenticated
  using ((select public.is_authority()))
  with check ((select public.is_authority()));

-- ---------- numbers ----------
drop policy if exists "numbers: authority select" on public.numbers;
create policy "numbers: authority select" on public.numbers
  for select to authenticated
  using ((select public.is_authority()));

-- ---------- number_series ----------
drop policy if exists "number_series: read" on public.number_series;
create policy "number_series: read" on public.number_series
  for select to authenticated
  using (true);
