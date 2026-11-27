-- Medusa's central catalogue (Supabase / Postgres). Run once in the project's SQL editor.
-- Written by scripts/catalogue/build_catalogue.py publish (secret key, on the Mac).
-- Read by the TV app through the app_titles view with the publishable key: read-only, catalogue facts only.

create table if not exists public.services (
  code text primary key, name text not null, colour text,
  free boolean not null default false, sort int not null default 0
);

create table if not exists public.platforms (
  slug text primary key, name text not null, colour text, sort int not null default 0
);

create table if not exists public.titles (
  id bigint primary key,                 -- tmdb_id * 2 (+1 for shows): stable across rebuilds
  kind text not null check (kind in ('movie', 'show')),
  title text not null,
  year int,
  original_title text,
  tmdb_id int not null,
  imdb_id text,
  runtime int,
  countries text[] not null default '{}',
  language text,
  imdb_rating real, imdb_votes int, tmdb_rating real, tmdb_votes int, rt_score int,
  horror boolean not null default false,
  genres text[] not null default '{}',
  franchise text,
  overview text, tagline text,
  poster text, backdrop text,
  trailer_key text,
  india_status text not null default 'unknown',   -- streaming, rent, not_yet, unknown
  services text[] not null default '{}',         -- service codes you can stream on in India
  service_names text[] not null default '{}',
  rent_buy text[] not null default '{}',
  watch jsonb not null default '[]',             -- JustWatch offers: code, pkg, name, type, url
  watch_page text,
  seasons int[] not null default '{}',           -- episodes per season (shows)
  networks text[] not null default '{}',
  platform text,                                 -- where a show premiered (platforms.slug)
  tmdb_status text,
  next_ep jsonb, prev_ep jsonb,
  published_at timestamptz not null default now()
);

-- The maintained view the app reads. Computed fields live here, so every screen gets the same numbers.
create or replace view public.app_titles with (security_invoker = on) as
select t.*,
  (t.india_status in ('streaming', 'rent') or cardinality(t.services) > 0 or jsonb_array_length(t.watch) > 0) as avail,
  -- the Mac app's hidden sort score (app/db.py score), never shown
  case when t.rt_score is null and t.imdb_rating is null and t.tmdb_rating is null then null else round(((
      coalesce(t.rt_score * 4.0, 0)
    + coalesce(t.imdb_rating * 10 * (case when coalesce(t.imdb_votes, 0) = 0 then 2 else 4 * least(1, t.imdb_votes / 500.0) end), 0)
    + coalesce(t.tmdb_rating * 10, 0) + 60
  ) / (
      (case when t.rt_score is null then 0 else 4 end)
    + (case when t.imdb_rating is null then 0 when coalesce(t.imdb_votes, 0) = 0 then 2 else 4 * least(1, t.imdb_votes / 500.0) end)
    + (case when t.tmdb_rating is null then 0 else 1 end) + 1
  ))::numeric, 1) end as score
from public.titles t;

alter table public.titles enable row level security;
alter table public.services enable row level security;
alter table public.platforms enable row level security;
drop policy if exists "read catalogue" on public.titles;
drop policy if exists "read services" on public.services;
drop policy if exists "read platforms" on public.platforms;
create policy "read catalogue" on public.titles for select to anon using (true);
create policy "read services" on public.services for select to anon using (true);
create policy "read platforms" on public.platforms for select to anon using (true);
grant select on public.titles, public.app_titles, public.services, public.platforms to anon;

-- ---------------------------------------------------------------- profiles
-- Profiles and title states are cloud-owned. Apply account_profile_links.sql, profile_cloud_sync.sql,
-- and profile_privacy.sql in that order to enable authenticated account-scoped sync.

create table if not exists public.profiles (
  id text primary key, name text not null, avatar int not null default 0, photo_url text,
  updated_at timestamptz not null default now()
);

create table if not exists public.profile_titles (
  profile_id text not null references public.profiles(id) on delete cascade,
  title_id bigint not null,
  watched boolean not null default false, watched_at timestamptz,
  interested boolean not null default false,
  prog_season int, prog_episode int, prog_at timestamptz,
  updated_at timestamptz not null default now(),    -- newest change wins when a device merges
  primary key (profile_id, title_id)
);

create table if not exists public.profile_events (
  id bigserial primary key,
  profile_id text not null,
  kind text not null check (kind in ('profile', 'delete_profile', 'watched', 'interested', 'progress')),
  title_id bigint,
  value jsonb not null default '{}',
  device text,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.profile_titles enable row level security;
alter table public.profile_events enable row level security;
drop policy if exists "read profiles" on public.profiles;
drop policy if exists "read profile titles" on public.profile_titles;
drop policy if exists "send events" on public.profile_events;
revoke select on public.profiles, public.profile_titles from anon;
revoke insert on public.profile_events from anon;
