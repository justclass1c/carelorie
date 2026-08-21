# Weight Tracking Enhancement Implementation Plan

Enhance the weight tracking feature by adding date selection to the update dialog and improving the graph visualization.

## Proposed Changes

### ViewModel

#### [MODIFY] [DashboardViewModel.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/viewmodels/DashboardViewModel.kt)
- Update `DashboardEvent.UpdateWeight` to include a `LocalDate`.
- Update `updateWeight` method to use the user-selected date instead of defaulting to today.

### UI Components

#### [MODIFY] [WeightUpdateDialog.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/dashboard/WeightUpdateDialog.kt)
- Add a date selection field that triggers a `DatePickerDialog`.
- Default the date to `LocalDate.now()`.
- Pass both the weight and the selected date back to the caller.

#### [MODIFY] [WeightGraph.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/dashboard/WeightGraph.kt)
- Change the container `Box` to be square using `aspectRatio(1f)`.
- Adjust the internal graph drawing logic to ensure the axes and data points are centered within the square container.

### Screens

#### [MODIFY] [Goal.kt](file:///C:/Users/user/Desktop/carelorie/app/src/main/java/com/xxx/carelorie/ui/screens/Goal.kt)
- Update the `WeightUpdateDialog` integration to handle the new date parameter.

## Verification Plan

### Automated Tests
- Build successful: `app:assembleDebug`.

### Manual Verification
- Open the Weight Update dialog.
- Verify the current date is shown by default.
- Click to change the date and verify the selection works.
- Save a weight for a past date and verify the graph updates correctly.
- Verify the graph box is square and centered on the Goal screen.
