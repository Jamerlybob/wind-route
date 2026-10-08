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
- The launcher icon is still the Android Studio default.
- Addresses are typed free text with no suggestions.
- Departure is only Now, +1 h or +3 h.
- A route is lost if the app is killed.

## Next action

Milestone 1 in `docs/ROADMAP.md` is under way. Dark mode is done: the cards
follow the system through the DayNight theme, and the map uses our own style
file `res/raw/map_style_night.json`. Checked on the emulator in both modes,
and the route stays on screen across a switch (it now lives in `RouteState`,
a ViewModel).

The colour theme is done too: a blue palette (`brand_*` in `colors.xml`, with
a dark copy in `values-night`) wired into the Material colour roles in
`themes.xml`. Blue because the wind colours do not use it.

Next: "use my location" as the start, then place suggestions with the free
Geocoder, swap start and finish, and settings.

Note for whoever does maps work next: the emulator's Play services loads the
legacy map renderer, and `GoogleMap.setMapColorScheme` is ignored there
(logcat says so). That is why dark mode is a JSON style, not the colour
scheme call. Do not switch back without testing on a legacy-renderer device.

Before building Milestone 3 (bikepacking), finish the research gap noted at the
bottom of `docs/RESEARCH.md`.

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
restrict the key to Maps SDK for Android and Routes API. Not yet confirmed
done, so ask him next session. Do not add an Android-app restriction to the
key: the Routes call is plain HTTP and does not send the package headers.

Getting builds to his phone: `SendUserFile` was not available this session,
so the APK was copied to `Desktop\WindRoute` on the laptop. He may plug the
phone in with USB debugging; check `adb devices` and install directly if so.

## Not done yet, on purpose

- The fortnightly scheduled build James asked for is not set up. Set it up
  once Milestone 1 is in and he has confirmed v0.1 on his phone.