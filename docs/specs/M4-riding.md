# Spec: riding with it (Milestone 4)

Read `docs/specs/COMMON.md` first, then `STATE.md`. Do this after
`M2-M3-screen.md`. Existing logic: `nav/RouteProgress` (snap to the route,
distance along, off-route), `elevation/ClimbDetector`, `ClimbWind`,
`wind/RouteWind`, `wind/GustWarnings`.

This milestone touches permissions, a foreground service and battery. Keep it
plain and well explained: James has not seen a Service before.

## 1. Turn-by-turn steps from Google

- Add to the Routes field mask: `routes.legs.steps.distanceMeters`,
  `routes.legs.steps.navigationInstruction`,
  `routes.legs.steps.startLocation`, `routes.legs.steps.endLocation`
  (field names checked against the computeRoutes reference on 2026-10-08:
  `navigationInstruction` has `maneuver` and `instructions`).
- Parse them into a list of steps on `Route` (pure Java, tested) and save them
  with the route. Old saved routes without steps must still load.
- Note in your final message that the field mask changed, so James can check
  the SKU in his Google billing report: Google bills by fields requested and
  we could not confirm from the docs that steps stay on the cheapest tier.
- A GPX import has no steps. For those, navigation still works with position,
  wind and climb cues, and no turn instructions. Do not invent turns.

## 2. Ride mode

A "Start ride" button in the bottom sheet when a route is shown.

- The map follows the rider, heading up, zoomed in. A top banner shows the
  next instruction and distance to it; a bottom strip shows distance left,
  time left, and the wind right now on this stretch (an arrow relative to the
  direction of travel, and "headwind 14 km/h").
- The elevation profile shows where the rider is.
- A "Stop" button, and the screen stays on (`FLAG_KEEP_SCREEN_ON`) while ride
  mode is in front.

## 3. Location, kept running with the screen off

- A foreground service of type `location` with an ongoing notification
  ("WindRoute is guiding your ride", with a Stop action). Manifest:
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`,
  and `android:foregroundServiceType="location"` on the service. Location
  permission must already be granted before `startForeground`; the service is
  only ever started from the visible Activity, so background location
  permission is not needed and must not be requested.
- Ask for notification permission (Android 13+) when the ride starts, with
  one line of explanation. If refused, the ride still works.
- Positions come from `LocationManager` (GPS provider, falling back to
  network) through `LocationManagerCompat.requestLocationUpdates`. No
  play-services-location. About one fix a second while moving is plenty;
  explain the battery trade-off in a comment.
- Ride mode needs precise location. If only approximate was granted, ask for
  the upgrade once and explain why.
- The service owns the ride state so it survives the Activity being
  destroyed; the Activity binds to it or observes it and redraws. Stopping the
  ride, or arriving, stops the service and removes the notification.

## 4. Spoken cues

`android.speech.tts.TextToSpeech`. Deciding WHAT to say and WHEN is pure
Java (`nav/CueBuilder`, `nav/CuePlanner`) with thorough tests; the service
only speaks what it is given.

Research could not find published wording or timing for Komoot or Google
Maps, so these are our own rules; keep them as named constants:

- Turns: once at about 300 m ("In 300 metres, turn left onto Queen Street")
  and once at about 40 m ("Turn left"). If two turns are closer together than
  that, say the first and chain the second ("then turn right").
- Climbs, at about 400 m before the start: "Climb in 400 metres. 1.2
  kilometres at 6 percent." With `ClimbWind`: "...into a headwind."
  At the top: "Top of the climb."
- Wind, when the verdict changes and will last at least 2 km: "Headwind for
  the next 5 kilometres." "Tailwind from here to the finish."
- Gusty crosswind from `GustWarnings`, about 300 m before: "Strong crosswind
  from the left for the next kilometre."
- Never talk over yourself: queue cues, drop one that is no longer true by
  the time it would be spoken, and leave at least 10 seconds between
  non-turn cues. Turns always win.
- Round distances the way a person would say them (50 m steps under 1 km,
  one decimal above). Units follow the settings.
- Audio focus: request transient focus that ducks music while speaking, and
  give it back.

Settings: which cues (turns, climbs, wind, gusts), how early, and a "less
talk" switch that keeps only turns and climbs into the wind.

## 5. Off route

From `RouteProgress.offRoute`, sustained for 10 seconds and 3 fixes so one bad
GPS fix does not trigger it: say "Off route" once and show a banner with
"Re-route from here". Re-routing is a Google Routes call, so it happens ONLY
when the rider taps that button, never automatically. Explain the quota
reason in a comment.

## 6. Arriving

Within 30 m of the finish: "You have arrived", a short ride summary (distance,
time, the wind you had), and the service stops.

## Checking

Pure logic gets unit tests, including a simulated ride along a made-up route
that asserts the exact sequence of cues. On the emulator, replay a route with
`adb emu geo fix <lng> <lat>` at intervals from a small script and take
screenshots of ride mode, the notification, and the off-route banner. Say
plainly in the final message that spoken audio and real GPS could not be
checked on an emulator and need James's phone.
