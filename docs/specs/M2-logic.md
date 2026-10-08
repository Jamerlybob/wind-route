# Spec: Milestone 2 logic, "when should I leave?"

Read `docs/specs/COMMON.md` first. This task is **pure Java plus tests only**.
No Activity, layout, manifest or resource changes: another agent is rewriting
those right now, and Claude will wire your classes into the UI afterwards.

You own the `weather/` package. Everything else you add is a NEW file. Do not
edit `wind/WindMath.java`, `wind/RouteWind.java`, `route/Route.java` or
`route/RoutesClient.java`; call them as they are.

## 1. A longer, richer forecast (`weather/`)

Today `OpenMeteoClient` asks for wind only, for 2 days. Extend it:

- Add hourly `temperature_2m`, `precipitation` (mm) and
  `precipitation_probability` (%) to the request.
- `forecast_days=8`, so a departure up to 7 days ahead is covered (the API
  allows up to 16).
- Carry the new values in `WindForecast` (or a class beside it). Keep the
  existing 4-argument constructor and `OpenMeteoClient.fetch(List<GeoPoint>)`
  signature working: `MainActivity` and existing tests use them.
- Keep `timeformat=unixtime&timezone=GMT`.
- A missing hour is `null` in the JSON. Wind treats it as 0; for temperature
  and rain keep "unknown" distinguishable from 0 (for example `Double.NaN`)
  and document it.

Verified against https://open-meteo.com/en/docs on 2026-10-08: the variable
names above, comma-separated `latitude`/`longitude` for several places, a JSON
array back for several places and a single object for one, errors as
`{"error": true, "reason": ".."}` with HTTP 400.

## 2. Weather along the ride (`weather/RideWeather.java`, new)

Given a route, sample indexes, forecasts and a departure time, return for each
sample point the arrival time there, temperature, rain in mm and rain
probability. Plus totals: wettest hour, chance of any rain on the ride,
coldest and warmest point. Time along the route is spread evenly over the
duration, exactly as `RouteWind.analyze` does it.

## 3. Best time to leave (`wind/DepartureScorer.java`, new)

- Input: route, sample indexes, forecasts, a first departure time and a count
  of hours (default 24). Output: one entry per hourly departure with the
  `RouteWind` summary numbers (average headwind, shares, max gust) and, when
  rain data is present, the ride's rain.
- A single score per hour so the UI can draw a strip and mark the best one.
  Lower net headwind is better; rain and strong gusts are penalties. Put the
  weights in named constants and explain the reasoning in comments. It must be
  obvious to a reader why one hour beats another.
- Never score a departure whose ride would run past the end of the forecast
  data; leave those hours out.
- No network: this reuses the forecast already fetched.

## 4. Gust warnings (`wind/GustWarnings.java`, new)

From a `RouteWind`, list the stretches worth warning about: gusts above a
threshold (named constant, default 45 km/h) where the wind is across the road,
since a gusty crosswind is what pushes a rider sideways. Merge neighbouring
stretches into one warning with its start distance, length and side
(left/right, from `WindMath.crosswindComponent`'s sign... note `RouteWind
.Stretch` does not carry the wind direction; if you need it, compute it in
your own class from the forecast rather than editing `RouteWind`).

## 5. What the wind costs in minutes (`wind/PowerModel.java`, new)

The standard cycling power equation on a flat road:

    P = (0.5 * rho * CdA * (v + w)^2 * v) + (Crr * m * g * v)

where v is ground speed (m/s), w the headwind component (m/s, negative for a
tailwind), rho air density, CdA drag area, Crr rolling resistance, m rider
plus bike mass.

- Named, commented default constants for an everyday rider (rho 1.225, CdA
  about 0.40 upright, Crr about 0.005, m about 85 kg). State in the Javadoc
  that these are assumptions; the UI will repeat them.
- Step 1: from the rider's still-air speed (route distance / duration) solve
  for the power they are assumed to hold.
- Step 2: for each `RouteWind.Stretch`, solve for the speed that same power
  gives against that stretch's headwind. There is no closed form worth
  reading: use bisection, and comment why it converges (power rises
  monotonically with speed).
- Output: ride time in still air, ride time with this wind, and the
  difference in minutes. Guard the edge cases: a strong tailwind must not
  produce absurd speeds (cap, with a comment), zero-length stretches, zero
  duration.
- Tests: no wind gives zero difference; a headwind costs more time than the
  same tailwind saves (the asymmetry is real and worth a test and a comment);
  an out-and-back in steady wind is slower than in still air.

## Tests

Every class above gets JUnit 4 tests with hand-checkable numbers, in the style
of `RouteWindTest.java`. Use small made-up routes and forecasts, never the
network.
