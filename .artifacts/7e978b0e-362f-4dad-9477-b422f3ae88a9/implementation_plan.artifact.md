# Goal Screen Landscape UI Implementation Plan

Add a dedicated landscape layout for the Goal screen to improve usability on wide screens.

## Proposed Changes

### UI Components

#### [MODIFY] [WeightGraph.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/dashboard/WeightGraph.kt)
- Add a `modifier: Modifier = Modifier` parameter to the `WeightGraph` function.
- Replace the hardcoded `fillMaxWidth(0.8f)` and `aspectRatio(1f)` with the passed `modifier`.
- Move the default styling (`aspectRatio(1f)`, etc.) to the call sites in `Goal.kt` to allow different layouts in portrait vs. landscape.

#### [MODIFY] [CarelorieCalendar.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/dashboard/CarelorieCalendar.kt)
- Add a `modifier: Modifier = Modifier` parameter to the `CarelorieCalendar` function.
- Apply this `modifier` to the root `Column`.

### Screens

#### [MODIFY] [Goal.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/screens/Goal.kt)
- Use `LocalConfiguration.current.orientation` to detect the device orientation.
- Implement two layout branches:
    - **Portrait**: Current vertical stack (Calendar -> Streak -> Graph -> Button).
    - **Landscape**:
        - `StreakBar` at the top (full width).
        - A `Row` below the streak containing:
            - `CarelorieCalendar` (weighted to take left half).
            - A `Column` (weighted to take right half) containing the `WeightGraph` and "Update Weight" button.
- Ensure the layout is scrollable in both orientations if content exceeds screen height.

## Verification Plan

### Automated Tests
- Build successful: `app:assembleDebug`.

### Manual Verification
- Deploy to a device/emulator.
- Verify portrait layout remains unchanged.
- Rotate the device to landscape and verify:
    - `StreakBar` is at the top.
    - `Calendar` is on the left.
    - `WeightGraph` is on the right.
    - "Update Weight" button is appropriately placed (e.g., below the graph or at the bottom of the right column).
