# Spec: logic for hills, riding and trips (Milestones 3 to 5)

Read `docs/specs/COMMON.md` first. This task is **pure Java plus tests only**,
and **every file you add is new**. Do not edit any existing source file: two
other agents are working in `MainActivity`, the layouts, `wind/`, `route/` and
`weather/` right now. Call existing classes (`GeoPoint`, `GeoMath`, `Route`,
`RouteWind`, `Http`) as they are. Claude wires your classes into the UI later.

New packages: `elevation/`, `gpx/`, `trip/`, `nav/`.

## 1. Elevation (`elevation/ElevationClient.java`)

Open-Meteo's elevation API, verified against
https://open-meteo.com/en/docs/elevation-api on 2026-10-08:

- `GET https://api.open-meteo.com/v1/elevation?latitude=a,b&longitude=c,d`
- At most 100 coordinates per request.
- Reply: `{"elevation": [38.0, ...]}`, always an array, metres.
- Errors: HTTP 400 with `{"error": true, "reason": ".."}`.
- Data is Copernicus GLO-90, a 90 m grid. No key, free for non-commercial use.

Follow `OpenMeteoClient`'s shape: `buildUrl`, `parse` and `fetch` as separate
static methods so the first two are testable without the network. Batch in
groups of 100. Use `Locale.US` when formatting coordinates.

Choosing which points to ask for is its own testable method: evenly spaced
along the route about every 100 m, but never more than 500 points in total
(5 requests), widening the spacing on a long route. Because the grid is 90 m,
asking more often than that gains nothing; say so in a comment.

## 2. Profile and climbs (`elevation/ElevationProfile.java`, `ClimbDetector.java`)

- `ElevationProfile`: distance along the route and height for each sample,
  total ascent and descent, gradient between samples. A 90 m grid is noisy on
  steep ground and adds phantom climbing, so smooth before summing (a short
  moving average) and ignore rises smaller than a named threshold. Explain
  both in comments; a learner should understand why raw summing overcounts.
- `ClimbDetector`: find the climbs. Reference points are Garmin ClimbPro and
  Wahoo Summit; Wahoo's minimum climb length is 400 m. A climb here is at
  least 400 m long, averages at least 3%, and gains at least 15 m. A short dip
  or flat (named constant) inside a climb does not end it. Each climb reports
  start distance, length, gain, average gradient and steepest 100 m gradient.
  All thresholds are named constants.
- `elevation/ClimbWind.java`: given a climb and a `RouteWind`, the
  distance-weighted average headwind on that climb and a flag for the case
  WindRoute wants to own: a climb into a headwind. Threshold as a named
  constant.

## 3. GPX (`gpx/GpxParser.java`, `gpx/GpxWriter.java`)

- Parse GPX 1.1 tracks (`trk/trkseg/trkpt`) and routes (`rte/rtept`) into a
  list of `GeoPoint`, plus elevations and waypoints (`wpt` with `name`) when
  present. Tolerate GPX 1.0 and files from Komoot, Ride with GPS, Strava and
  Garmin: ignore extensions and unknown elements.
- Use `javax.xml.parsers.DocumentBuilderFactory`, which exists both on Android
  and in plain JVM unit tests. Turn off DTDs and external entities (a GPX file
  comes from outside and must not be able to read local files); comment why.
- A bad file throws `IOException` with a message fit to show a user.
- `GpxWriter` writes a route as a GPX 1.1 track that Garmin and Wahoo accept,
  escaping names properly. Test a parse, write, parse round trip.

## 4. Splitting a trip into days (`trip/DaySplitter.java`)

Given cumulative distance and (optionally) an elevation profile, split a long
route into days: by target distance per day, or by a target effort that
counts climbing (state the rule, for example 100 m of climbing counts as 1 km
of distance, as a named constant with a comment on where the rule of thumb
comes from). Also accept hand-picked split distances. Each day reports its
start and end point indexes, distance and ascent. The last day must not be a
stub: if it would be under a third of the target, even the days out instead.

## 5. Where am I on the route? (`nav/RouteProgress.java`)

Given the route points and a position: the nearest point on the polyline
(project onto segments, do not just pick the nearest vertex), distance along
the route, distance remaining, and distance off the route. Off-route is
"further than a named threshold (40 m) from the line". Where a route crosses
or doubles back on itself the nearest segment can be the wrong one, so accept
the previous distance-along as a hint and prefer a match at or just after it.
Explain that in a comment.

For projecting onto a segment, treat the neighbourhood as flat (equirectangular
approximation); explain why that is accurate enough over tens of metres and
would not be over hundreds of kilometres.

## Tests

JUnit 4, hand-checkable numbers, no network, in the style of
`RouteWindTest.java`. GPX tests use short GPX strings written in the test.
