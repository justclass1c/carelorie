# Carelorie — steps 1–3

Unzip over your project. Nothing from your original code was deleted except dead duplicates
(see "Cleanup" below).

## Before you run

1. **Gradle sync** — catalog and `app/build.gradle.kts` both changed.
2. **Room 4 → 5** (new `food_log_entries` table). Project uses `fallbackToDestructiveMigration()`,
   so local accounts/profiles/weights are cleared once. Re-register a test account.
3. **Supabase DELETE policy** — without it, deletes fail silently:
   ```sql
   create policy "allow delete" on food_logs for delete using (true);
   ```
4. **AI key is optional.** The app runs fine without it (stub mode). To enable real photo
   recognition, add to `local.properties` (already gitignored):
   ```
   GEMINI_API_KEY=your_key_here
   ```
   Get a free key at aistudio.google.com. Note: any key compiled into an APK can be extracted
   from it — use a rotatable free-tier key, and rotate after submission.

---

## Step 1 — stabilise
- Dashboard: **Snack** added; all four meals from one `MEAL_TYPES` list
- Dashboard: fixed scrolling (`fillMaxSize()` inside `verticalScroll` = infinite height constraint)
- Dashboard: **"•••" menu** works — *Remove food* enters delete mode with a trash icon per row;
  *Save as meal* present but disabled until Create Meal exists
- **Food Log tab was blank** — route was `composable("food log") { }`; screen + ViewModel were
  already written and never connected
- Added delete for food logs (existed nowhere in the data layer)
- **Deleted `generateDummyData()`** — dashboard was showing randomised macros when Supabase
  returned nothing
- ViewModels moved from `MainActivity.onCreate` into `AppContainer` + factories → survive rotation
- Gradle: Compose BOM was declared twice; removed duplicate `ui`/`material3` pins and a
  desugaring library that was never enabled
- Macro colours centralised in `ui/theme/MacroColors.kt` (were copy-pasted in five files)

## Step 2 — Food Log module
**Offline-first.** Logs write to Room first, push to Supabase after. The screen reads only from
Room, so history works with no connection. Entries written offline queue and upload on reconnect.
Deleting offline hides the row locally and removes the server copy on the next sync.

- `data/local/FoodLogEntity.kt`, `FoodLogDao.kt` — Flow queries by day, range, logged dates
- `data/FoodRepository.kt` — rewritten offline-first; `refresh()` never wipes a good cache on a
  failed request, so a dropped connection mid-demo can't blank the screen
- Screen: grouped by meal (empty meals hidden), per-entry timestamps, per-entry delete,
  ring macro progress, real empty states, offline banner
- Month calendar: tracked days filled, today outlined, future disabled. Built from fixed rows of
  seven, not a `LazyVerticalGrid`, so it nests inside a scrolling column safely
- `data/NutritionTargets.kt` — targets were hardcoded in two places and could disagree
- `data/DefaultFoodPresets.kt` — 28 Malaysian presets (nasi lemak, char kuey teow, roti canai,
  teh tarik…). Approximate single-serving values; seeds when the presets table is empty

## Step 3 — Food Search + Review Foods
**Meal-type dropdown** in the top bar (your V2 sketch) — change the target meal without going back.

**Barcode scanning** via Google Code Scanner (`play-services-code-scanner`). Chosen over CameraX
+ ML Kit because it supplies the whole scanning UI and **needs no camera permission**. Scans
resolve against Open Food Facts and auto-select the match.

**Open Food Facts** (`data/nutrition/OpenFoodFactsService.kt`) — free, no API key, and it backs
both the barcode lookup *and* the detailed nutrition panel. One integration, two features.
Uses `HttpURLConnection` with tolerant hand-parsing rather than strict deserialisation, because
the response shape varies per product.

**AI photo recognition** — `FoodRecognitionService` interface with two implementations:
- `StubFoodRecognitionService` — canned results, so the full flow demos today with no key
- Gemini-backed implementation — activates automatically when `GEMINI_API_KEY` is set

**Review Foods** — the tick button in the search bar opens it *before* anything is logged.
Shows each selected food, adjustable quantity, expandable nutrition detail (fibre, sugar,
sodium, saturated fat — whatever the product actually lists), running totals, and a source badge
so users know whether numbers came from a preset, a barcode or a photo estimate. Nothing is
hardcoded; missing fields are hidden rather than shown as zero.

## Tablet support
`MainActivity` computes a `WindowSizeClass`:
- Compact (phones, split screen) → bottom navigation bar
- Medium/expanded (tablets) → navigation rail, matching your prototype tablet slide
- Food Log on wide screens → two panes, calendar always visible

Recalculated on window change, so rotating or entering split screen swaps layout live.

## Cleanup
Three parallel half-finished versions of the nutrition/AI layer existed. Kept `data/nutrition/`
(what the UI imports) and deleted the unreferenced duplicates: `data/ai/`, `data/model/`,
`data/FoodItem.kt`, `data/remote/OpenFoodFactsService.kt`, `ui/components/food/FoodResultCard.kt`,
`ui/screens/CameraScreen.kt`. Also dropped four CameraX dependencies that nothing used, and
**removed `CAMERA` from the manifest** — declaring it makes `ACTION_IMAGE_CAPTURE` demand a
runtime grant, which would have broken the AI photo button.

`FoodItemCard.kt` and `FoodLogEntryCard.kt` are now unused (their screens were rewritten). Left
in place in case a teammate wants them; safe to delete.

## Not built yet
- Create Meal, onboarding — deferred at your request
- The shared-userId fix — still open. Two devices both become `userId = 1`, so histories merge.
  The Food Log is where this shows up worst.

## Known limitations
- Preset and AI macro values are approximations, as all portion-based estimates are
- Unsynced entries push on screen visit, not via a background worker (WorkManager would be the upgrade)
- `refresh()` treats an empty response as possibly-offline when a cache exists; Supabase can't
  distinguish the two here, and keeping data is the safer default
