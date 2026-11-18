-- Peli: compartir con un amigo la lista de películas de tu servidor (solo los números de TMDB, sin claves ni direcciones).
-- Pegar completo en Supabase > SQL Editor > Run (después de accounts.sql; no borra nada de lo anterior).
-- Quien tiene el servidor la sube; su amigo (ya aceptado) la baja y las ve como «Disponible» sin tener acceso al servidor.

create table if not exists public.shared_library (
  owner      uuid primary key references auth.users(id) on delete cascade,
  tmdb_ids   integer[] not null default '{}',
  updated_at timestamptz not null default now()
);
alter table public.shared_library enable row level security;
revoke all on public.shared_library from anon, authenticated;

-- Guarda (reemplaza) mi lista. Máximo 20 000 películas.
create or replace function public.set_library(p_ids integer[])
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'no_session'; end if;
  if coalesce(array_length(p_ids, 1), 0) > 20000 then raise exception 'too_many'; end if;
  insert into public.shared_library (owner, tmdb_ids)
  values (auth.uid(), coalesce(p_ids, '{}'))
  on conflict (owner) do update set tmdb_ids = excluded.tmdb_ids, updated_at = now();
end;
$$;

-- La lista de un amigo aceptado.
create or replace function public.get_library(p_friend uuid)
returns table(tmdb_ids integer[], updated_at timestamptz)
language sql stable security definer set search_path = public as $$
  select l.tmdb_ids, l.updated_at
  from public.shared_library l
  where l.owner = p_friend and public.is_friend(p_friend);
$$;

revoke all on function public.set_library(integer[]), public.get_library(uuid) from public, anon;
grant execute on function public.set_library(integer[]), public.get_library(uuid) to authenticated;
