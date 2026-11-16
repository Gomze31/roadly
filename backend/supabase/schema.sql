-- Roadly — service communautaire (signalements), Supabase / PostgreSQL.
-- À exécuter dans l'éditeur SQL du projet Supabase. Toutes les règles de points sont côté serveur.
-- Ré-exécutable : sur une installation existante, il met à jour les fonctions et ajoute les protections (2026-11-16).
--
-- Modèle de menace (audit 2026-11-16) : n'importe qui peut appeler les fonctions avec la clé anon publique et inventer
-- autant d'identifiants d'appareil qu'il veut. Protections :
--   * l'identifiant d'appareil n'est jamais renvoyé (il sert de secret : le connaître permettait d'agir au nom d'un autre) ;
--   * limites par adresse IP (empreinte HMAC salée, gardée 24 h, jamais l'adresse elle-même) en plus des limites par appareil :
--     création d'appareils, signalements, votes ;
--   * un appareil créé depuis moins d'une heure peut voter, mais son vote ne crédite pas de point et ne compte pas pour
--     désactiver un signalement (anti-comptes jetables) ;
--   * lecture limitée à une boîte de 1° de côté (pas d'aspiration de tous les signalements) ;
--   * fonctions security definer avec search_path fixé ; exécution retirée à PUBLIC, accordée à anon fonction par fonction.

create extension if not exists pgcrypto;

-- Pseudonymes d'appareil : aucune donnée personnelle, un UUID généré sur le téléphone.
create table if not exists public.profiles (
  device_id uuid primary key,
  pseudonym text not null default ('conducteur-' || substr(gen_random_uuid()::text, 1, 6)),
  points integer not null default 0,
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

-- Types alignés sur fr.roadly.core.designsystem.component.ReportKind.
do $$ begin
  create type public.report_kind as enum ('ACCIDENT','JAM','OBSTACLE','CLOSED','ROADWORK','WEATHER','POTHOLE','WORK_LIGHT','HAZARD');
exception when duplicate_object then null; end $$;

create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  kind public.report_kind not null,
  lat double precision not null check (lat between -90 and 90),
  lon double precision not null check (lon between -180 and 180),
  heading smallint,
  device_id uuid not null references public.profiles(device_id) on delete cascade,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null,
  confirmations integer not null default 0,
  denials integer not null default 0,
  active boolean not null default true
);
create index if not exists reports_active_idx on public.reports (active, expires_at);
create index if not exists reports_geo_idx on public.reports (lat, lon) where active;
create index if not exists reports_device_idx on public.reports (device_id, created_at);

create table if not exists public.report_votes (
  report_id uuid not null references public.reports(id) on delete cascade,
  device_id uuid not null references public.profiles(device_id) on delete cascade,
  confirm boolean not null,
  created_at timestamptz not null default now(),
  primary key (report_id, device_id)
);
-- Vote « de confiance » : émis par un appareil de plus d'une heure (seuls ceux-là créditent ou désactivent).
alter table public.report_votes add column if not exists trusted boolean not null default true;
create index if not exists report_votes_device_idx on public.report_votes (device_id, created_at);

-- Sel du HMAC des adresses IP, tiré au hasard à l'installation, jamais exposé.
create table if not exists public.app_secrets (name text primary key, value bytea not null);
insert into public.app_secrets(name, value) values ('ip_salt', gen_random_bytes(32)) on conflict (name) do nothing;

-- Événements pour les limites par IP (empreinte seulement), purgés après 24 h.
create table if not exists public.rate_events (
  ip_hash text not null,
  action text not null,
  at timestamptz not null default now()
);
create index if not exists rate_events_idx on public.rate_events (ip_hash, action, at);

-- Signalement tel que le voient les clients : sans l'identifiant d'appareil de l'auteur, avec « mine » pour le demandeur.
do $$ begin
  create type public.report_public as (
    id uuid, kind public.report_kind, lat double precision, lon double precision, heading smallint,
    created_at timestamptz, expires_at timestamptz, confirmations integer, denials integer, active boolean, mine boolean
  );
exception when duplicate_object then null; end $$;

-- Durées de vie par type (minutes) : courtes par défaut, nid-de-poule plus long.
create or replace function public.report_ttl_minutes(k public.report_kind) returns integer language sql immutable
set search_path = public, extensions, pg_temp as $$
  select case k
    when 'JAM' then 30 when 'ACCIDENT' then 60 when 'OBSTACLE' then 60 when 'HAZARD' then 60
    when 'WEATHER' then 120 when 'CLOSED' then 240 when 'ROADWORK' then 480 when 'WORK_LIGHT' then 480
    when 'POTHOLE' then 4320 end
$$;

-- Empreinte de l'adresse IP de l'appel (en-tête x-forwarded-for transmis par Supabase à PostgREST) ; null hors PostgREST.
create or replace function public.client_ip_hash() returns text language plpgsql stable security definer
set search_path = public, extensions, pg_temp as $$
declare h json; ip text;
begin
  begin h := current_setting('request.headers', true)::json; exception when others then h := null; end;
  -- cf-connecting-ip est posé par Cloudflare (devant Supabase) et ne peut pas être fourni par le client ; x-forwarded-for
  -- peut commencer par une valeur choisie par le client : il ne sert qu'en dernier recours (vérifier sur son déploiement).
  ip := nullif(trim(coalesce(h->>'cf-connecting-ip', h->>'x-real-ip', split_part(coalesce(h->>'x-forwarded-for', ''), ',', 1))), '');
  if ip is null then return null; end if;
  return encode(hmac(convert_to(ip, 'UTF8'), (select value from public.app_secrets where name = 'ip_salt'), 'sha256'), 'hex');
end $$;

-- Lève « rate_limited » si l'IP a déjà fait [max_count] fois [act] dans [win] ; sinon enregistre l'événement.
create or replace function public.check_ip_rate(act text, max_count integer, win interval) returns void language plpgsql security definer
set search_path = public, extensions, pg_temp as $$
declare h text := public.client_ip_hash();
begin
  if h is null then return; end if;
  if (select count(*) from public.rate_events where ip_hash = h and action = act and at > now() - win) >= max_count then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;
  insert into public.rate_events(ip_hash, action) values (h, act);
end $$;

-- Profil de l'appareil : créé au premier appel (au plus 5 nouveaux appareils par IP et par jour), puis mis à jour.
create or replace function public.touch_profile(p_device uuid) returns public.profiles language plpgsql security definer
set search_path = public, extensions, pg_temp as $$
declare r public.profiles;
begin
  if p_device is null then raise exception 'invalid_device' using errcode = 'P0004'; end if;
  update public.profiles set last_seen_at = now() where device_id = p_device returning * into r;
  if r.device_id is null then
    perform public.check_ip_rate('new_device', 5, interval '1 day');
    insert into public.profiles(device_id) values (p_device) on conflict (device_id) do update set last_seen_at = now() returning * into r;
  end if;
  return r;
end $$;

create or replace function public.to_public(r public.reports, p_device uuid) returns public.report_public language sql stable
set search_path = public, extensions, pg_temp as $$
  select (r.id, r.kind, r.lat, r.lon, r.heading, r.created_at, r.expires_at, r.confirmations, r.denials, r.active,
          p_device is not null and r.device_id = p_device)::public.report_public
$$;

-- Les anciennes signatures renvoyaient public.reports (avec device_id) : elles sont remplacées.
drop function if exists public.create_report(public.report_kind, double precision, double precision, smallint, uuid);
drop function if exists public.vote_report(uuid, boolean, uuid);
drop function if exists public.reports_in_box(double precision, double precision, double precision, double precision);

-- Création : le client ne fixe jamais expires_at ni les compteurs ; 1 signalement / 60 s / appareil, et par IP
-- 1 / 15 s et 30 / heure (adresses partagées des opérateurs mobiles : limites volontairement larges).
create or replace function public.create_report(p_kind public.report_kind, p_lat double precision, p_lon double precision, p_heading smallint, p_device uuid)
returns public.report_public language plpgsql security definer
set search_path = public, extensions, pg_temp as $$
declare r public.reports;
begin
  perform public.touch_profile(p_device);
  if exists (select 1 from public.reports where device_id = p_device and created_at > now() - interval '60 seconds') then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;
  perform public.check_ip_rate('report_burst', 1, interval '15 seconds');
  perform public.check_ip_rate('report', 30, interval '1 hour');
  insert into public.reports(kind, lat, lon, heading, device_id, expires_at)
    values (p_kind, p_lat, p_lon, case when p_heading between 0 and 359 then p_heading end, p_device,
            now() + make_interval(mins => public.report_ttl_minutes(p_kind)))
    returning * into r;
  update public.profiles set points = points + 2 where device_id = p_device;
  return public.to_public(r, p_device);
end $$;

-- Vote : une voix par appareil, interdit sur son propre signalement ; 60 votes / heure par appareil et 200 par IP.
-- Seuls les votes d'appareils de plus d'une heure créditent 1 point à l'auteur ou comptent pour désactiver
-- (3 démentis de confiance, aucune confirmation) ; tout vote positif prolonge de 15 min.
create or replace function public.vote_report(p_report uuid, p_confirm boolean, p_device uuid)
returns public.report_public language plpgsql security definer
set search_path = public, extensions, pg_temp as $$
declare r public.reports; voter public.profiles; is_trusted boolean; already boolean;
begin
  voter := public.touch_profile(p_device);
  select * into r from public.reports where id = p_report and active;
  if r.id is null then raise exception 'not_found' using errcode = 'P0002'; end if;
  if r.device_id = p_device then raise exception 'own_report' using errcode = 'P0003'; end if;
  if (select count(*) from public.report_votes where device_id = p_device and created_at > now() - interval '1 hour') >= 60 then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;
  perform public.check_ip_rate('vote', 200, interval '1 hour');
  is_trusted := voter.created_at <= now() - interval '1 hour';
  already := exists (select 1 from public.report_votes where report_id = p_report and device_id = p_device and confirm and trusted);
  insert into public.report_votes(report_id, device_id, confirm, trusted) values (p_report, p_device, p_confirm, is_trusted)
    on conflict (report_id, device_id) do update set confirm = excluded.confirm, trusted = excluded.trusted, created_at = now();
  update public.reports set
    confirmations = (select count(*) from public.report_votes v where v.report_id = p_report and v.confirm),
    denials = (select count(*) from public.report_votes v where v.report_id = p_report and not v.confirm)
    where id = p_report;
  if p_confirm then
    update public.reports set expires_at = greatest(expires_at, now() + interval '15 minutes') where id = p_report;
    -- Un point par confirmeur de confiance, une seule fois (re-voter ne rapporte rien).
    if is_trusted and not already then update public.profiles set points = points + 1 where device_id = r.device_id; end if;
  elsif (select count(*) from public.report_votes v where v.report_id = p_report and not v.confirm and v.trusted) >= 3
        and (select count(*) from public.report_votes v where v.report_id = p_report and v.confirm) = 0 then
    update public.reports set active = false where id = p_report;
  end if;
  select * into r from public.reports where id = p_report;
  return public.to_public(r, p_device);
end $$;

-- Lecture : signalements actifs et non expirés dans une boîte d'au plus 1° de côté (bornée autour de son centre), 200 au plus.
-- [p_device] (facultatif) ne sert qu'à marquer ses propres signalements (« mine »).
create or replace function public.reports_in_box(south double precision, west double precision, north double precision, east double precision, p_device uuid default null)
returns setof public.report_public language plpgsql stable security definer
set search_path = public, extensions, pg_temp as $$
declare clat double precision := (south + north) / 2; clon double precision := (west + east) / 2;
        hlat double precision := least(abs(north - south) / 2, 0.5); hlon double precision := least(abs(east - west) / 2, 0.5);
begin
  return query
    select (public.to_public(r, p_device)).* from public.reports r
    where r.active and r.expires_at > now()
      and r.lat between clat - hlat and clat + hlat and r.lon between clon - hlon and clon + hlon
    order by r.created_at desc limit 200;
end $$;

-- Expiration périodique (pg_cron, extension à activer dans le tableau de bord) : toutes les 5 min ; purge des limites IP.
create or replace function public.expire_reports() returns void language sql security definer
set search_path = public, extensions, pg_temp as $$
  update public.reports set active = false where active and expires_at <= now();
  delete from public.rate_events where at < now() - interval '1 day';
$$;
-- select cron.schedule('roadly-expire', '*/5 * * * *', $$select public.expire_reports()$$);

-- LOT 10 : profil et classement. Points cumulés côté serveur ; classement public par pseudonyme (jamais de position).
create or replace function public.get_profile(p_device uuid)
returns public.profiles language plpgsql security definer
set search_path = public, extensions, pg_temp as $$
begin
  return public.touch_profile(p_device);
end $$;

create or replace function public.leaderboard(p_limit integer default 50)
returns table(pseudonym text, points integer, rank bigint) language sql stable security definer
set search_path = public, extensions, pg_temp as $$
  select p.pseudonym, p.points, rank() over (order by p.points desc) as rank
  from public.profiles p where p.points > 0 order by p.points desc limit least(greatest(coalesce(p_limit, 50), 1), 100)
$$;

-- RLS : tout passe par les fonctions (security definer) ; aucun accès direct aux tables pour anon ni authenticated.
alter table public.profiles enable row level security;
alter table public.reports enable row level security;
alter table public.report_votes enable row level security;
alter table public.app_secrets enable row level security;
alter table public.rate_events enable row level security;
do $$ begin revoke all on public.profiles, public.reports, public.report_votes, public.app_secrets, public.rate_events from anon;
exception when undefined_object then null; end $$;
do $$ begin revoke all on public.profiles, public.reports, public.report_votes, public.app_secrets, public.rate_events from authenticated;
exception when undefined_object then null; end $$;

-- Exécution : retirée à PUBLIC (droit par défaut de PostgreSQL), accordée à anon pour les seules fonctions d'API.
revoke execute on all functions in schema public from public;
do $$ begin execute 'revoke execute on all functions in schema public from anon';
exception when undefined_object then null; end $$;
do $$ begin execute 'revoke execute on all functions in schema public from authenticated';
exception when undefined_object then null; end $$;
grant execute on function public.create_report(public.report_kind, double precision, double precision, smallint, uuid) to anon;
grant execute on function public.vote_report(uuid, boolean, uuid) to anon;
grant execute on function public.reports_in_box(double precision, double precision, double precision, double precision, uuid) to anon;
grant execute on function public.get_profile(uuid) to anon;
grant execute on function public.leaderboard(integer) to anon;
