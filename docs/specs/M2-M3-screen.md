# Spec: put "when should I leave?" and hills on screen (Milestones 2 and 3)

Read `docs/specs/COMMON.md` first. The logic already exists and is tested:
`wind/DepartureScorer`, `wind/PowerModel`, `wind/GustWarnings`,
`weather/RideWeather`, `elevation/ElevationClient`, `ElevationProfile`,
`ClimbDetector`, `ClimbWind`. This task is the screen. Read those classes
before designing anything. You may add small methods or overloads to them
(with tests) but do not rewrite them.

Keep `MainActivity` a thin wiring class. Each piece below is its own class.

## 0. Make the logic respect the settings

Milestone 1 lets the rider set a usual speed (which replaces Google's
duration) and a calm threshold, and passes both into `RouteWind.analyze`. The
Milestone 2 classes were written in parallel and still use
`route.durationSeconds` and the default threshold. Everything on screen must
agree, so every analysis must use the same effective duration and threshold.
Do it in one place (for example build the effective `Route` once, and add the
threshold overloads where missing) and test it.

## 1. Give the map back: a bottom sheet and a collapsing search card

The two cards now cover half the map, and this milestone adds more.

- The summary card becomes a Material bottom sheet (`BottomSheetBehavior`,
  already in the Material library). Collapsed, it shows what it shows today:
  headline, details line, the other-direction line, share bar and legend.
  Dragged up, it reveals the sections below in a scrolling column. Google's
  cycling notice stays visible in the collapsed state (it is required).
- Once a route is on screen the search card collapses to one line, for
  example "Mission Bay → Mt Eden · Now", with the settings icon. Tapping it
  opens the full card again. With no route, the full card shows.
- The map padding and camera must keep the whole route visible between the
  collapsed search line and the collapsed sheet.
- System back: an expanded sheet collapses first; an open search card with a
  route on screen collapses first.

## 2. Departure time

- Replace the three chips with: "Now", and "Leave at ..." which opens
  `MaterialDatePicker` then `MaterialTimePicker` (both in the Material
  library). Only today to 7 days ahead can be chosen. The chosen time shows on
  the chip in the phone's own locale and 12/24 hour setting ("Sat 07:30").
- The forecast is now 8 days and bigger. Changing the departure time must
  still redraw from memory with no network call.
- If the data fetched earlier no longer covers the chosen ride (the app sat
  open for a day), refetch the free forecast, never the route.

## 3. Best time to leave (first section of the sheet)

A custom View, `DepartureStripView`: one bar per hour for the next 24 hours
from `DepartureScorer`.

- Bar height and colour show the net wind: tailwind teal upward, headwind
  red-orange downward from a centre line, using the existing `wind_*` colours.
  A small rain mark on hours where the ride would be wet. The selected hour is
  outlined; the best-scoring hour carries a small "best" label.
- Hour labels along the bottom every 3 hours, in local time.
- Tapping a bar selects that departure and recolours the route.
- One line above it in words: "Best: leave at 14:00, 4 km/h tailwind, dry".
- Draw it with `Canvas` in `onDraw`. Comment the drawing maths; James has not
  seen a custom View before. Support dark and light. Give it a content
  description that says the same as the line in words.

## 4. Weather on the ride

In the sheet: temperature range and rain for the ride at the chosen time from
`RideWeather`, in words ("12 to 15 degrees. 40% chance of rain, up to 1.2 mm
an hour around 15:00."). Say nothing about rain data when it is unknown
rather than "0". Temperature respects a new Celsius/Fahrenheit setting.

Add the rain chance to the collapsed details line only when it is 30% or more.

## 5. What the wind costs

From `PowerModel`: "The wind adds about 6 minutes" or "saves about 4
minutes", shown in the collapsed details line in place of nothing, and in the
sheet with an info icon that opens a dialog listing the model's assumptions
from its constants (upright rider, 85 kg with bike, flat road, same effort as
in still air). Under one minute either way, say "no real difference".

## 6. Gust warnings

From `GustWarnings`: in the sheet, a line per warning ("Gusts to 52 km/h from
the left for 1.2 km, starting at 8.4 km"), and on the map draw those stretches
with a dashed outline so they are findable. No warnings, no section.

## 7. Hills

- After a route arrives (and after a restore), fetch elevation with
  `ElevationClient` on the background thread. It is free and at most 5
  requests. A failure here must not hide the wind result: show the route and
  leave the hills section out with one quiet line.
- Total ascent goes in the collapsed details line ("12.9 km · 50 min ·
  180 m up").
- A custom View, `ElevationProfileView`, in the sheet: the height profile as a
  filled area, coloured along its length by each stretch's wind verdict so the
  rider sees wind and hills together. Climbs from `ClimbDetector` are marked
  along the top. Distance labels along the bottom, lowest and highest point on
  the side, in the chosen units (add metres/feet to settings). Same standard
  of commenting as the strip.
- Under it, a list of the climbs: "Climb 2 at 8.4 km: 1.2 km, 74 m, 6% (max
  9%)". Where `ClimbWind` flags it, add "into a 14 km/h headwind" and mark it
  on the profile. This is the combination WindRoute wants to be known for, so
  make it stand out, and add "N climbs into the wind" to the collapsed details
  line when N is not zero.
- Save the elevations with the route in `RouteStore` so a restore does not
  need to fetch them again.

## 8. Tap a stretch

Tapping the route line on the map shows that stretch's facts in a small card
or info window: distance from the start, when you will be there, wind speed,
head/tail/cross component, gust, and gradient if hills are loaded. Polylines
must be made clickable for this; keep the merged-run drawing.

## 9. Credit the data

Open-Meteo's data is licensed CC BY 4.0 and its elevation page asks for
attribution to Open-Meteo and to the Copernicus programme. Add an "About the
data" block at the bottom of the settings screen: weather and elevation from
Open-Meteo.com, elevation data from Copernicus DEM GLO-90, routes and maps
from Google. Plain text is enough.

## Checking

Run it on the emulator (AVD `Medium_Phone`, recipe in `AGENTS.md`; one may
already be running, leave it running). The emulator has a saved route, so a
launch restores it without spending a Routes call; do not run more than two
new route searches. Look at screenshots of: collapsed state, expanded sheet
scrolled through, the strip after tapping a different hour, the date and time
pickers, and light mode. Fix what looks wrong before finishing.
