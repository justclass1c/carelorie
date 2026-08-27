# Implementation Plan - Fix Supabase Permission Denied Error

The application is encountering a `PostgrestRestException: permission denied for table food_logs` (Code: 42501) when attempting to insert a new food log. This error occurs because the Supabase backend has not granted the necessary permissions to the `anon` role, which the app uses for database interactions.

## User Review Required

> [!IMPORTANT]
> This fix requires executing SQL commands in your **Supabase Project Dashboard**. As an IDE assistant, I cannot access your remote database directly. You must run these commands to resolve the issue.

## Root Cause Analysis

The error `42501` (insufficient_privilege) typically stems from two possibilities in Supabase:
1. **Missing Role Grants**: The `anon` role (used for unauthenticated requests) does not have `INSERT` permissions on the `food_logs` table.
2. **Row Level Security (RLS)**: RLS is enabled on the table, but there is no policy that allows the `anon` role to perform `INSERT` operations.

## Proposed Changes

### 1. Supabase Backend Fix (Action Required by User)

Please go to your [Supabase Dashboard](https://supabase.com/dashboard), navigate to the **SQL Editor**, and run the following script. This script will ensure the `anon` role has the correct permissions and that RLS policies are in place to allow data entry.

```sql
-- 1. Ensure the anon role has basic access to the public schema
GRANT USAGE ON SCHEMA public TO anon;
GRANT USAGE ON SCHEMA public TO authenticated;

-- 2. Grant INSERT/SELECT/UPDATE/DELETE permissions on the tables
-- This is necessary if RLS is disabled, or as a baseline for RLS
GRANT ALL ON TABLE public.food_logs TO anon;
GRANT ALL ON TABLE public.food_logs TO authenticated;
GRANT ALL ON TABLE public.macros TO anon;
GRANT ALL ON TABLE public.macros TO authenticated;
GRANT ALL ON TABLE public.food_presets TO anon;
GRANT ALL ON TABLE public.food_presets TO authenticated;

-- 3. Handle RLS (If enabled)
-- If you want to keep RLS enabled but allow the app to work:
ALTER TABLE public.food_logs ENABLE ROW LEVEL SECURITY;

-- Create a policy to allow anyone with the ANON key to insert (matches current app behavior)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'food_logs' AND policyname = 'Allow anonymous inserts'
    ) THEN
        CREATE POLICY "Allow anonymous inserts" ON public.food_logs
        FOR INSERT
        TO anon
        WITH CHECK (true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE tablename = 'food_logs' AND policyname = 'Allow anonymous selects'
    ) THEN
        CREATE POLICY "Allow anonymous selects" ON public.food_logs
        FOR SELECT
        TO anon
        USING (true);
    END IF;
END
$$;
```

### 2. Android Client Improvements

While the primary fix is on the backend, we can improve error handling in the app to provide clearer feedback if this happens again.

#### [MODIFY] [SupabaseRepository.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/data/remote/SupabaseRepository.kt)
- Enhance the `catch` blocks to specifically log Supabase-specific exceptions and potentially notify the UI layer of permission issues.

## Verification Plan

### Manual Verification
1. Run the SQL script in the Supabase Dashboard.
2. Attempt to add a food log in the app.
3. Check the Logcat for the "Error adding food log" message. If the fix is successful, the log should no longer appear, and the data should be visible in the Supabase table editor.
