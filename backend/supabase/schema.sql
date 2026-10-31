-- Roadly — service communautaire (signalements), Supabase / PostgreSQL.
-- À exécuter dans l'éditeur SQL du projet Supabase. Toutes les règles de points sont côté serveur.

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
create type public.report_kind as enum ('ACCIDENT','JAM','OBSTACLE','CLOSED','ROADWORK','WEATHER','POTHOLE','WORK_LIGHT','HAZARD');

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

create table if not exists public.report_votes (
  report_id uuid not null references public.reports(id) on delete cascade,
  device_id uuid not null references public.profiles(device_id) on delete cascade,
  confirm boolean not null,
  created_at timestamptz not null default now(),
  primary key (report_id, device_id)
);

-- Durées de vie par type (minutes) : courtes par défaut, nid-de-poule plus long.
create or replace function public.report_ttl_minutes(k public.report_kind) returns integer language sql immutable as $$
  select case k
    when 'JAM' then 30 when 'ACCIDENT' then 60 when 'OBSTACLE' then 60 when 'HAZARD' then 60
    when 'WEATHER' then 120 when 'CLOSED' then 240 when 'ROADWORK' then 480 when 'WORK_LIGHT' then 480
    when 'POTHOLE' then 4320 end
$$;

-- Création : le client ne fixe jamais expires_at ni les compteurs ; anti-spam = 1 signalement / 60 s / appareil.
create or replace function public.create_report(p_kind public.report_kind, p_lat double precision, p_lon double precision, p_heading smallint, p_device uuid)
returns public.reports language plpgsql security definer as $$
declare r public.reports;
begin
  insert into public.profiles(device_id) values (p_device) on conflict (device_id) do update set last_seen_at = now();
  if exists (select 1 from public.reports where device_id = p_device and created_at > now() - interval '60 seconds') then
    raise exception 'rate_limited' using errcode = 'P0001';
  end if;
  insert into public.reports(kind, lat, lon, heading, device_id, expires_at)
    values (p_kind, p_lat, p_lon, p_heading, p_device, now() + make_interval(mins => public.report_ttl_minutes(p_kind)))
    returning * into r;
  update public.profiles set points = points + 2 where device_id = p_device;
  return r;
end $$;

-- Vote : une voix par appareil ; 3 démentis sans confirmation désactivent ; une confirmation prolonge de 15 min
-- et crédite 1 point à l'auteur (jamais au votant : pas de triche par auto-confirmation, device différent exigé).
create or replace function public.vote_report(p_report uuid, p_confirm boolean, p_device uuid)
returns public.reports language plpgsql security definer as $$
declare r public.reports;
begin
  insert into public.profiles(device_id) values (p_device) on conflict (device_id) do update set last_seen_at = now();
  select * into r from public.reports where id = p_report and active;
  if r.id is null then raise exception 'not_found' using errcode = 'P0002'; end if;
  if r.device_id = p_device then raise exception 'own_report' using errcode = 'P0003'; end if;
  insert into public.report_votes(report_id, device_id, confirm) values (p_report, p_device, p_confirm)
    on conflict (report_id, device_id) do update set confirm = excluded.confirm, created_at = now();
  update public.reports set
    confirmations = (select count(*) from public.report_votes v where v.report_id = p_report and v.confirm),
    denials = (select count(*) from public.report_votes v where v.report_id = p_report and not v.confirm)
    where id = p_report;
  select * into r from public.reports where id = p_report;
  if p_confirm then
    update public.reports set expires_at = greatest(expires_at, now() + interval '15 minutes') where id = p_report;
    update public.profiles set points = points + 1 where device_id = r.device_id;
  elsif r.denials >= 3 and r.confirmations = 0 then
    update public.reports set active = false where id = p_report;
  end if;
  select * into r from public.reports where id = p_report;
  return r;
end $$;

-- Lecture : signalements actifs dans une boîte (lat/lon), expirés exclus même avant le passage du nettoyeur.
create or replace function public.reports_in_box(south double precision, west double precision, north double precision, east double precision)
returns setof public.reports language sql stable as $$
  select * from public.reports
  where active and expires_at > now() and lat between south and north and lon between west and east
  order by created_at desc limit 200
$$;

-- Expiration périodique (pg_cron, extension à activer dans le tableau de bord) : toutes les 5 min.
create or replace function public.expire_reports() returns void language sql as $$
  update public.reports set active = false where active and expires_at <= now();
$$;
-- select cron.schedule('roadly-expire', '*/5 * * * *', $$select public.expire_reports()$$);

-- RLS : tout passe par les fonctions (security definer) ; lecture directe interdite au rôle anon.
alter table public.profiles enable row level security;
alter table public.reports enable row level security;
alter table public.report_votes enable row level security;
revoke all on public.profiles, public.reports, public.report_votes from anon;
grant execute on function public.create_report, public.vote_report, public.reports_in_box to anon;
