-- Peli: tabla compartida entre tú y tu pareja. Pegar completo en Supabase > SQL Editor > Run.
create table if not exists public.entries (
  room         text    not null check (length(room) >= 8),
  user_id      text    not null,
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
  primary key (room, user_id, movie_id)
);

-- Si la tabla ya existía de una versión anterior, agrega la columna de la nota propia.
alter table public.entries add column if not exists my_rating integer not null default 0;

alter table public.entries enable row level security;
grant select, insert, update, delete on public.entries to anon;

-- Cada fila solo se puede leer/escribir si la petición trae el header x-room con el código de esa sala.
-- El código (50 bits al azar) es el "secreto" compartido de la pareja.
drop policy if exists "acceso por sala" on public.entries;
create policy "acceso por sala" on public.entries
  for all to anon
  using      (room = coalesce((current_setting('request.headers', true))::json ->> 'x-room', ''))
  with check (room = coalesce((current_setting('request.headers', true))::json ->> 'x-room', ''));
