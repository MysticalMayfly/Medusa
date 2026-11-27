-- Cloud-side profile event processing. Additive migration: legacy anonymous clients
-- remain queued; only an authenticated owner can immediately materialize a new event.
-- Existing queued events are deliberately not replayed by this migration.
begin;
create schema if not exists medusa_internal;
revoke all on schema medusa_internal from public;

alter table public.profile_titles add column if not exists watched_updated_at timestamptz;
alter table public.profile_titles add column if not exists interested_updated_at timestamptz;
alter table public.profile_titles add column if not exists progress_updated_at timestamptz;
-- Before per-field clocks existed, the row's timestamp is the safest available baseline.
update public.profile_titles set
  watched_updated_at = coalesce(watched_updated_at, updated_at),
  interested_updated_at = coalesce(interested_updated_at, updated_at),
  progress_updated_at = coalesce(progress_updated_at, updated_at)
where watched_updated_at is null or interested_updated_at is null or progress_updated_at is null;

create or replace function medusa_internal.stamp_direct_profile_title()
returns trigger language plpgsql security definer set search_path = pg_catalog, public as $$
begin
  if coalesce(current_setting('medusa.processing_event', true), '') = '1' then return new; end if;
  if tg_op = 'INSERT' then
    new.watched_updated_at := coalesce(new.watched_updated_at, clock_timestamp());
    new.interested_updated_at := coalesce(new.interested_updated_at, clock_timestamp());
    new.progress_updated_at := coalesce(new.progress_updated_at, clock_timestamp());
  else
    if (new.watched, new.watched_at) is distinct from (old.watched, old.watched_at) then
      new.watched_updated_at := greatest(coalesce(old.watched_updated_at, '-infinity'::timestamptz), clock_timestamp());
    else new.watched_updated_at := old.watched_updated_at; end if;
    if new.interested is distinct from old.interested then
      new.interested_updated_at := greatest(coalesce(old.interested_updated_at, '-infinity'::timestamptz), clock_timestamp());
    else new.interested_updated_at := old.interested_updated_at; end if;
    if (new.prog_season, new.prog_episode, new.prog_at) is distinct from (old.prog_season, old.prog_episode, old.prog_at) then
      new.progress_updated_at := greatest(coalesce(old.progress_updated_at, '-infinity'::timestamptz), clock_timestamp());
    else new.progress_updated_at := old.progress_updated_at; end if;
  end if;
  new.updated_at := greatest(new.updated_at, new.watched_updated_at, new.interested_updated_at, new.progress_updated_at);
  return new;
end $$;
drop trigger if exists medusa_stamp_direct_profile_title on public.profile_titles;
create trigger medusa_stamp_direct_profile_title before insert or update on public.profile_titles
for each row execute function medusa_internal.stamp_direct_profile_title();

create or replace function medusa_internal.apply_profile_event(e public.profile_events)
returns void language plpgsql security definer set search_path = pg_catalog, public as $$
begin
  if e.kind = 'profile' then
    insert into public.profiles(id,name,avatar,photo_url,updated_at)
    values(e.profile_id,left(coalesce(nullif(e.value->>'name',''),'Profile'),20),
      coalesce((e.value->>'avatar')::integer,0),e.value->>'photo_url',e.created_at)
    on conflict(id) do update set name=excluded.name,avatar=excluded.avatar,
      photo_url=excluded.photo_url,updated_at=excluded.updated_at
      where public.profiles.updated_at < excluded.updated_at;
    return;
  end if;
  if e.kind not in ('watched','interested','progress') or e.title_id is null then
    raise exception 'Unsupported profile event';
  end if;
  if not exists(select 1 from public.profiles where id=e.profile_id) then
    raise exception 'Profile must exist before recording title changes';
  end if;
  perform set_config('medusa.processing_event','1',true);
  insert into public.profile_titles(profile_id,title_id,updated_at)
    values(e.profile_id,e.title_id,e.created_at) on conflict(profile_id,title_id) do nothing;
  if e.kind = 'watched' then
    update public.profile_titles set watched=(e.value->>'on')::boolean,
      watched_at=case when (e.value->>'on')::boolean then nullif(e.value->>'at','')::timestamptz else null end,
      watched_updated_at=e.created_at,updated_at=greatest(updated_at,e.created_at)
    where profile_id=e.profile_id and title_id=e.title_id and (watched_updated_at is null or watched_updated_at < e.created_at);
  elsif e.kind = 'interested' then
    update public.profile_titles set interested=(e.value->>'on')::boolean,
      interested_updated_at=e.created_at,updated_at=greatest(updated_at,e.created_at)
    where profile_id=e.profile_id and title_id=e.title_id and (interested_updated_at is null or interested_updated_at < e.created_at);
  else
    update public.profile_titles set prog_season=(e.value->>'season')::integer,
      prog_episode=(e.value->>'episode')::integer,prog_at=nullif(e.value->>'at','')::timestamptz,
      progress_updated_at=e.created_at,updated_at=greatest(updated_at,e.created_at)
    where profile_id=e.profile_id and title_id=e.title_id and (progress_updated_at is null or progress_updated_at < e.created_at);
  end if;
  perform set_config('medusa.processing_event','0',true);
end $$;
revoke all on function medusa_internal.apply_profile_event(public.profile_events) from public;

create or replace function medusa_internal.process_owned_profile_event()
returns trigger language plpgsql security definer set search_path = pg_catalog, public as $$
begin
  -- Older builds send anonymously. Keep those events queued, never run them with elevated access.
  if auth.uid() is null then
    if auth.role() = 'service_role' and exists(select 1 from public.account_profile_links where profile_id=new.profile_id) then
      perform medusa_internal.apply_profile_event(new);
    end if;
    return new;
  end if;
  if not exists(select 1 from public.account_profile_links where user_id=auth.uid() and profile_id=new.profile_id) then
    raise exception 'Account does not own this profile';
  end if;
  perform medusa_internal.apply_profile_event(new);
  return new;
end $$;
drop trigger if exists medusa_process_owned_profile_event on public.profile_events;
create trigger medusa_process_owned_profile_event after insert on public.profile_events
for each row execute function medusa_internal.process_owned_profile_event();

grant insert on public.profile_events to authenticated;
grant select on public.profiles, public.profile_titles to authenticated;
grant usage on sequence public.profile_events_id_seq to authenticated;
drop policy if exists "read owned profiles" on public.profiles;
create policy "read owned profiles" on public.profiles for select to authenticated
using (exists(select 1 from public.account_profile_links l where l.user_id=(select auth.uid()) and l.profile_id=profiles.id));
drop policy if exists "read owned profile titles" on public.profile_titles;
create policy "read owned profile titles" on public.profile_titles for select to authenticated
using (exists(select 1 from public.account_profile_links l where l.user_id=(select auth.uid()) and l.profile_id=profile_titles.profile_id));
drop policy if exists "send owned profile events" on public.profile_events;
create policy "send owned profile events" on public.profile_events for insert to authenticated
with check (exists(select 1 from public.account_profile_links l where l.user_id=(select auth.uid()) and l.profile_id=profile_events.profile_id));
commit;
