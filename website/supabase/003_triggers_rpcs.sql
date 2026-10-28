-- RakshaCall · 003 · Triggers, RPCs, Realtime
-- Run after 002_rls.sql. Safe to re-run (create or replace / drop if exists).
--
-- Trigger functions are SECURITY DEFINER: a citizen has no access to `numbers`
-- and can only see their own reports, but the triggers must read all reports
-- for a number and write the aggregate row.

-- ---------- number helpers ----------

-- '+91 98765-43210', '09876543210', '919876543210', '9876543210' → '+919876543210'.
-- Anything that is not a recognisable Indian mobile is returned with only '+' and digits kept.
create or replace function public.normalize_caller_number(p text)
returns text
language plpgsql
immutable
set search_path = ''
as $$
declare
  d text := regexp_replace(coalesce(p, ''), '\D', '', 'g');
begin
  if length(d) = 10 then return '+91' || d; end if;
  if length(d) = 11 and left(d, 1) = '0' then return '+91' || right(d, 10); end if;
  if length(d) = 12 and left(d, 2) = '91' then return '+' || d; end if;
  if d = '' then return p; end if;
  return '+' || d;
end;
$$;

-- First 5 digits of the 10-digit national number: '+919876543210' → '98765'.
create or replace function public.number_prefix_of(p text)
returns text
language sql
immutable
set search_path = ''
as $$
  select left(right(regexp_replace(coalesce(p, ''), '\D', '', 'g'), 10), 5);
$$;

-- ---------- BEFORE INSERT on reports: rate limit + fill derived fields ----------

create or replace function public.handle_report_before_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  recent int;
begin
  -- Anti-abuse: at most 20 reports per device in any rolling hour.
  select count(*) into recent
  from public.reports r
  where r.device_id = new.device_id
    and r.created_at > now() - interval '1 hour';
  if recent >= 20 then
    raise exception 'rate_limited: device % already sent % reports in the last hour', new.device_id, recent
      using errcode = 'P0001', hint = 'Max 20 reports per device per hour.';
  end if;

  new.caller_number := public.normalize_caller_number(new.caller_number);

  if new.number_prefix is null or btrim(new.number_prefix) = '' then
    new.number_prefix := public.number_prefix_of(new.caller_number);
  end if;

  if new.telecom_circle is null or btrim(new.telecom_circle) = '' then
    select s.telecom_circle into new.telecom_circle
    from public.number_series s
    where s.prefix = left(new.number_prefix, 4);
  end if;

  -- Phones can't backdate reports (would distort spike detection). Seeds/service role can.
  if auth.role() = 'authenticated' or new.created_at is null then
    new.created_at := now();
  end if;

  return new;
end;
$$;

drop trigger if exists reports_before_insert on public.reports;
create trigger reports_before_insert
  before insert on public.reports
  for each row execute function public.handle_report_before_insert();

-- ---------- numbers aggregate ----------

-- Recomputes one numbers row from all of its reports (idempotent, so insert and
-- feedback triggers share it and the counts can never drift).
create or replace function public.refresh_number(p_number text)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.numbers as n (
    number, number_prefix, telecom_circle, first_seen, last_activity,
    report_count, reporter_count, safe_count, max_risk_score, is_flagged
  )
  select
    p_number,
    (array_agg(r.number_prefix order by r.created_at desc))[1],
    (array_agg(r.telecom_circle order by r.created_at desc) filter (where r.telecom_circle is not null))[1],
    min(r.created_at),
    max(r.created_at),
    count(*),
    count(distinct r.device_id),
    count(distinct r.device_id) filter (where r.user_feedback = 'safe'),
    max(r.risk_score),
    count(distinct r.device_id) >= 3
      and count(distinct r.device_id) > count(distinct r.device_id) filter (where r.user_feedback = 'safe')
  from public.reports r
  where r.caller_number = p_number
  having count(*) > 0
  on conflict (number) do update set
    number_prefix  = excluded.number_prefix,
    telecom_circle = coalesce(excluded.telecom_circle, n.telecom_circle),
    first_seen     = excluded.first_seen,
    last_activity  = excluded.last_activity,
    report_count   = excluded.report_count,
    reporter_count = excluded.reporter_count,
    safe_count     = excluded.safe_count,
    max_risk_score = excluded.max_risk_score,
    is_flagged     = excluded.is_flagged;
end;
$$;

create or replace function public.handle_report_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform public.refresh_number(new.caller_number);
  return null;
end;
$$;

drop trigger if exists reports_after_insert on public.reports;
create trigger reports_after_insert
  after insert on public.reports
  for each row execute function public.handle_report_insert();

create or replace function public.handle_report_feedback()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if new.user_feedback is distinct from old.user_feedback then
    perform public.refresh_number(new.caller_number);
  end if;
  return null;
end;
$$;

drop trigger if exists reports_after_feedback on public.reports;
create trigger reports_after_feedback
  after update of user_feedback on public.reports
  for each row execute function public.handle_report_feedback();

revoke all on function public.handle_report_before_insert() from public;
revoke all on function public.handle_report_insert() from public;
revoke all on function public.handle_report_feedback() from public;
revoke all on function public.refresh_number(text) from public, anon, authenticated;

-- ---------- RPCs for the citizen app ----------

-- Pre-ring lookup. Limited fields only, one number per call, no enumeration.
create or replace function public.get_number_reputation(p_number text)
returns json
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
  v_number text := public.normalize_caller_number(p_number);
  v_prefix text := public.number_prefix_of(v_number);
  v_row public.numbers%rowtype;
  v_prefix_count int;
begin
  if auth.uid() is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;

  select * into v_row from public.numbers n where n.number = v_number;

  select count(*) into v_prefix_count
  from public.reports r
  where r.number_prefix = v_prefix
    and r.created_at > now() - interval '7 days';

  return json_build_object(
    'report_count',        coalesce(v_row.report_count, 0),
    'reporter_count',      coalesce(v_row.reporter_count, 0),
    'safe_count',          coalesce(v_row.safe_count, 0),
    'is_flagged',          coalesce(v_row.is_flagged, false),
    'max_risk_score',      coalesce(v_row.max_risk_score, 0),
    'prefix_report_count', v_prefix_count
  );
end;
$$;

-- Citizen tapped "This was safe" on one of their own reports.
create or replace function public.mark_report_safe(p_report_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_device text := public.my_device_id();
begin
  if v_device is null then
    raise exception 'no device registered for this user' using errcode = '42501';
  end if;

  update public.reports r
     set user_feedback = 'safe'
   where r.id = p_report_id
     and r.device_id = v_device;

  if not found then
    raise exception 'report not found or not yours' using errcode = '42501';
  end if;
end;
$$;

revoke all on function public.get_number_reputation(text) from public, anon;
revoke all on function public.mark_report_safe(uuid) from public, anon;
grant execute on function public.get_number_reputation(text) to authenticated;
grant execute on function public.mark_report_safe(uuid) to authenticated;

-- ---------- Realtime ----------
-- Equivalent to Database → Publications → supabase_realtime → toggle reports + numbers.
do $$
begin
  if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
    begin
      alter publication supabase_realtime add table public.reports;
    exception when duplicate_object then null;
    end;
    begin
      alter publication supabase_realtime add table public.numbers;
    exception when duplicate_object then null;
    end;
  end if;
end;
$$;
