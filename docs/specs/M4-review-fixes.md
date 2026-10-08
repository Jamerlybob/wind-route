# Review fixes for riding mode and places

Read `docs/specs/COMMON.md` first. Claude built and ran your Milestone 4 and
polish work: 137 tests pass, lint is clean, a Google route loads with its
steps, ride mode opens (banner, heading-up map, profile marker, wind strip,
Stop), and the places lookup returned 208 places on Mission Bay to Auckland
Domain. Fix these. Work economically; do not re-read what you wrote.

## What the rider sees

1. **The ride banner shows the step being ridden, not the next turn.** At the
   start it read "0.0 km: Head north on Patteson Ave toward Nihill Cres".
   The banner must show the NEXT manoeuvre and the distance to it:
   "390 m · Turn right onto Tamaki Dr". Only before the first fix, or when
   the rider is within about 30 m of the start, show the DEPART instruction,
   with no distance. Put the choice in `nav/TurnGuide` (pure Java) with
   tests, including the very short steps Google returns (6 m, 21 m, 8 m in
   that route): the banner should never flash a step that is already behind.
2. **Distances under 1 km in ride mode are in metres** (or feet/yards-free:
   feet under 0.2 mi for imperial), rounded the way `CueBuilder` rounds
   spoken ones. "0.0 km" must never appear in ride mode. One shared
   formatter for the banner and the spoken cue.
3. **"Start ride" is buried under the whole sheet.** It took three swipes to
   find. Put it in the collapsed part of the sheet so it is visible without
   expanding: a filled button under the wind share bar. It is the one filled
   button in the sheet; "Find water, food and camping" becomes tonal.
4. **Places are drawn as 208 filled buttons.** Make them plain list rows:
   name on the first line, "Cafe · 0.4 km along · 80 m off route" on the
   second, kind first. Group under "Water", "Food", "Camping" headings with
   a count, each group collapsed to its first 5 with a "Show all N" row.
   Distances off the route under 1 km in metres.
5. **"8.4 km · 28 min· 42 m up"** has no space before the second dot. Check
   `details_primary` and the place that builds its third argument (Android
   strips leading spaces in string resources; add them in code or use
   ` `).
6. **"adds 184 min"** on a long route. From 60 minutes up, say
   "adds 3 h 4 min". Same for "saves" and the long form sentence.

## Code

7. **`MainActivity` has grown ride code full of fully qualified names**
   (`io.github.jamerlybob.windroute.units.UnitText` and so on, written out
   inline). Use imports. Move the ride screen (showing and hiding ride mode,
   `onRideUpdate`, the camera, the rider marker, the wind text) into a
   `RideScreenController` beside `RideSheetController`, the same way the
   sheet and search card are separated. The Activity keeps permission
   requests and binding.
8. **`RideService` is too thin on comments for someone who has never seen a
   Service.** At the density of `WindMath.java`, explain: what a foreground
   service is and why the notification is required; started versus bound and
   why this one is both; why the plan is handed over through the static
   `prepare` (an Intent cannot carry it; say what the size limit is about);
   what audio focus and ducking are; why `onStartCommand` returns
   `START_NOT_STICKY`. Group the fields and give the long methods
   (`onStartCommand`, `onLocationChanged`) short named helpers.
9. `RideUpdate` mixes final fields with public mutable ones set afterwards.
   Make it immutable (all fields final, set in the constructor or a small
   builder).
10. The camera in ride mode asks for zoom 17, tilt 45 on every fix with
    `animateCamera`. Only move the camera when the position or bearing has
    changed meaningfully (more than about 3 m or 5 degrees), and do not
    restart an animation that is still running. Comment why (battery, and a
    jittery map when standing at lights).

## Checking

Unit tests for 1, 2 and 6. `testDebugUnitTest lintDebug assembleDebug` must
pass; one Gradle build at a time. Skip the emulator: on this laptop it
crashes when the map moves at close zoom, so Claude will check the screens.
Do not commit.
