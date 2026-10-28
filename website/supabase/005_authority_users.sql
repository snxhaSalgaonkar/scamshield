-- RakshaCall · 005 · Promote the two officer accounts to role 'authority'
--
-- FIRST create the users in the Supabase dashboard:
--   Authentication → Users → Add user → Create new user
--   email + password, tick "Auto Confirm User". Do it twice (two officers).
-- THEN replace the two emails below with the ones you used and run this file.

insert into public.profiles (id, role)
select u.id, 'authority'
from auth.users u
where u.email in (
  'walshdsouza10@gmail.com',   -- ← change
  'sohampendse10@gmail.com'    -- ← change
)
on conflict (id) do update set role = 'authority';

-- Should list both emails with role 'authority':
select u.email, p.role
from auth.users u join public.profiles p on p.id = u.id
where p.role = 'authority';
