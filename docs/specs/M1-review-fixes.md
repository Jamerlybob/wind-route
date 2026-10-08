# Review fixes for Milestone 1

Read `docs/specs/COMMON.md` first. Claude reviewed Milestone 1 and ran it on
the emulator. It works. These are the things to fix. Change nothing else.

## 1. Text in the two address fields touches the outline

Since the fields became `MaterialAutoCompleteTextView`, the text starts flush
against the left edge of the outlined box with no padding (look at "Mission
Bay, Auckland" in the From field). It must sit inside the box with the normal
Material start padding, exactly as the old `TextInputEditText` did, in both
fields, in light and dark. Check it on the emulator with a screenshot.

## 2. Suggestions can be thrown away by the text field itself

`PlaceSuggestionsController` fills a plain `ArrayAdapter`. An
`AutoCompleteTextView` runs the adapter's own `Filter` on every keystroke, and
`ArrayAdapter`'s filter keeps only items whose words start with the typed
text. So a Geocoder result such as "12 Ponsonby Road, Auckland" for the query
"ponsonby rd", or a recent place that merely contains the text, is silently
dropped, and each keystroke re-filters the list we just set.

We have already chosen what to show, so the field must show the list as
given. Use a small adapter whose `Filter` returns every item unchanged, and
explain why in a comment (this is a classic trap worth teaching).

On the emulator no dropdown appeared at all for "Ponsonby Road" typed in the
To field. Logcat showed `GmsGeocoder: forward geocoding network failure`, so
that may be the emulator's Geocoder rather than our code, but confirm the
dropdown path with recent places (they need no network): after one search,
focusing an empty field must list the recent places, and typing part of one
must keep it listed.

Also give suggestions their own single-thread executor. Today they share the
Activity's one background thread with the route search, so a slow Geocoder
call delays "Show wind".

## 3. The comments did not survive

`AGENTS.md` says James is learning Java from this code and asks for the
comment density of `WindMath.java`. The new and rewritten classes are far
below it: `RouteMapRenderer.java` has about four comment lines in 200, and the
rewrite of `MainActivity` deleted explanations that were there on purpose
(why work goes to a background thread and comes back through a `Handler`, the
white casing under the route, why neighbouring stretches are drawn as one
line, why the map style is a JSON file and not `setMapColorScheme`).

- Restore those explanations where the code now lives. `git show
  d27d330:app/src/main/java/io/github/jamerlybob/windroute/MainActivity.java`
  has the originals.
- Go through every class added in Milestone 1 (`CurrentLocationController`,
  `PlaceSuggestionsController`, `RouteForecastLoader`, `RouteMapRenderer`,
  `RouteStore`, `SettingsActivity`, `WindRouteApplication`, `settings/`,
  `units/`, `places/`, `route/RouteJson`, `route/RouteWaypoint`,
  `route/CyclingWarnings`, `wind/DirectionComparison`) and add the why:
  class Javadoc saying what it is for and why it is its own class, and a
  comment wherever an Android concept appears for the first time
  (`ActivityResultLauncher`, `ViewModel` fields, `SharedPreferences`,
  `TextWatcher`, the generation counters that drop stale results). Explain
  ideas, do not narrate lines.
- In `AndroidManifest.xml` the comment about `configChanges` now sits above
  `SettingsActivity`. Move it back above `MainActivity`.
- `activity_main.xml` lost its explanatory comments too; restore them.

Do the same pass, more lightly, on the classes merged from the logic branches
that are thin on why: `elevation/ClimbDetector` (each threshold constant needs
a line saying where the number comes from), `elevation/ElevationProfile`,
`nav/RouteProgress` (the named tolerances), `poi/PoiAlongRoute`.

No behaviour changes in this item. Tests must still pass unchanged.
