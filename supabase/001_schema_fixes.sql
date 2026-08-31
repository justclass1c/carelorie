-- Carelorie — Supabase schema fixes
--
-- Run this ONCE in the Supabase SQL editor (Dashboard -> SQL Editor -> New query -> Run).
--
-- Every statement is idempotent, so running it twice is harmless.
--
-- Each block below was verified against the live database before being written: the columns it
-- adds are the exact ones the app sends and the server currently rejects.


-- ============================================================================
-- 1. food_presets — fixes "saved here but not accepted by the server"
-- ============================================================================
-- Verified missing:  select name,brand from food_presets
--                 -> 42703 "column food_presets.brand does not exist"
--
-- RemoteFoodPreset sends `brand` and `servingDescription` on every insert, so every attempt to
-- save a food to the library fails and the row stays local and unsynced — which is what the red
-- banner on the Foods screen is reporting. The banner is accurate; the table is the problem.
alter table public.food_presets add column if not exists "brand"              text;
alter table public.food_presets add column if not exists "servingDescription" text;


-- ============================================================================
-- 2. profiles — AI advice + onboarding answers
-- ============================================================================
-- Verified missing: weightAdvice, goal, targetWeight, estimatedTdee, onboardingCompletedAt.
--
-- Without these, every profile write fails, which also means the weight-update path fails.
alter table public.profiles add column if not exists "weightAdvice"          text;
alter table public.profiles add column if not exists "everWeighedOver95"     text;
alter table public.profiles add column if not exists "weightTrend"           text;
alter table public.profiles add column if not exists "bodyFatBand"           text;
alter table public.profiles add column if not exists "exerciseFrequency"     text;
alter table public.profiles add column if not exists "activityLevel"         text;
alter table public.profiles add column if not exists "cardioExperience"      text;
alter table public.profiles add column if not exists "goal"                  text;
alter table public.profiles add column if not exists "targetWeight"          real;
alter table public.profiles add column if not exists "dietType"              text;
alter table public.profiles add column if not exists "trainingType"          text;
alter table public.profiles add column if not exists "calorieDistribution"   text;
alter table public.profiles add column if not exists "proteinPreference"     text;
alter table public.profiles add column if not exists "estimatedTdee"         integer;
alter table public.profiles add column if not exists "onboardingCompletedAt" text;


-- ============================================================================
-- 3. users — "Member since"
-- ============================================================================
alter table public.users add column if not exists "createdAt" text;


-- ============================================================================
-- 4. weight — the client cannot touch this table at all
-- ============================================================================
-- Verified:  select * from weight  ->  42501 "permission denied for table weight"
--
-- Weight history is therefore device-only today: it never reaches the server, so it does not
-- follow a user to a new phone or a reinstall. These grants bring it in line with the other
-- tables the app already uses.
grant select, insert, update, delete on public.weight to anon;
grant usage, select on all sequences in schema public to anon;


-- ============================================================================
-- 5. Duplicate food logs — belt and braces
-- ============================================================================
-- The app had a race that could insert the same diary entry more than once (two overlapping
-- syncs both uploading the same pending row). That is fixed in the client, but a unique index
-- makes it impossible to happen again from any client version.
--
-- Run the SELECT first. If it returns rows you still have duplicates from the old build; the
-- DELETE below removes all but the earliest copy of each. Both are safe to skip if the SELECT
-- comes back empty.

-- select "userId", "foodName", "mealType", "createdAt", count(*)
--   from public.food_logs
--  group by 1,2,3,4 having count(*) > 1;

-- delete from public.food_logs a
--  using public.food_logs b
--  where a.id > b.id
--    and a."userId"    = b."userId"
--    and a."foodName"  = b."foodName"
--    and a."mealType"  = b."mealType"
--    and a."createdAt" = b."createdAt";


-- ============================================================================
-- SECURITY — please read, this one matters
-- ============================================================================
-- The publishable key ships inside the APK and can be extracted from it, so anything that key
-- can reach is effectively public. Right now that includes the users table:
--
--     select * from users;   -- returns every row, email and password included
--
-- One row still holds a plaintext password from before PasswordHasher landed.
--
-- Your lecturer's advice not to use Supabase Auth is sound and this project already follows it:
-- Supabase Auth keeps accounts in its own `auth.users` schema, which you do not own or control,
-- whereas this app keeps them in `public.users` where you do. Keeping your own table is fine.
--
-- The catch is that per-user RLS policies are normally written against `auth.uid()`, and there
-- is no `auth.uid()` without Supabase Auth. So the way to secure your own users table is to
-- stop exposing it directly and put a function in front of it instead:
--
--   -- 1. Nobody may read the table with the app's key any more.
--   revoke select on public.users from anon;
--
--   -- 2. Login goes through a function that runs as its owner, checks the hash server-side,
--   --    and returns only the id. The password never leaves the database.
--   create or replace function public.login(p_email text, p_password_hash text)
--   returns table ("userId" text)
--   language sql
--   security definer
--   set search_path = public
--   as $$
--     select "userId" from public.users
--      where email = p_email and password = p_password_hash
--      limit 1;
--   $$;
--
--   grant execute on function public.login(text, text) to anon;
--
-- The app would then call `supabase.postgrest.rpc("login", ...)` instead of selecting the row
-- and comparing client-side. That is a code change as well as a SQL one, so it is deliberately
-- NOT applied here — doing the revoke on its own would lock you out of your own app.
