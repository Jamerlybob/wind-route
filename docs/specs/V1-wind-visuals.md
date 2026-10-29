# Spec: wind you can see (V1)

Read `docs/specs/COMMON.md` first, then `STATE.md`. James, 2026-10-30: the app
works; now make it grab people. Visual quality is the priority, and wind is
the star of every screen. Keep the code readable for a Java learner.

Everything here uses Open-Meteo (free) and the Maps SDK (free). No new Google
calls, no new libraries.

## 0. A fresh start every time

James: "when the user re-opens the app it should be reset, not have the
latest nav on it". On a cold start show the map at the rider's position
(or the last camera position if location is not granted) with empty From and
To fields, no route and no sheet. Keep recent places in the suggestions.
Add one chip under the search card, "Last: Mission Bay → Auckland Domain",
that restores the saved route (as today, no Routes call) when tapped, and
hides once a new search starts. A rotation or returning from Settings must
still keep the current route (that is `RouteState`, not a cold start).

## 1. The wind field

- `weather/WindGridClient`: one Open-Meteo forecast request for a grid of
  points. The forecast API takes comma-separated `latitude` and `longitude`
  lists and then returns a JSON list, one object per location (checked on
  https://open-meteo.com/en/docs on 2026-10-30; it gives no limit, so keep a
  grid to at most 10 x 10 = 100 points). Ask for hourly `wind_speed_10m`,
  `wind_direction_10m`, `wind_gusts_10m`, unixtime, GMT, as
  `OpenMeteoClient` does. `buildUrl` and `parse` tested with a saved reply.
- Open-Meteo's free tier is 600 calls a minute and 10,000 a day. Fetch the
  grid only when the camera settles (`OnCameraIdleListener`) on an area not
  already covered by the cached grid, or at a different hour; debounce by
  1 s; never more than one request in flight. Comment the budget.
- `wind/WindField` (pure Java, tested): holds the grid for one hour and
  answers `at(lat, lng)` with east and north components (u, v) by bilinear
  interpolation. Interpolate u and v, never the direction angle (350 and 10
  degrees average to 0, not 180; put that in a comment and a test).
  Interpolate linearly in time between hours, too.

## 2. Wind moving across the map

A custom `WindFlowView` drawn over the map (a transparent View above the
`MapView`, not a map overlay), the effect of earth.nullschool or Windy:

- About 800 particles at random screen positions. Each frame, move each one
  by the wind at its position, converted to pixels with the map's
  `Projection` and the current zoom, scaled so speed reads well at any zoom.
  Draw short fading trails: draw onto an offscreen bitmap that is faded a
  little every frame, then drawn to the canvas. Particles die after a random
  lifetime or when they leave the screen and are reborn at random.
- Colour by speed on one shared scale (calm, pale, through to strong, deep),
  and keep it readable over both the light and dark map styles.
- While the camera moves, clear the trails and hide the particles; resume on
  idle. Stop animating when the Activity is paused, in ride mode, and when
  the layer is off. Aim for 30 frames a second with `postInvalidateOnAnimation`
  and say in a comment why the work per frame is kept small.
- Pure-Java parts tested: particle advection step, respawn rule, colour
  scale.

## 3. A wind speed heatmap

A `GroundOverlay` bitmap for the grid's area, coloured by speed on the same
scale as the particles, smoothly interpolated (draw a small bitmap, one pixel
per sample at say 64 x 64 from `WindField`, and let the overlay scale it with
filtering), at about 55 percent opacity. A small legend in a corner: the
colour ramp with three labelled speeds in the rider's units.

## 4. Layers button

A round "Layers" button under the my-location button opens a small sheet
with switches: "Moving wind", "Wind speed", and a time chip row: the hour
shown (defaults to the planned departure, or now). The choice is saved in
settings. Both layers work with and without a route. With a route, the
departure strip's selected hour drives the layers' hour too.

## 5. Better wind on the route

- Sample the route more densely: every 2 km up to 60 km routes, capped at
  100 samples (Open-Meteo multi-location, one request; check how
  `RouteForecastLoader` requests today and keep it to one or two requests).
- Ask for `minutely_15` wind where it helps: it is native in North America
  and Central Europe and interpolated elsewhere (same docs page). Use it
  when the ride starts within the next 48 hours, hourly otherwise, and
  interpolate in time to the moment the rider reaches each point.
- Between samples, interpolate u and v along the route instead of using the
  nearest sample, so the colour changes smoothly.
- Tests for each of these, including a ride that crosses an hour boundary.

## 6. A route that looks the part

- Draw the route as a white casing with the coloured line on top, wider
  (8 dp casing, 5 dp line), round caps and joints.
- Colour intensity by strength: a light headwind is a lighter red than a
  strong one. Keep the four colours and the existing legend meaning.
- Small arrows along the route every few hundred pixels (spaced in screen
  space so they never crowd), pointing the way the wind blows, coloured by
  the verdict there. Redraw on camera idle.
- The sheet headline gets a small wind badge: a circle with an arrow showing
  the wind relative to the overall direction of travel and the speed under
  it.

## 7. Before a route: "where to ride today"

With no route shown, a card at the bottom: the wind at the rider's position
now and for the next few hours ("SW 22 km/h, easing to 12 by 3 PM") and a
suggestion for a loop: "Ride out into it, north-east is a tailwind home".
Pure Java for the wording, tested. One Open-Meteo request, the same grid
call if it already covers the position.

## Checking

Unit tests for all pure logic. Lint clean. One Gradle build at a time; skip
the emulator (Claude will run it). Do not commit.
