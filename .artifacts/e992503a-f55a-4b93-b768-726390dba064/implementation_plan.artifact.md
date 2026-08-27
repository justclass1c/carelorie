# Implementation Plan - Food Log Page

Implement a scrollable Food Log page that allows users to view and navigate food entries by date, with a daily macro summary and Supabase integration.

## Proposed Changes

### [Data Layer]

#### [MODIFY] [SupabaseRepository.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/data/remote/SupabaseRepository.kt)
- Ensure `fetchFoodLogs` correctly handles date filtering. (Current implementation uses `ILIKE "$date%"` which is suitable for ISO strings).

### [UI Layer - ViewModels]

#### [NEW] [FoodLogViewModel.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/ui/viewmodels/FoodLogViewModel.kt)
- Create `FoodLogViewModel` to manage state for the Food Log screen.
- State will include: `selectedDate`, `foodLogs`, `isLoading`, `error`, and `targets` (Protein, Carbs, Fat, Calories).
- Methods to load logs for a specific date and update the selected date.

### [UI Layer - Screens & Components]

#### [NEW] [FoodLogScreen.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/ui/screens/FoodLogScreen.kt)
- Implement the main UI for the Food Log.
- Include a `DateNavigator` (arrows + clickable date).
- Include a `MacroSummaryCard` showing P, C, F, and Total Calories progress.
- Include a `LazyColumn` for displaying `FoodLogEntryCard`s.

#### [NEW] [FoodLogEntryCard.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/food/FoodLogEntryCard.kt)
- A card component to display an individual food log entry with its PCFC values.

### [Navigation & Wiring]

#### [MODIFY] [MainActivity.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/MainActivity.kt)
- Initialize `FoodLogViewModel`.
- Pass it to `BottomNavBar`.

#### [MODIFY] [NavigationBar.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/ui/NavigationBar.kt)
- Accept `FoodLogViewModel` and pass it to `AppNavigation`.

#### [MODIFY] [Navigation.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/Navigation.kt)
- Accept `FoodLogViewModel`.
- Replace the empty `food log` composable with `FoodLogScreen`.

## Verification Plan

### Automated Tests
- N/A (Manual verification via UI)

### Manual Verification
- Deploy the app and navigate to the "Food Log" tab.
- Verify that the current date is displayed.
- Use arrows to change dates and verify logs update (if any).
- Tap the date to open the calendar (DatePicker) and select a different date.
- Verify the summary totals match the logs for that day.
- Verify the scrollability if there are many entries.
