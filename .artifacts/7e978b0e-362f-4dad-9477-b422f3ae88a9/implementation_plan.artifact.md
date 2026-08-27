# Tablet Streak Bar Optimization Implementation Plan

Optimize the `StreakBar` component for tablet views by displaying all boxes in a single centered line.

## Proposed Changes

### UI Components

#### [MODIFY] [StreakBar.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/dashboard/StreakBar.kt)
- Use `LocalConfiguration.current.screenWidthDp` to detect if the device is in a wide (tablet) view (>= 600dp).
- If in tablet view:
    - Display all 30 boxes in a single row.
    - Center the row horizontally using `Arrangement.Center`.
- If in regular view (phone):
    - Keep the two-row layout (15 boxes each).
    - Update `StreakRow` to use `Arrangement.Center` for better alignment on all devices.
- Ensure the "Current Streak" text remains centered above the boxes.

## Verification Plan

### Manual Verification
- **Phone View**: Verify the streak bar still shows two rows of boxes, centered.
- **Tablet View**: Verify the streak bar shows all 30 boxes in a single centered line.
- **Landscape Phone**: Depending on the phone width, verify it switches to a single line if the width exceeds 600dp.
