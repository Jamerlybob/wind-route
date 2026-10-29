# Spec: easy bikepacking (V2)

Read `docs/specs/COMMON.md` first. Do this after `V1-wind-visuals.md`. James,
2026-10-30: "not overly complicated bikepacking stuff but goated stuff", for
example "how much do you want to cycle today, roughly 50 km? OK, here is
where you should stop." Simple to use, wind front and centre. Build on what
exists: `trip/DaySplitter`, `TripPlanner`, `TripActivity`, `poi/*`,
`DaylightClient`.

## 1. "How far today?"

In the sheet for any route over 30 km: a row "How far today?" with a slider
(10 to 200 km, steps of 5, default 50, or the rider's last choice) and a
toggle between distance and time ("3 h of riding").

- A tent marker appears on the map at that point, and the sheet says
  "Stop near Huntly · 52 km · about 2 h 50 with this wind · arrive 11:40 AM".
  The time uses the wind (`PowerModel`/`RouteWind`), not flat speed; say
  how much the wind adds or saves ("the wind adds 20 min").
- The place name comes from the platform `Geocoder` reverse lookup (free);
  fall back to "52 km along" when it gives nothing.
- Snap the stop to something useful when there is one within 5 km either
  way: a campsite first, then a town with food, using the places already
  found if the rider has run the lookup. If none has been run, offer
  "Find camping near here" which runs Overpass for a small box round that
  point only (one request, a button, never automatic).
- Warn plainly if the stop is after sunset ("you would arrive 40 min after
  sunset") using `DaylightClient`.
- Pure Java for picking and snapping the stop, with tests.

## 2. Multi-day, made simple

The trip screen already splits a route into days. Make it the friendly
version:

- Lead with one question: "How far a day?" (the same slider), with
  "Number of days" as the alternative. Everything else (count the climbing,
  start time, the 40 km water warning) goes under "More options", collapsed.
- A map at the top showing each day's section in its own tone with numbered
  overnight markers (tent icons), instead of one blue line.
- Each day card leads with a wind verdict badge and one sentence
  ("Day 2 · Sat · 68 km · tailwind most of the way, dry"), then the details.
  A bad day (strong headwind or rain) gets a clear tint and a suggestion:
  "Leave a day later: headwind drops from 25 to 10 km/h".
- Overnight stops snap to campsites or towns the same way as section 1.
- Pure Java for the day sentence and the "leave a day later" suggestion,
  tested.

## 3. Look

Same visual bar as V1: Material 3, tonal surfaces, the wind colours used
consistently, no walls of filled buttons, everything readable in dark mode.
Icons as vector drawables you draw yourself (tent, drop, fork, layers);
no icon library.

## Checking

Unit tests for the pure logic, lint clean, one Gradle build at a time, skip
the emulator, do not commit.
