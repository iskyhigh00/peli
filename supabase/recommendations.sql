-- Peli: recomendar una película a un amigo (con cuenta).
-- Pegar completo en Supabase > SQL Editor > Run (después de accounts.sql; no borra nada de lo anterior).

create table if not exists public.recommendations (
  id          bigint generated always as identity primary key,
  from_user   uuid    not null references auth.users(id) on delete cascade,
  to_user     uuid    not null references auth.users(id) on delete cascade,
  movie_id    integer not null,
  title       text    not null default '',
  poster_path text,
  year        text    not null default '',
  created_at  timestamptz not null default now(),
  dismissed   boolean not null default false,
  unique (from_user, to_user, movie_id)
);
alter table public.recommendations enable row level security;
revoke all on public.recommendations from anon, authenticated;

-- Recomendar a un amigo aceptado (si ya se la había recomendado, vuelve a aparecer).
create or replace function public.recommend_movie(p_friend uuid, p_movie integer, p_title text, p_poster text, p_year text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'no_session'; end if;
  if not public.is_friend(p_friend) then raise exception 'not_friends'; end if;
  insert into public.recommendations (from_user, to_user, movie_id, title, poster_path, year)
  values (auth.uid(), p_friend, p_movie, left(coalesce(p_title, ''), 200), p_poster, left(coalesce(p_year, ''), 10))
  on conflict (from_user, to_user, movie_id)
  do update set dismissed = false, created_at = now(), title = excluded.title, poster_path = excluded.poster_path, year = excluded.year;
end;
$$;

-- Lo que me recomendaron y aún no descarté (con el nombre de quien lo recomendó).
create or replace function public.my_recommendations()
returns table(id bigint, from_name text, movie_id integer, title text, poster_path text, year text)
language sql stable security definer set search_path = public as $$
  select r.id, p.username, r.movie_id, r.title, r.poster_path, r.year
  from public.recommendations r
  join public.profiles p on p.id = r.from_user
  where r.to_user = auth.uid() and not r.dismissed and public.is_friend(r.from_user)
  order by r.created_at desc
  limit 50;
$$;

create or replace function public.dismiss_recommendation(p_id bigint) returns void
language sql security definer set search_path = public as $$
  update public.recommendations set dismissed = true where id = p_id and to_user = auth.uid();
$$;

revoke all on function public.recommend_movie(uuid, integer, text, text, text), public.my_recommendations(),
  public.dismiss_recommendation(bigint) from public, anon;
grant execute on function public.recommend_movie(uuid, integer, text, text, text), public.my_recommendations(),
  public.dismiss_recommendation(bigint) to authenticated;
