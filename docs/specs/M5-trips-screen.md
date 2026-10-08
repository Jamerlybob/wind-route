# Spec: trips on screen (Milestone 5)

Read `docs/specs/COMMON.md` first, then `STATE.md`. Do this only after
`M2-M3-screen.md` is in: it builds on the bottom sheet and the hills section.
The logic exists and is tested: `gpx/GpxParser`, `gpx/GpxWriter`,
`trip/DaySplitter`, `poi/OverpassClient`, `poi/PoiAlongRoute`. Read them
first. You may add small methods to them, with tests.

The idea, from `docs/RESEARCH.md`: WindRoute does not try to out-plan Komoot
or Ride with GPS. It imports their routes and is the best answer to "what will
the weather do to this ride", day by day.

## 1. Import and export GPX

- An overflow menu on the search card: "Import GPX", "Export GPX", "Plan as a
  trip", "Settings" (move the gear in here if space is tight).
- Import uses the Storage Access Framework (`ActivityResultContracts
  .OpenDocument`), so no storage permission is needed. Also register the app
  to open `.gpx` files from a file manager or a share ("Open with").
  GPX has no reliable MIME type: accept `application/gpx+xml`,
  `application/octet-stream`, `text/xml` and `application/xml`, and check the
  content, not the name.
- An imported route is a `Route` like any other: distance from the points,
  duration from the rider's usual speed setting (if it is "Use Google's
  estimate", use 18 km/h and say so once). It gets wind, weather and hills
  exactly as a searched route does, and is saved and restored the same way.
  No Google Routes call is made for it. If the GPX carries elevations, use
  them and skip the elevation request.
- A big file can have tens of thousands of points. Thin it to a sensible
  number for drawing and analysis (pure Java, tested) while keeping the shape.
- Export writes the current route with `ActivityResultContracts
  .CreateDocument`. Climbs and, later, chosen places go in as waypoints.
- Show Google's cycling notice only for routes that came from Google.

## 2. A trip is several days

A second screen, `TripActivity`, opened by "Plan as a trip" for the current
route (worth offering by itself when a route is over about 120 km).

- Controls: number of days or distance per day, a switch "count the climbing"
  (`DaySplitter`'s effort rule), and a start date (today to 7 days ahead,
  limited by the 8 day forecast; say so when days fall outside it).
- A list, one card per day: date, distance, ascent, the wind share bar,
  net wind in words, wind cost in minutes, rain, temperature range, sunrise
  and sunset, and "climbs into the wind" when there are any. Each day is
  forecast for its own date, starting at a daily start time the rider sets
  (default 08:00).
- Tapping a day opens it on the main screen as a normal route (map, sheet,
  strip), with a way back to the trip.
- "Shift the trip by a day" earlier or later, redrawing every card from the
  forecast already in memory, so a rider can dodge a bad day. Highlight which
  start date gives the best trip overall, using `DepartureScorer`'s scoring
  summed over the days.
- Split points can be dragged or nudged by hand (plus and minus 5 km buttons
  per boundary is enough).

Sunrise and sunset come from Open-Meteo's forecast API: `daily=sunrise,sunset`
(verified on https://open-meteo.com/en/docs on 2026-10-08; daily requests need
a `timezone` parameter; with `timeformat=unixtime` the values are epoch
seconds). Add that as a small separate request and class in `weather/`, with
`buildUrl` and `parse` tested. One place per day's start is enough.

## 3. Places along the way

- In the trip screen and in the bottom sheet for a single long route: a
  "Find water, food and camping" button. It is a button, not automatic,
  because the public Overpass server is a shared free service.
- Results as small markers on the map by kind, and a list ordered by distance
  along the route. Per day: the longest stretch without water and without
  food, flagged when it is over a threshold the rider can set (default 40 km).
- Tapping a place shows its name, kind, distance along and off the route, and
  opening hours text if present. "Add to export" includes it in the GPX.
- Handle HTTP 429 and 504 with a plain message and no automatic retry.
- Credit: "Places © OpenStreetMap contributors" where results are shown and in
  the settings "About the data" block (the ODbL licence requires it).
- For a long winding route the 100 point thinning in `OverpassClient` can cut
  corners by more than the corridor. Fix that properly: split a long route
  into several requests of bounded length, run one after another, or widen
  the corridor with the spacing. Test whichever you choose.

## 4. Save trips for use without signal

- A trip (route, split, elevations, places, and the forecast with the time it
  was fetched) is saved on the phone as JSON files in app storage, listed
  under "Saved trips" in the overflow menu, with delete.
- Opened without a connection it shows everything it has and says how old the
  forecast is. With a connection it refreshes the free forecast.
- The base map itself cannot be saved (Google's terms do not allow it); say
  that once in the screen rather than leaving the rider to find out.

## Checking

Unit test all pure logic. On the emulator: import a GPX you write yourself
(push it with `adb push` to `/sdcard/Download/`), plan it as a 3 day trip,
shift it a day, open a day, export it. Screenshots of each. Spend no Google
Routes calls on this milestone.
