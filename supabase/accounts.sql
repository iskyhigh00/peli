-- Peli: cuentas con Google, nombre de usuario, ID de amigo y amistades.
-- Pegar completo en Supabase > SQL Editor > Run (después de schema.sql; no borra nada de lo anterior).

-- ---------- Perfiles ----------
create table if not exists public.profiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  username    text not null check (username ~ '^[A-Za-z0-9_]{3,20}$'),
  friend_code text not null unique,
  created_at  timestamptz not null default now()
);
create unique index if not exists profiles_username_lower on public.profiles (lower(username));

alter table public.profiles enable row level security;
revoke all on public.profiles from anon;
grant select on public.profiles to authenticated;
drop policy if exists "ver mi perfil" on public.profiles;
create policy "ver mi perfil" on public.profiles for select to authenticated using (id = auth.uid());

-- ---------- Amistades (solo se tocan con las funciones de abajo) ----------
create table if not exists public.friendships (
  requester  uuid not null references auth.users(id) on delete cascade,
  addressee  uuid not null references auth.users(id) on delete cascade,
  status     text not null default 'pending' check (status in ('pending', 'accepted')),
  created_at timestamptz not null default now(),
  primary key (requester, addressee),
  check (requester <> addressee)
);
alter table public.friendships enable row level security;
revoke all on public.friendships from anon, authenticated;

-- ---------- Películas marcadas por cuenta ----------
create table if not exists public.user_entries (
  user_id      uuid    not null references auth.users(id) on delete cascade,
  movie_id     integer not null,
  title        text    not null default '',
  overview     text    not null default '',
  poster_path  text,
  year         text    not null default '',
  release_date text,
  liked        boolean not null default false,
  seen         boolean not null default false,
  disliked     boolean not null default false,
  watchlist    boolean not null default false,
  genres       text,
  rating       double precision,
  updated_at   bigint  not null default 0,
  my_rating    integer not null default 0,
  primary key (user_id, movie_id)
);

-- ¿Soy amigo (aceptado) de esa persona?
create or replace function public.is_friend(other uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.friendships f
    where f.status = 'accepted'
      and ((f.requester = auth.uid() and f.addressee = other)
        or (f.addressee = auth.uid() and f.requester = other))
  );
$$;
revoke all on function public.is_friend(uuid) from public, anon;
grant execute on function public.is_friend(uuid) to authenticated;

alter table public.user_entries enable row level security;
revoke all on public.user_entries from anon;
grant select, insert, update, delete on public.user_entries to authenticated;
drop policy if exists "ver lo mio y lo de mis amigos" on public.user_entries;
create policy "ver lo mio y lo de mis amigos" on public.user_entries
  for select to authenticated using (user_id = auth.uid() or public.is_friend(user_id));
drop policy if exists "escribir lo mio" on public.user_entries;
create policy "escribir lo mio" on public.user_entries
  for insert to authenticated with check (user_id = auth.uid());
drop policy if exists "actualizar lo mio" on public.user_entries;
create policy "actualizar lo mio" on public.user_entries
  for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists "borrar lo mio" on public.user_entries;
create policy "borrar lo mio" on public.user_entries
  for delete to authenticated using (user_id = auth.uid());

-- ---------- Funciones ----------

-- Mi perfil (null si aún no elegí nombre).
create or replace function public.my_profile() returns table(username text, friend_code text)
language sql stable security definer set search_path = public as $$
  select p.username, p.friend_code from public.profiles p where p.id = auth.uid();
$$;

-- Crea mi perfil con el nombre elegido y genera mi ID de amigo (8 caracteres sin letras ambiguas).
create or replace function public.create_profile(p_username text) returns table(username text, friend_code text)
language plpgsql security definer set search_path = public as $$
declare
  alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  code text;
  tries int := 0;
begin
  if auth.uid() is null then raise exception 'no_session'; end if;
  p_username := btrim(p_username);
  if p_username !~ '^[A-Za-z0-9_]{3,20}$' then raise exception 'invalid_username'; end if;
  if exists (select 1 from public.profiles where id = auth.uid()) then raise exception 'already_exists'; end if;
  if exists (select 1 from public.profiles where lower(profiles.username) = lower(p_username)) then raise exception 'username_taken'; end if;
  loop
    code := '';
    for i in 1..8 loop
      code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
    end loop;
    exit when not exists (select 1 from public.profiles where profiles.friend_code = code);
    tries := tries + 1;
    if tries > 20 then raise exception 'try_again'; end if;
  end loop;
  insert into public.profiles (id, username, friend_code) values (auth.uid(), p_username, code);
  return query select p_username, code;
end;
$$;

-- Pedir amistad con un ID (ABCD-EFGH) o un nombre de usuario (@nombre).
-- Devuelve: sent:<nombre> | accepted:<nombre> | exists:<nombre> | not_found | self
create or replace function public.request_friend(p_query text) returns text
language plpgsql security definer set search_path = public as $$
declare
  q text := btrim(p_query);
  target public.profiles;
  existing public.friendships;
begin
  if auth.uid() is null then raise exception 'no_session'; end if;
  if not exists (select 1 from public.profiles where id = auth.uid()) then raise exception 'no_profile'; end if;
  q := regexp_replace(q, '^@', '');
  select * into target from public.profiles where friend_code = upper(regexp_replace(q, '[\s-]', '', 'g'));
  if not found then
    select * into target from public.profiles where lower(username) = lower(q);
  end if;
  if not found then return 'not_found'; end if;
  if target.id = auth.uid() then return 'self'; end if;

  select * into existing from public.friendships
   where (requester = auth.uid() and addressee = target.id) or (requester = target.id and addressee = auth.uid());
  if found then
    if existing.status = 'accepted' then return 'exists:' || target.username; end if;
    if existing.addressee = auth.uid() then
      update public.friendships set status = 'accepted' where requester = existing.requester and addressee = existing.addressee;
      return 'accepted:' || target.username;
    end if;
    return 'exists:' || target.username;
  end if;
  insert into public.friendships (requester, addressee) values (auth.uid(), target.id);
  return 'sent:' || target.username;
end;
$$;

-- Aceptar una solicitud que me enviaron.
create or replace function public.accept_friend(p_friend uuid) returns void
language sql security definer set search_path = public as $$
  update public.friendships set status = 'accepted'
   where requester = p_friend and addressee = auth.uid() and status = 'pending';
$$;

-- Quitar a un amigo o rechazar/cancelar una solicitud.
create or replace function public.remove_friend(p_friend uuid) returns void
language sql security definer set search_path = public as $$
  delete from public.friendships
   where (requester = auth.uid() and addressee = p_friend) or (requester = p_friend and addressee = auth.uid());
$$;

-- Mis amigos y solicitudes (incoming = me la enviaron a mí).
create or replace function public.my_friends()
returns table(friend_id uuid, username text, status text, incoming boolean)
language sql stable security definer set search_path = public as $$
  select p.id, p.username, f.status, (f.addressee = auth.uid())
  from public.friendships f
  join public.profiles p on p.id = case when f.requester = auth.uid() then f.addressee else f.requester end
  where f.requester = auth.uid() or f.addressee = auth.uid()
  order by f.status desc, p.username;
$$;

revoke all on function public.my_profile(), public.create_profile(text), public.request_friend(text),
  public.accept_friend(uuid), public.remove_friend(uuid), public.my_friends() from public, anon;
grant execute on function public.my_profile(), public.create_profile(text), public.request_friend(text),
  public.accept_friend(uuid), public.remove_friend(uuid), public.my_friends() to authenticated;
