# Implementation Plan - Rework AI Health Insight

Enhance the AI Health Insight feature to provide more personalized advice by including detailed user context (7-day weight trend, BMI, and profile information) in the DeepSeek prompt.

## User Review Required

> [!IMPORTANT]
> The AI advice will now consider the user's height, weight, gender, and 7-day trend. This requires the user to have filled in their profile details (especially height) for accurate BMI calculation and personalization.

## Proposed Changes

### Network Layer

#### [MODIFY] [DeepSeekService.kt](file:///C:/Users/user/Documents/GitHub/carelorie/app/src/main/java/com/xxx/carelorie/data/remote/DeepSeekService.kt)
- Add a new data class `HealthContext` to encapsulate all relevant user data.
- Update `getWeightAdvice` to accept `HealthContext`.
- Refine the prompt to include:
    - Name and Gender for personalization.
    - Calculated BMI and its category.
    - Weight growth/loss over the last 7 days.
    - Lifting experience.
- Maintain the "brief" (approx. 20 words) constraint.

### ViewModel

#### [MODIFY] [DashboardViewModel.kt](file:///C:/Users/user/Documents/GitHub/carelorie/app/src/main/java/com/xxx/carelorie/ui/viewmodels/DashboardViewModel.kt)
- Update `updateWeight` logic:
    1. Fetch the full `UserProfile`.
    2. Calculate BMI: `weight / (heightMeters^2)`.
    3. Calculate the weight change over the last 7 days from `weightHistory`.
    4. Construct `HealthContext` and call `DeepSeekService`.
    5. Save the enriched advice to the profile.

## Verification Plan

### Manual Verification
- Ensure `DEEPSEEK_API_KEY` is active.
- Fill in Profile data (Height, Gender, etc.).
- Update Weight on the Goal page.
- Verify that the AI advice now mentions specific context if relevant (e.g., "Great work Tan, your BMI is improving...") or provides more tailored guidance based on the 7-day trend.
