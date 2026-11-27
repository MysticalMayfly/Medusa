-- Apply after updated clients use account tokens. Older anonymous builds must update.
begin;
drop policy if exists "read profiles" on public.profiles;
drop policy if exists "read profile titles" on public.profile_titles;
drop policy if exists "send events" on public.profile_events;
revoke select on public.profiles,public.profile_titles from anon;
revoke insert on public.profile_events from anon;
commit;
