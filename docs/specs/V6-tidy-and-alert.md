# Tidy up, then the "good ride day" alert (V6, 2026-10-30)

Read `docs/specs/COMMON.md` first. James said go on 2026-10-30. He also said
he did not see the wind overlay on his phone; Claude has already made the
particles more visible (`WindPresentation.particleOpacity`, thicker lines,
450 particles). Do the tidy-up first, then the alert.

## Part 1: tidy up

1. **The wind layer must never be silently missing.** If the grid request
   is loading, show a small progress ring on the Layers button; if it
   fails (no network, HTTP error), show a short message once ("Couldn't
   load the wind map. Tap Layers to retry.") and retry when Layers is
   tapped or the camera next settles. Log nothing to the user when it
   works. Check also that the layer is fetched on a fresh install with no
   route and no location permission yet (use the camera position).
2. **Places list**: headings in title case with an icon ("Water (12)",
   "Food (40)", "Camping (3)", "Bike shops (2)"); an unnamed place uses its
   kind ("Drinking water", "Toilets", "Campsite") rather than "Unnamed
   water", and identical unnamed places within 50 m collapse into one row.
   Tapping a row centres the map on it and shows a small card with name,
   kind, distance along and off the route, opening hours if known, and
   "Add to GPX export" / "Remove". Pure Java for naming and collapsing,
   tested.
3. **Gust warnings on the route, not as a wall of text.** Draw a small gust
   marker (a wind icon in the crosswind colour) at the start of each gusty
   stretch. In the sheet, collapse the list to one line ("Gusts to 75 km/h
   on 4 stretches") that expands on tap.
4. **Saved trips offline**: opening a saved trip with no network must show
   everything stored and "Forecast from 3 h ago". Check the code path by
   reading it and add a unit test for the age wording; fix anything that
   would make a network call before showing stored data.
5. **App icon**: the legacy `mipmap-*` WebP icons (only used before
   Android 8) are still the template defaults. Replace them with PNGs
   rendered from the adaptive icon's foreground and background, at the
   standard densities, or remove them and set `minSdk`-appropriate
   fallbacks if the project's minSdk is 26 or more (check `build.gradle`).

## Part 2: "good ride day" alert

A rider picks a saved route (or the current one) as "my usual ride" and a
time ("I usually ride at 7 AM"). Each evening at about 7 PM the app checks
tomorrow's forecast for that route at that time and, if it is a good day,
posts one notification: "Tomorrow 7 AM looks good: tailwind home, dry, 14
degrees." Optional second rule: "Also tell me when it's a bad day" (strong
headwind or rain).

- **No new libraries** (WorkManager is a new dependency and James has not
  approved it). Use the platform `JobScheduler` with a periodic job (once a
  day, requires network) and say in a comment why not WorkManager. The job
  runs one Open-Meteo request for the route's sample points (free), no
  Google call, then scores it with `DepartureScorer`.
- Settings: an "Alerts" section with the switch, the chosen route, the
  time, and the bad-day option. Notification permission requested when
  the switch is turned on, with one line of explanation.
- Pure Java for "is tomorrow good" and the notification sentence, tested.
- Tapping the notification opens the route for that time.

## Checking

Unit tests for the pure logic. testDebugUnitTest lintDebug assembleDebug
must pass. One Gradle build at a time, no emulator, do not commit.
