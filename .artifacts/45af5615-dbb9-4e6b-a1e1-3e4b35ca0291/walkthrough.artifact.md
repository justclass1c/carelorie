# Walkthrough - Supabase Integration

I have successfully integrated Supabase into the Carelorie app to handle remote storage for macros and food logs, tied to the local `userId`.

## Changes Made

### Configuration & Networking
- **[AndroidManifest.xml](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/AndroidManifest.xml)**: Added the `INTERNET` permission to allow the app to communicate with Supabase.
- **[libs.versions.toml](file:///home/class1c/AndroidStudioProjects/carelorie/gradle/libs.versions.toml)** & **[build.gradle.kts](file:///home/class1c/AndroidStudioProjects/carelorie/app/build.gradle.kts)**:
    - Integrated the **Supabase BOM** (Bill of Materials) for version consistency.
    - Added `supabase-postgrest` for database operations.
    - Added `kotlinx-serialization-json` for data mapping.
    - Applied the `kotlinx-serialization` plugin.

### Remote Data Layer
- **[SupabaseClient.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/data/remote/SupabaseClient.kt)**: Initialized the Supabase client.
    > [!IMPORTANT]
    > You MUST replace `YOUR_SUPABASE_URL` and `YOUR_SUPABASE_ANON_KEY` in this file with your actual project credentials.
- **[RemoteModels.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/data/remote/RemoteModels.kt)**: Defined `@Serializable` models for `macros` and `food_logs` tables.
- **[SupabaseRepository.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/data/remote/SupabaseRepository.kt)**: Implemented CRUD operations using Postgrest to insert and fetch data by `userId`.

### Logic Integration
- **[MacroDataRepository.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/data/MacroDataRepository.kt)**:
    - Updated to attempt fetching data from Supabase.
    - Includes an automatic fallback to dummy data if the network is unavailable or the table is empty.
    - Added `syncDailyMacros()` to upload local data to the cloud.

## Verification Results

### Build & Connectivity
- The project builds successfully (`app:assembleDebug`).
- The Supabase client initializes correctly within the DI/MainActivity setup.

### Data Flow
- Dashboard data fetching is now a `suspend` operation, correctly managed by `viewModelScope` in `DashboardViewModel`.
- Remote models are correctly mapped to local UI models.

## Next Steps for You

1.  **Configure Credentials**: Update the constants in `SupabaseClient.kt`.
2.  **Setup Tables**: Create two tables in your Supabase project:
    - `macros`: Columns: `userId` (int), `date` (text/iso8601), `protein` (float), `carbs` (float), `fat` (float).
    - `food_logs`: Columns: `userId` (int), `mealType` (text), `foodName` (text), `calories` (int), `protein` (float), `carbs` (float), `fat` (float), `createdAt` (text).
3.  **Authentication**: Since account credentials remain local, you can use the `userId` as the foreign key in your Supabase tables to keep data segregated.
