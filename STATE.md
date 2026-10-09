# STATE

**Updated:** 2026-10-09

## Where it is

Milestone 1 is in and works on the emulator (2026-10-08). James confirmed v0.1
on his OPPO with a Wellington route the same day and asked for the whole
roadmap, with Codex doing most of the work.

On screen now: my location as the start, suggestions from the free Geocoder
with recent places, swap and the other direction's wind, a settings screen
(units, riding speed, theme, calm threshold), the last route restored on a
cold start with no Routes call, Google's cycling notice always shown.

Also on screen since 2026-10-09 (118 unit tests pass): Milestones 2 and 3
(see below) and Milestone 5: GPX import and export, "open with" for .gpx
files, a trip screen that splits a route into days with each day forecast
for its own date, shift the trip a day, sunrise and sunset, saved trips.

Built on 2026-10-09 (143 unit tests pass, lint clean) and only partly
checked: Milestone 4, riding mode. "Start ride" in the sheet opens a
heading-up map with the next turn and the distance to it, the rider's place
on the hills profile, the wind right now, and Stop. Behind it are
`RideService` (a foreground location service with a notification), spoken
cues planned in pure Java (`nav/CueBuilder`, `nav/CuePlanner`), off-route
detection with a re-route only on a tap, and arrival. Seen on the emulator:
ride mode opening at the start of a route, standing still. NOT seen: the
ride moving, turns arriving, the off-route banner, arrival, the
notification. Never heard: any spoken cue.

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

James cleared the session on 2026-10-09 without testing the riding build
("I won't do that stuff yet"). He has the APK (commit 03902fc). Start the
next session by asking whether he has tried it.

### For James to do

1. Install the riding build, pick a short Google route, tap "Start ride" and
   walk or ride a few hundred metres. Report: does it speak, does the banner
   count down to the turn, does it keep going with the screen off, does Stop
   in the notification work, and above all does it crash.
2. On the same outing: do address suggestions appear while typing, and is
   "My location" right with real GPS.
3. The Google console steps (reminder set for 2026-10-15): upgrade the
   billing account, cap Routes at about 30 requests a day, add a budget
   alert, restrict the key. While there, look at which SKU the Routes calls
   are billed under now that the request asks for `routes.legs.steps`.

### For Claude (with Codex) to do

Riding mode, all unverified until it has been seen moving:
1. Get a moving ride on screen. Either find why the emulator dies in ride
   mode (see Known gaps), try a different AVD or system image, or install
   on James's phone over USB debugging and replay there.
2. Then check, with screenshots: the banner changing to the next turn, the
   very short steps Google returns (6 m, 8 m, 21 m) not flashing past, the
   off-route banner after 10 s and "Re-route from here" spending exactly one
   Routes call, arrival and the ride summary, the notification and its Stop
   action, the ride surviving the Activity being destroyed, and the map
   going back to the whole route when the ride ends
   (`RouteMapRenderer.setRideMode`, written by Claude, compiled, not seen).
3. A GPX route in ride mode (no steps): position, wind and climb cues only.
4. The cue settings screen (which cues, how early, "less talk") has not been
   opened on the emulator at all.
5. A far-away first fix: the emulator's default position in California
   showed "1.5 km left, 100 m, turn right" on an Auckland route before
   off-route could trigger. Decide what the banner should say when the rider
   is nowhere near the route.
6. Read `RideService`, `RideScreenController`, `CuePlanner` and
   `CuePlanFactory` properly. Claude reviewed the first version of
   `RideService` line by line but only skimmed the second pass (the comment
   and structure rewrite) and has not read the cue rules against
   `docs/specs/M4-riding.md` section 4.

Trips and places:
7. Trip polish from `docs/specs/M5-polish.md` not yet seen on screen: item 2
   (search line for a trip day and a GPX import), 4 (no "0.0 mm" on day
   cards), 6 (tapping "Best overall start" moves the trip), 7 (a day outside
   the forecast says so). Use the 240 km test GPX; make a new one if the
   scratch copy is gone.
8. Places list: headings are lower case ("water (12)"), unnamed taps read
   "Unnamed water", and tapping a place, "Add to export" and the longest
   gap without water or food per day have not been checked. Bike shops are
   in the roadmap line but check `PoiKind` actually includes them.
9. Saved trips opened without a connection, and the forecast-age message.

Older gaps still open (details under Known gaps): gust warnings never seen
on screen, the legacy WebP launcher icons, GPX import of a real Komoot or
Ride with GPS file on a device, the fortnightly scheduled build.

When riding mode has been confirmed on the phone, tick Milestone 4 in
`docs/ROADMAP.md`.

The Codex session for this work is `01a11cdf-ba06-74b1-b283-a8181b280ea2`
(`<newer codex.exe> exec resume <id> -m gpt-6.1-sol -c
model_reasoning_effort=low "..."`). At low effort the whole of riding mode
plus two fix passes took about 45 minutes and did not reach the limit.

Two Codex CLIs are installed. `codex` on the PATH is 0.154.0 and only knows
the 5.6 models. The desktop app's own copy under
`%LOCALAPPDATA%\OpenAI\Codexin\<hash>\codex.exe` is newer (0.162 on
2026-10-09) and offers `gpt-6.1-sol`, `gpt-6-sol` and `gpt-6-luna`. The hash
folder changes when the app updates, so look for it each time.

Codex model and effort: James's `~/.codex/config.toml` default is
`gpt-5.6-sol` at low effort. Claude ran it at high (three agents at once,
allowance gone in two hours) and then medium (one agent, about four hours).
James asked on 2026-10-09 for whatever is cheapest that does the job: use
`gpt-6.1-sol` at low for feature work and `gpt-6-luna` for polish and comment
passes, one agent at a time. `codex debug models`
lists what the account offers.

The laptop ran out of memory on 2026-10-09 with the emulator, Codex and
Gradle all running. Run one Gradle build at a time and close the emulator
when it is not in use.

## Known gaps

- Gust warnings have not been seen on screen: every test forecast was calm.
- The emulator on this laptop dies (no crash report, the process just goes)
  in ride mode, a few seconds after the map starts following at close zoom.
  Zooming in by hand also killed it until Vulkan was turned off
  (`-feature -Vulkan`), which fixed plain zooming but not ride mode. The
  tilt, the speech engine and `-no-audio` were each ruled out. The cause was
  not found, so it is not proven to be the emulator rather than the app:
  watch for a crash on James's phone.
- Fake GPS for a ride: `adb emu geo fix` works for one position. For a
  replay use `adb shell appops set com.android.shell android:mock_location
  allow` and `cmd location providers add-test-provider gps`.
- Geocoder suggestions could not be confirmed on the emulator: its Geocoder
  logs "forward geocoding network failure". Recent places do appear in the
  dropdown. Needs a check on James's phone.
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