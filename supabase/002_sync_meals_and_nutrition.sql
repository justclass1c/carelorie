-- Carelorie — make saved meals and full nutrition follow the user to a new device
--
-- Run this ONCE in the Supabase SQL editor (Dashboard -> SQL Editor -> New query -> Run).
--
-- Every statement is idempotent, so running it twice is harmless.
--
-- ============================================================================
-- ORDER MATTERS: run this BEFORE installing the app build that goes with it.
-- ============================================================================
-- Section 1 adds columns that the new build sends on every diary insert. If the app ships first,
-- Postgres rejects each insert with 42703 ("column does not exist") and nothing new can be
-- logged to the server until this has run. Running this file first is always safe: the columns
-- and tables are simply unused by the old build.


-- ============================================================================
-- 1. food_logs — servings and the nutrition breakdown
-- ============================================================================
-- These eight values were device-only, because food_logs had nowhere to put them. The app carried
-- them across a sync by copying them off the row it was replacing, which works on one phone and
-- loses them entirely on a second. Signing in on a new device gave you your diary back with every
-- entry reading "1 serving" and no fibre, sugar, sodium or saturated fat.
-- Deliberately no DEFAULT and no backfill. Adding a column with `default 1` writes that 1 into
-- every existing row, and the phone that logged a two-serving entry still holds the real number —
-- the next sync would pull the server's 1 over the top of it and the count would be gone for good.
-- Leaving them NULL lets the app tell "this predates the column" from "this really is one
-- serving", and keep the local value in the first case.
alter table public.food_logs add column if not exists "quantity"           real;
alter table public.food_logs add column if not exists "brand"              text;
alter table public.food_logs add column if not exists "servingDescription" text;
alter table public.food_logs add column if not exists "fiberGrams"         real;
alter table public.food_logs add column if not exists "sugarGrams"         real;
alter table public.food_logs add column if not exists "saturatedFatGrams"  real;
alter table public.food_logs add column if not exists "sodiumMilligrams"   real;
alter table public.food_logs add column if not exists "nutritionSource"    text;


-- ============================================================================
-- 2. meal_presets — saved meals, which have never left the device
-- ============================================================================
-- Keyed by the app's own localId (a UUID generated on the phone) rather than a serial. That makes
-- syncing a plain upsert: no inserting a row to find out its id and writing that back, which is
-- the round trip that produced duplicate diary entries on the food_logs table.
create table if not exists public.meal_presets (
    "localId"     text primary key,
    "ownerUserId" text not null,
    "name"        text not null,
    "mealType"    text not null,
    "createdAt"   text not null
);

create index if not exists meal_presets_owner_idx
    on public.meal_presets ("ownerUserId");


-- ============================================================================
-- 3. meal_preset_items — the foods inside a saved meal
-- ============================================================================
-- on delete cascade: items are owned by their meal and are never edited on their own, so removing
-- the meal must take them with it. Without this, deleting a saved meal would leave its rows behind
-- forever, since nothing else references them.
create table if not exists public.meal_preset_items (
    "localId"        text primary key,
    "mealPresetId"   text not null references public.meal_presets ("localId") on delete cascade,
    "foodName"       text not null,
    "calories"       integer not null default 0,
    "protein"        real    not null default 0,
    "carbs"          real    not null default 0,
    "fat"            real    not null default 0,
    "quantity"       real    not null default 1,
    "sourcePresetId" text
);

create index if not exists meal_preset_items_meal_idx
    on public.meal_preset_items ("mealPresetId");


-- ============================================================================
-- 4. Permissions for the two new tables
-- ============================================================================
-- The app talks to Supabase with the publishable (anon) key and no Supabase Auth, so these grants
-- are what the other tables in this project already rely on. Matching them keeps the new tables
-- consistent with the rest rather than half-locked in a way that only shows up as a silent
-- failure to sync.
grant select, insert, update, delete on public.meal_presets      to anon;
grant select, insert, update, delete on public.meal_preset_items to anon;


-- ============================================================================
-- 5. Check it worked
-- ============================================================================
-- Run these two after the above. The first should list all eight new food_logs columns; the
-- second should return 0 rather than an error.

-- select column_name from information_schema.columns
--  where table_name = 'food_logs'
--    and column_name in ('quantity','brand','servingDescription','fiberGrams',
--                        'sugarGrams','saturatedFatGrams','sodiumMilligrams','nutritionSource');

-- select count(*) from public.meal_presets;


-- ============================================================================
-- SECURITY — unchanged by this file, still worth reading
-- ============================================================================
-- These two tables are as open as every other table in this project: the anon key ships inside
-- the APK, so anything it can reach is effectively public, and saved meals are now part of that.
-- The fix is the same one described at the bottom of 001_schema_fixes.sql — it needs a code
-- change alongside the SQL, so it is deliberately still not applied here.
