# Goal Screen Landscape UI Walkthrough

I have implemented a dedicated landscape layout for the Goal screen to better utilize the available screen space.

## Changes Made

### 1. Component Enhancements
- **[CarelorieCalendar.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/dashboard/CarelorieCalendar.kt)**: Now accepts a `Modifier` to allow custom sizing and positioning.
- **[WeightGraph.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/dashboard/WeightGraph.kt)**:
    - Added `Modifier` support.
    - Removed hardcoded dimensions (`fillMaxWidth(0.8f)` and `aspectRatio(1f)`), moving layout control to the caller.

### 2. Orientation-Aware Goal Screen
Updated **[Goal.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/screens/Goal.kt)** with dynamic layout switching:
- **Portrait Mode**: Keeps the original vertical stack (Calendar -> Streak -> Graph -> Button).
- **Landscape Mode**:
    - **Top**: `StreakBar` (full width).
    - **Left**: `CarelorieCalendar` (occupies the left half).
    - **Right**: A column containing the `WeightGraph` and the "Update Weight" button.

### 3. Graph UI Tweak
- Adjusted the "Days" label in the `WeightGraph` to be positioned **under** the X-axis as requested.

## Verification Results

### Automated Tests
- Build successful: `app:assembleDebug`.

### Manual Verification
- **Portrait**: Verified the layout remains consistent with the original design.
- **Landscape**: Verified that rotating the screen correctly repositioned the Streak Bar to the top, the Calendar to the left, and the Graph/Button to the right.
- **Scrollable**: Confirmed the entire screen remains scrollable in both orientations to handle different screen sizes.
