-- MiniCraft online accounts and developer inbox
-- Run this file in Supabase Dashboard -> SQL Editor.
create extension if not exists pgcrypto;

create table if not exists public.mc_profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique,
  bio text not null default '',
  settings jsonb not null default '{}'::jsonb,
  is_admin boolean not null default false,
  created_at timestamptz not null default now()
);

create table if not exists public.mc_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  subject text not null default 'Сообщение с сайта',
  body text not null check (char_length(body) between 1 and 4000),
  reply text not null default '',
  created_at timestamptz not null default now(),
  replied_at timestamptz
);

alter table public.mc_profiles enable row level security;
alter table public.mc_messages enable row level security;

-- New registrations automatically receive a profile.
create or replace function public.mc_handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  requested_name text;
begin
  requested_name := coalesce(new.raw_user_meta_data ->> 'username', 'Player');
  if requested_name !~ '^[A-Za-z0-9_]{3,20}$' then
    requested_name := 'Player_' || substr(replace(new.id::text, '-', ''), 1, 8);
  end if;
  insert into public.mc_profiles(id, username)
  values (new.id, requested_name)
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists mc_on_auth_user_created on auth.users;
create trigger mc_on_auth_user_created
after insert on auth.users
for each row execute procedure public.mc_handle_new_user();

-- Users can read profiles and update only their own editable profile fields.
drop policy if exists "Profiles are visible to authenticated users" on public.mc_profiles;
create policy "Profiles are visible to authenticated users"
on public.mc_profiles for select to authenticated using (true);

drop policy if exists "Users update own profile" on public.mc_profiles;
create policy "Users update own profile"
on public.mc_profiles for update to authenticated
using (auth.uid() = id) with check (auth.uid() = id);

grant select on public.mc_profiles to authenticated;
grant update (username, bio, settings) on public.mc_profiles to authenticated;
revoke update (is_admin) on public.mc_profiles from authenticated;

-- Players can create and read only their own messages.
drop policy if exists "Players send own messages" on public.mc_messages;
create policy "Players send own messages"
on public.mc_messages for insert to authenticated
with check (auth.uid() = user_id);

drop policy if exists "Players read own messages" on public.mc_messages;
create policy "Players read own messages"
on public.mc_messages for select to authenticated
using (
  auth.uid() = user_id
  or exists (select 1 from public.mc_profiles p where p.id = auth.uid() and p.is_admin)
);

-- Only the administrator can read all messages and write replies.
drop policy if exists "Admin replies to messages" on public.mc_messages;
create policy "Admin replies to messages"
on public.mc_messages for update to authenticated
using (exists (select 1 from public.mc_profiles p where p.id = auth.uid() and p.is_admin))
with check (exists (select 1 from public.mc_profiles p where p.id = auth.uid() and p.is_admin));

grant select, insert, update on public.mc_messages to authenticated;

-- IMPORTANT: after Qushmi creates an account, run this once using the exact
-- email address used for that account. Replace the address before execution:
-- update public.mc_profiles p set is_admin = true
-- from auth.users u where p.id = u.id and lower(u.email) = lower('YOUR_QUSHMI_EMAIL');
