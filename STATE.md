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
- Default purple Material theme (dark mode itself is done).
- Addresses are typed free text with no suggestions.
- Departure is only Now, +1 h or +3 h.
- A route is lost if the app is killed.

## Next action

Milestone 1 in `docs/ROADMAP.md` is under way. Dark mode is done: the cards
follow the system through the DayNight theme, and the map uses our own style
file `res/raw/map_style_night.json`. Checked on the emulator in both modes,
and the route stays on screen across a switch (it now lives in `RouteState`,
a ViewModel).

Next: the colour theme and typography, then "use my location", place
suggestions (waiting on James: paid Places or free Geocoder), and settings.

Note for whoever does maps work next: the emulator's Play services loads the
legacy map renderer, and `GoogleMap.setMapColorScheme` is ignored there
(logcat says so). That is why dark mode is a JSON style, not the colour
scheme call. Do not switch back without testing on a legacy-renderer device.

Before building Milestone 3 (bikepacking), finish the research gap noted at the
bottom of `docs/RESEARCH.md`.

## Not done yet, on purpose

- The fortnightly scheduled build James asked for is not set up. Set it up
  once Milestone 1 is in and he has confirmed v0.1 on his phone.