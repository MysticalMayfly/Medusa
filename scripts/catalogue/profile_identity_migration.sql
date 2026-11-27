-- One-time migration of the linked legacy p1 account to the canonical Google ID.
-- All profile rows, history and queued events are preserved; no records are deleted.
begin;
lock table public.profiles, public.profile_titles, public.account_profile_links, public.profile_events in access exclusive mode;
do $$ declare target_id text; begin
  select 'g' || replace(user_id::text,'-','') into target_id from public.account_profile_links where profile_id='p1';
  if target_id is null then raise exception 'The legacy profile is not linked to an account'; end if;
  if not exists(select 1 from public.profiles where id='p1') then raise exception 'The legacy profile is missing'; end if;
  if exists(select 1 from public.profiles where id=target_id) then raise exception 'The destination profile already exists'; end if;
end $$;
-- Include legacy events received before the lock. Replaying is idempotent and no event is removed.
do $$ declare item public.profile_events; begin
  for item in select e.* from public.profile_events e
    join public.account_profile_links l on l.profile_id=e.profile_id order by e.created_at,e.id
  loop
    perform medusa_internal.apply_profile_event(item);
  end loop;
end $$;
alter table public.profile_titles drop constraint profile_titles_profile_id_fkey;
alter table public.profile_titles add constraint profile_titles_profile_id_fkey foreign key(profile_id)
  references public.profiles(id) on update cascade on delete cascade;
create table if not exists medusa_internal.profile_aliases(old_id text primary key, new_id text not null);
create or replace function medusa_internal.map_legacy_profile_event()
returns trigger language plpgsql security definer set search_path=pg_catalog,public as $$
declare target_id text; begin
  select a.new_id into target_id from medusa_internal.profile_aliases a where a.old_id=new.profile_id;
  if target_id is not null then new.profile_id:=target_id; end if;
  return new;
end $$;
drop trigger if exists medusa_map_legacy_profile_event on public.profile_events;
create trigger medusa_map_legacy_profile_event before insert on public.profile_events
for each row execute function medusa_internal.map_legacy_profile_event();
do $$ declare target_id text; begin
  select 'g' || replace(user_id::text,'-','') into target_id from public.account_profile_links where profile_id='p1';
  insert into medusa_internal.profile_aliases(old_id,new_id) values ('p1',target_id);
  update public.profiles set id=target_id where id='p1';
  update public.account_profile_links set profile_id=target_id where profile_id='p1';
  update public.profile_events set profile_id=target_id where profile_id='p1';
end $$;
commit;
