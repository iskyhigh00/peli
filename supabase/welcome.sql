-- Peli: mensaje de bienvenida compartido entre dos amigos (con cuenta).
-- Pegar completo en Supabase > SQL Editor > Run (después de accounts.sql; no borra nada de lo anterior).
-- Hay un solo mensaje por pareja de amigos: quien lo guarda es el autor, y lo ve la otra persona al abrir la app.

create table if not exists public.welcome_messages (
  user_a     uuid not null references auth.users(id) on delete cascade,
  user_b     uuid not null references auth.users(id) on delete cascade,
  author     uuid not null references auth.users(id) on delete cascade,
  body       text not null default '',
  updated_at timestamptz not null default now(),
  primary key (user_a, user_b),
  check (user_a < user_b)
);
alter table public.welcome_messages enable row level security;
revoke all on public.welcome_messages from anon, authenticated;

-- Guarda (o reemplaza) el mensaje con un amigo aceptado.
create or replace function public.set_welcome(p_friend uuid, p_body text)
returns void language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'no_session'; end if;
  if not public.is_friend(p_friend) then raise exception 'not_friends'; end if;
  insert into public.welcome_messages (user_a, user_b, author, body)
  values (least(auth.uid(), p_friend), greatest(auth.uid(), p_friend), auth.uid(), left(coalesce(p_body, ''), 140))
  on conflict (user_a, user_b)
  do update set author = auth.uid(), body = excluded.body, updated_at = now();
end;
$$;

-- El mensaje con ese amigo (mine = true si lo escribí yo).
create or replace function public.get_welcome(p_friend uuid)
returns table(body text, mine boolean, updated_at timestamptz)
language sql stable security definer set search_path = public as $$
  select w.body, w.author = auth.uid(), w.updated_at
  from public.welcome_messages w
  where w.user_a = least(auth.uid(), p_friend) and w.user_b = greatest(auth.uid(), p_friend)
    and public.is_friend(p_friend);
$$;

revoke all on function public.set_welcome(uuid, text), public.get_welcome(uuid) from public, anon;
grant execute on function public.set_welcome(uuid, text), public.get_welcome(uuid) to authenticated;
