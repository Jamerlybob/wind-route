# Spec: places along a trip, and a launcher icon

Read `docs/specs/COMMON.md` first. Two unrelated, bounded jobs. **Every source
file you add is new**; do not edit any existing Java file, layout, manifest,
`strings.xml`, `colors.xml` or `themes.xml` (other agents are in them).

## 1. Water, food, camping and bike shops (`poi/`, pure Java plus tests)

OpenStreetMap data through the Overpass API. Usage policy checked on
2026-10-08 (https://dev.overpass-api.de/overpass-doc/en/preface/commons.html):
the public instance expects at most about 10,000 requests and 1 GB a day per
user, answers HTTP 429 when rate-limited and 504 when overloaded, and does not
want to be the backend of a widely distributed app. That is fine for James's
personal use; say so in the class Javadoc so the question is asked again
before any Play Store release.

- Endpoint: `POST https://overpass-api.de/api/interpreter`, form body
  `data=<query>`, with `[out:json]`. `Http.java` has `get` and `postJson`; you
  may not edit it, so do the form POST with `HttpURLConnection` inside your
  own class, following the same style, timeouts included. Send a `User-Agent`
  naming the app.
- `PoiKind`: WATER (`amenity=drinking_water`), FOOD (`shop=supermarket`,
  `shop=convenience`, `amenity=cafe`, `amenity=restaurant`,
  `amenity=fast_food`), CAMPING (`tourism=camp_site`, `tourism=caravan_site`),
  BIKE_SHOP (`shop=bicycle`), TOILETS (`amenity=toilets`), SHELTER
  (`amenity=shelter`).
- `OverpassClient.buildQuery(points, kinds, corridorMeters)`: ONE request per
  search, using Overpass's `around` filter with a polyline (`around:500,lat1,
  lng1,lat2,lng2,...`). A route can have thousands of points, which would make
  a huge query, so thin it first to at most 100 points evenly spaced by
  distance. Ask for nodes and ways, with `out center;` so a way has one
  coordinate. Include a `[timeout:25]`.
- `OverpassClient.parse(json)`: a list of `Poi` (kind, name or null,
  position, and the raw `opening_hours` tag if present). An element can match
  more than one kind; pick one by a documented order.
- `PoiAlongRoute`: for each `Poi`, the distance along the route of the
  nearest route point and how far off the route it is, sorted by distance
  along. Plus a helper that answers the bikepacker's question: the longest gap
  between consecutive WATER points, and between FOOD points, including the gap
  from the start and to the finish.
- `buildQuery`, `parse` and the gap maths are testable without the network and
  must be tested. Never call the network in a test.

## 2. A launcher icon

The app still has Android Studio's default icon. Replace it with an adaptive
icon drawn as vectors, editing only `res/drawable/ic_launcher_foreground.xml`
and `res/drawable/ic_launcher_background.xml` (and adding a
`<monochrome>` layer to the two `mipmap-anydpi-v26` files for themed icons).

- Idea: a route line with a bend in it, drawn in three segments coloured like
  the app's wind verdicts (headwind `#E4572E`-ish red-orange, crosswind amber,
  tailwind teal: take the exact values from `res/values/colors.xml`), on a
  deep blue background from the `brand_*` colours. Simple enough to read at
  48 dp. No text.
- Keep everything important inside the adaptive icon safe zone: the centre
  66 dp circle of the 108 dp canvas.
- The legacy `mipmap-*/ic_launcher*.webp` files are only used below Android 8;
  minSdk is 24, so they still matter on Android 7. You cannot generate WebP
  here, so leave them and say so in the final message.
