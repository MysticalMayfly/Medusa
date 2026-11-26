-- Additive bridge: Google/Supabase users own a stable profile link. Existing profile tables and the
-- Mac upkeeper remain unchanged. Legacy profiles are linked by an administrator, never by a client.
create table if not exists public.account_profile_links (
  user_id uuid primary key references auth.users(id) on delete cascade,
  profile_id text not null unique,
  created_at timestamptz not null default now()
);
alter table public.account_profile_links enable row level security;
revoke all on public.account_profile_links from anon, authenticated;
grant select, insert on public.account_profile_links to authenticated;
drop policy if exists "read own account profile" on public.account_profile_links;
create policy "read own account profile" on public.account_profile_links
  for select to authenticated using ((select auth.uid()) = user_id);
drop policy if exists "create own account profile" on public.account_profile_links;
create policy "create own account profile" on public.account_profile_links
  for insert to authenticated with check (
    (select auth.uid()) = user_id
    and profile_id = 'g' || replace((select auth.uid())::text, '-', '')
  );
