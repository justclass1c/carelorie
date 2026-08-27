# Fix Navigation Destination Not Found Error

The application is crashing with a `java.lang.IllegalArgumentException` when trying to navigate to the `profile` route. This error indicates that the `NavController` cannot find a destination matching "profile" in the navigation graph.

## Research Findings

1. **Route Definitions**: The `profile` route is defined in `Navigation.kt` using `composable("profile")`. There is also a `profile/{isOnboarding}` route.
2. **Navigation Call**: The `NavigationBar` in `ui/NavigationBar.kt` triggers navigation to `profile` when the "Profile" item is clicked.
3. **Potential Race Condition**: The `NavigationBar` visibility logic in `BottomNavBar.kt` uses `currentDestination?.route != "login"`. If `currentDestination` is `null` (during initial startup), the `NavigationBar` is shown, which could allow a user to click it before the `NavHost` has finished initializing the graph.
4. **Graph State**: The error message shows `startDestination={... route=login}`, confirming the graph is initialized, but for some reason, the `profile` destination is missing or unreachable at the moment of navigation.
5. **Route Overlap**: Having `profile` and `profile/{isOnboarding}` might cause confusion in some navigation matching scenarios, although they are technically distinct.

## Proposed Changes

### [Component Name] UI Navigation

#### [MODIFY] [NavigationBar.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/ui/NavigationBar.kt)
- Improve `NavigationBar` visibility logic to only show the bar when a valid destination is set and it's not a restricted screen (login/register).
- This prevents potential interactions before the graph is fully stable.

#### [MODIFY] [Navigation.kt](file:///home/class1c/AndroidStudioProjects/carelorie/app/src/main/java/com/xxx/carelorie/Navigation.kt)
- Consolidate `profile` and `profile/{isOnboarding}` into a single route with an optional argument: `profile?isOnboarding={isOnboarding}`.
- This simplifies the graph and avoids potential route matching conflicts.
- Update the `RegisterScreen` success navigation to use the new optional parameter format.

## Verification Plan

### Automated Tests
- I will run a build to ensure no syntax errors were introduced.
- I will check the `Navigation.kt` file structure after the change.

### Manual Verification
- Deploy the app and verify that clicking the "Profile" button in the navigation bar no longer causes a crash.
- Verify that registration still leads to the profile setup screen with `isOnboarding=true`.
- Verify that normal profile access (from the bar) has `isOnboarding=false`.
