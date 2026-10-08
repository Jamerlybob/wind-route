# Spec: Milestone 1, something you open every day

Read `docs/specs/COMMON.md` first. This task owns `MainActivity`, the layouts,
`RouteState`, `route/RoutesClient`, `route/Route`, `wind/WindMath` and
`wind/RouteWind`. Do not touch the `weather/` package: another agent is
working there.

`MainActivity` is already near 400 lines. Do not let it balloon: each feature
below gets its own small class, and the Activity only wires them together.

## 0. Fix: the departure chips are clipped

On a 360 dp wide phone the "In 3 h" chip is cut off behind the Show wind
button. Rearrange the search card so nothing is ever clipped at 360 dp. The
card also has to fit the new swap, my-location and settings controls below, so
design the card once for all of them. Keep it compact: the map is the point.

## 1. "My location" as the start

- A location icon at the end of the From field. Tapping it asks for location
  permission (only then, never at launch), and on success puts
  "My location" in the field.
- Ask for `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` together and
  accept either. If refused, show a one-line explanation and carry on working
  with typed addresses. Never nag.
- Use the platform `LocationManager` through `LocationManagerCompat
  .getCurrentLocation` (androidx.core is already on the classpath). Do NOT add
  play-services-location.
- When the origin is the device's position, `RoutesClient` sends coordinates
  instead of an address. The waypoint shape is
  `{"location": {"latLng": {"latitude": .., "longitude": ..}}}`. Unit test
  the request body for both forms.
- Once permission is held, show the blue dot (`map.setMyLocationEnabled`).

## 2. Place suggestions while typing

- Both fields become `MaterialAutoCompleteTextView` (it is in the Material
  library already) and suggest up to 5 places from `android.location.Geocoder`
  (`getFromLocationName`), plus matching recent places (item 5) listed first.
- Geocoder blocks, so run it on a background thread. Wait 400 ms after the
  last keystroke and require at least 3 characters. Drop a result if the text
  has changed since it was asked for.
- Bias results to the area the map is showing, using the bounding-box
  overload of `getFromLocationName`.
- Geocoder can be missing or return nothing (`Geocoder.isPresent()`); the
  fields must then behave exactly as plain text fields do today.
- Turning an `Address` into one readable line is pure string work: put it in a
  plain Java helper and test it.

## 3. Swap, and both directions compared

- A swap button exchanges From and To. If a route is on screen it re-runs the
  search (the user tapped; one Routes call is fine).
- In the summary card add one line comparing the other direction, for example
  "Ride it the other way: 70% tailwind". Compute it with NO network call: run
  `RouteWind.analyze` over the same points in reverse order with the same
  forecasts. Add what is needed to `Route`/`RouteWind` for that (for example a
  `Route.reversed()`), with tests. Only show the line when the other
  direction is meaningfully different; define "meaningfully" as a named
  constant with a comment.
- Be honest in the wording: the return road may differ. Keep it short.

## 4. Settings screen

A second Activity, `SettingsActivity`, opened from a gear icon. Plain XML
views and `SharedPreferences`. Do NOT add androidx.preference.

| Setting | Choices | Default |
|---|---|---|
| Distance | km, miles | km |
| Wind speed | km/h, mph, m/s, knots | km/h |
| Usual riding speed | "Use Google's estimate", or 10 to 40 km/h | Google's |
| Theme | system, light, dark | system |
| Calm below | 0 to 15 km/h | 5 |

- A plain Java `Settings` value object plus a small Android class that loads
  and saves it. Unit conversion and formatting live in a plain Java class with
  tests (`units/` package). Every distance and speed on screen goes through it.
- A riding speed, when set, replaces Google's duration: time = distance /
  speed. This must flow into `RouteWind.analyze`, which uses the duration to
  pick forecast hours.
- The calm threshold is passed into `WindMath.classify` / `RouteWind.analyze`
  as a parameter. Keep the existing signatures working as overloads so current
  tests still pass.
- Theme uses `AppCompatDelegate.setDefaultNightMode`, applied at startup.
- Coming back from settings redraws the route with the new values, with no
  network call.

## 5. Remember the last route and recent places

- Recent places: the last 8 distinct texts searched, newest first, offered as
  suggestions (item 2) and shown when a field is focused and empty.
- Last route: after a successful search, save the two field texts and the
  route. On a cold start, restore the fields and the route, then fetch a fresh
  forecast from Open-Meteo (free) and draw. NO Routes call on restore.
- Store with `SharedPreferences` and `org.json`. Turning a `Route` to and from
  JSON is pure Java: write it as such and test the round trip.
- If the saved data is unreadable, start empty. Never crash on old data.

## 6. Google's cycling notice

Google's Routes documentation says of bicycle routes: "You must display this
warning to the user for all walking, bicycling, and two-wheel routes that you
display in your app." The warning is that such routes "are in beta and might
sometimes be missing clear ... bicycling paths".

So: whenever a route is shown, always show a short fixed notice to that effect
in the summary card, followed by anything in `routes.warnings`. Do not show
the same sentence twice if Google also returns it.

## Icons

Hand-written vector drawables in `res/drawable` (24 dp, tinted with
`?attr/colorOnSurfaceVariant`). Every icon button needs a
`contentDescription`.

## Out of scope

Departure time picker, rain, elevation, navigation. Leave the three departure
chips working as they do now.
