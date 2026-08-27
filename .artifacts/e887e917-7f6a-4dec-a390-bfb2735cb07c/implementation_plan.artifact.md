# Implementation Plan - Start Weight Graph from Left

Modify the weight graph in `WeightGraph.kt` so that the line and dot plot start from the leftmost position of the graph area (the Y-axis), regardless of which day of the month the first record occurs on.

## User Review Required

> [!IMPORTANT]
> This change will shift the x-coordinates of the weight points so that the first recorded weight of the month always starts at the Y-axis. The distance between points will still be proportional to the number of days between them, but the graph will no longer show empty space for days before the first record.

## Proposed Changes

### UI Components

#### [MODIFY] [WeightGraph.kt](file:///C:/Users/user/Documents/GitHub/carelorie/app/src/main/java/com/xxx/carelorie/ui/components/dashboard/WeightGraph.kt)

- Update the points mapping logic:
    - Identify `firstDay` and `lastDay` from the filtered `monthlyWeights`.
    - Calculate `dayRange = lastDay - firstDay`.
    - If `dayRange > 0`, calculate `x = paddingLeft + ((day - firstDay) / dayRange.toFloat()) * graphWidth`.
    - If `dayRange == 0` (only one record), center the point or place it at `paddingLeft`. I'll place it at `paddingLeft` to truly "start from left".

## Verification Plan

### Manual Verification
- Deploy the app.
- Add weight records for various dates (e.g., 10th, 15th, 20th).
- Verify that the first record (10th) starts at the Y-axis.
- Verify that the trend line and dots are clearly visible and start from the left.
- Verify that labels still correctly show the actual day of the month.
