# STATE

**Updated:** 2026-10-08

## Where it is

v0.1 works end to end. Verified on the emulator with a real key: Mission Bay to
Mt Eden, Auckland returned 12.9 km, 50 min, 66% headwind, and the route drew
coloured correctly for the south-westerly in the forecast. 43 unit tests pass,
lint is clean, CI is green. James has the APK on his phone but has not reported
back on it yet.

The Maps key is in `local.properties` on James's PC. Google Cloud project,
billing, Maps SDK for Android and Routes API are set up.

## Known gaps in v0.1

- No Google cycling warning appeared on the test route. Check whether
  `routes.warnings` is really empty for bicycle routes or whether the notice
  has to be shown unconditionally (Google's terms require it).
- Default purple Material theme, no dark map style.
- Addresses are typed free text with no suggestions.
- Departure is only Now, +1 h or +3 h.
- A route is lost if the app is killed.

## Next action

Start Milestone 1 in `docs/ROADMAP.md` (make it something James will open every
day): dark mode with a dark map style, a proper colour theme, "use my
location" as the start, place suggestions, and a settings screen with units.

Before building Milestone 3 (bikepacking), finish the research gap noted at the
bottom of `docs/RESEARCH.md`.

## Not done yet, on purpose

- The fortnightly scheduled build James asked for is not set up. Set it up
  once Milestone 1 is in and he has confirmed v0.1 on his phone.