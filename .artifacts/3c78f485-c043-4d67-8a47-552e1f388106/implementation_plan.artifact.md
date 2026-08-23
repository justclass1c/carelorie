# Implementation Plan - Enable Row Level Security (RLS)

Enabling RLS is a critical step in securing your Supabase database. It ensures that data access is governed by policies rather than just presence of an API key.

## User Review Required

> [!WARNING]
> **Enabling RLS will block all app traffic until policies are created.**
> Currently, your app uses a local `userId` (Integer) and accesses the database anonymously. RLS policies typically rely on Supabase Auth to identify users reliably.

## Proposed Strategy

We will implement RLS in two phases. Phase 1 is a "Soft Launch" that secures the table while maintaining current functionality. Phase 2 (Recommended) involves migrating to Supabase Auth for real security.

### Phase 1: Enable RLS with Anonymous Policies
This allows you to turn on RLS while still allowing your current app (which uses `ANON_KEY`) to function. However, this does **not** provide strong security between different users.

#### SQL Changes (Run in Supabase SQL Editor)
```sql
-- Enable RLS
ALTER TABLE public.food_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.macros ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.food_presets ENABLE ROW LEVEL SECURITY;

-- Create "Public" policies (Temporary)
-- This allows anyone with the anon key to read/write, but RLS is active.
CREATE POLICY "Allow all access to food_logs" ON public.food_logs FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all access to macros" ON public.macros FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "Allow all access to food_presets" ON public.food_presets FOR ALL USING (true) WITH CHECK (true);
```

### Phase 2: Secure Identity-Based RLS (Recommended)
To truly secure data so User A cannot see User B's logs, we must integrate **Supabase Auth**.

#### 1. Add Auth Dependency
- **[libs.versions.toml](file:///home/class1c/AndroidStudioProjects/carelorie/gradle/libs.versions.toml)**: Add `supabase-auth = { group = "io.github.jan-tennert.supabase", name = "gotrue-kt" }`.
- **[build.gradle.kts](file:///home/class1c/AndroidStudioProjects/carelorie/app/build.gradle.kts)**: Add `implementation(libs.supabase.auth)`.

#### 2. Update Client Configuration
- **[SupabaseClient.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/data/remote/SupabaseClient.kt)**: Install the `Auth` plugin.

#### 3. Update Database Schema
- Change `userId` columns from `INT` to `UUID` to match Supabase Auth IDs.
- Link them to the `auth.users` table.

#### 4. Apply Strict RLS Policies
```sql
-- Example for food_logs
DROP POLICY "Allow all access to food_logs" ON public.food_logs;

CREATE POLICY "Users can only see their own logs"
ON public.food_logs
FOR SELECT
USING (auth.uid() = userId);

CREATE POLICY "Users can only insert their own logs"
ON public.food_logs
FOR INSERT
WITH CHECK (auth.uid() = userId);
```

## Open Questions
1. Do you want to proceed with **Phase 1** (just turning it on) or **Phase 2** (integrating full Authentication)?
2. If Phase 2, should I help you set up a Sign-In/Sign-Up screen, or do you want to handle Auth silently in the background?

## Verification Plan
### Manual Verification
- Once RLS is enabled, I will attempt to fetch data. If the policies are missing, the app should receive an empty list or an error.
- I will verify that data still flows after applying the "Allow all" policies.
