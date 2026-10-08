# STATE

**Updated:** 2026-10-08

## Where it is

Milestone 1 is in and works on the emulator (2026-10-08). James confirmed v0.1
on his OPPO with a Wellington route the same day and asked for the whole
roadmap, with Codex doing most of the work.

On screen now: my location as the start, suggestions from the free Geocoder
with recent places, swap and the other direction's wind, a settings screen
(units, riding speed, theme, calm threshold), the last route restored on a
cold start with no Routes call, Google's cycling notice always shown.

In the code with tests but NOT on screen yet (106 unit tests pass):

- `weather/` 8 day forecast with rain and temperature, `RideWeather`
- `wind/DepartureScorer`, `GustWarnings`, `PowerModel` (wind in minutes)
- `elevation/` client, profile, `ClimbDetector`, `ClimbWind`
- `gpx/` parser and writer, `trip/DaySplitter`, `nav/RouteProgress`
- `poi/` water, food, camping and bike shops from Overpass
- A new launcher icon (not yet looked at on a device)

## How the work is being done

Claude writes a spec in `docs/specs/`, Codex builds it with
`codex exec -s workspace-write` (one agent per git worktree so they cannot
collide), Claude reviews, runs it on the emulator and commits. Read
`docs/specs/COMMON.md` before handing Codex anything.

Codex hit James's ChatGPT usage limit at about 19:15 on 2026-10-08, part way
through `docs/specs/M1-review-fixes.md`. It said the limit resets at 23:32.
Three parallel agents at high reasoning effort used the allowance in about
two hours, so run fewer at once or at medium effort.

## Next action

1. Finish `M1-review-fixes.md` item 3: the comment pass is only partly done
   (`RouteMapRenderer`, `RouteStore` and `SettingsActivity` are still thin).
   Items 1 and 2 are done and checked on the emulator.
2. Build `docs/specs/M2-M3-screen.md`: bottom sheet, departure picker, best
   time strip, rain, wind cost, gust warnings, hills profile and climbs.
3. Then Milestone 5 screens (GPX import, trip days, places) and Milestone 4
   (riding with it). Milestone 4 needs the voice-cue research in
   `docs/RESEARCH.md` done first and a real phone to test on.

## Known gaps

- Geocoder suggestions could not be confirmed on the emulator: its Geocoder
  logs "forward geocoding network failure". Recent places do appear in the
  dropdown. Needs a check on James's phone.
- Focusing an empty field does not list recent places; typing does.
- "My location" has only been tried with the emulator's fake position.
- With approximate-only permission the start can be a couple of km out.
- `GpxParser` hardening is best-effort on Android's XML parser and has only
  run on the desktop JVM. Try a real GPX on a device when import is built.
- `OverpassClient` thins a long route to 100 points; on a long winding route
  the 500 m corridor may miss places. Widen it or split the route.
- The legacy `mipmap-*` WebP icons (Android 7 only) are still the default.

Note for whoever does maps work next: the emulator's Play services loads the
legacy map renderer, and `GoogleMap.setMapColorScheme` is ignored there
(logcat says so). That is why dark mode is a JSON style, not the colour
scheme call. Do not switch back without testing on a legacy-renderer device.

## Cost: James will not pay for anything

Decided 2026-10-08. Place suggestions will use the free Geocoder.

Checked against Google's pricing pages the same day:

- The map itself (Maps SDK for Android) is free with no limit.
- Routes API `computeRoutes` has a free monthly allowance: 10,000 requests on
  the Essentials SKU, 1,000 on Enterprise. Our request (BICYCLE, no traffic,
  no waypoints) should be Essentials. The docs list "two-wheeled vehicle
  routing" as Enterprise without saying whether that includes bicycles, so
  confirm which SKU shows up in the Cloud billing report.
- When the Cloud free trial ends (90 days or $300 of credit), Google does not
  charge anything, but the billing account closes and the key stops working
  unless James upgrades it to a paid account. Upgrading does not cost
  anything by itself; the monthly allowances still apply.

Decided 2026-10-08: stay on Google. James is fine with a card on the account
and was given the console steps the same day: upgrade the billing account,
cap the Routes API daily quota at about 30 requests (under 1,000 a month, so
it stays inside the free allowance on either SKU), add a budget alert, and
restrict the key to Maps SDK for Android and Routes API. He asked to do it a week later and has a calendar reminder for 2026-10-15. Not yet confirmed
done, so ask him next session. Do not add an Android-app restriction to the
key: the Routes call is plain HTTP and does not send the package headers.

Getting builds to his phone: `SendUserFile` was not available this session,
so the APK was copied to `Desktop\WindRoute` on the laptop. He may plug the
phone in with USB debugging; check `adb devices` and install directly if so.

The phone is an OPPO A5. On 2026-10-08 it connected for file transfer only
(USB debugging off, so `adb devices` was empty). The theme build was copied
into its Download folder over MTP for James to install by hand.

## Not done yet, on purpose

- The fortnightly scheduled build James asked for is not set up. Set it up
  once Milestone 1 is in and he has confirmed v0.1 on his phone.