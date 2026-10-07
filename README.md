# WindRoute

An Android app for cyclists. Type where you are starting and where you are
going, and the route is drawn on the map coloured by what the wind will be
doing to you on each stretch: against you, behind you, or across you.

Most wind apps for cycling want a route file from somewhere else. This one
works like a map app: two places in, a coloured route out.

**Status: early.** The route maths, the weather lookup and the screen are
built and unit tested. It has not yet been checked end to end on a real map.

## How it works

1. Google's Routes API returns a cycling route as an encoded polyline, plus its
   own estimate of the riding time.
2. The route is sampled about every 5 km, and Open-Meteo is asked for the
   hourly wind at all of those places in one request.
3. The route is cut into stretches of about 400 m. For each one the app works
   out which way the road points, when you will get there, and what the wind
   will be doing at that place and hour.
4. Each stretch is classed as headwind, tailwind, crosswind or calm, and the
   map line and the summary are built from that.

Because the forecast is hourly and the app knows when you reach each stretch,
a long ride is coloured by the wind you will actually meet, not the wind when
you set off. The "Now / In 1 h / In 3 h" chips recolour the route for a later
start without fetching anything again.

## The wind maths

For a rider heading towards bearing `B` and wind coming from direction `W`
(both compass degrees), the relative angle is `W - B`, wrapped into -180..180.

- headwind component = `speed x cos(angle)`: positive is against you
- crosswind component = `speed x sin(angle)`: positive is from your right

Wind within 60 degrees of dead ahead is a headwind, within 60 degrees of dead
astern a tailwind, and anything between is a crosswind. Under 5 km/h it is
calm whatever the direction. All of this is in
[`WindMath.java`](app/src/main/java/io/github/jamerlybob/windroute/wind/WindMath.java),
which has no Android code in it and is covered by plain JUnit tests.

## Layout

```
app/src/main/java/io/github/jamerlybob/windroute/
  MainActivity.java         the one screen: inputs, map, summary
  Http.java                 GET and POST on the JDK's HttpURLConnection
  wind/WindMath.java        angles, components, classification
  wind/RouteWind.java       a whole route cut into wind-labelled stretches
  route/PolylineDecoder.java  Google's encoded polyline format
  route/GeoMath.java        distance, bearing, sampling along a route
  route/RoutesClient.java   Google Routes API
  weather/OpenMeteoClient.java  Open-Meteo forecast API
app/src/test/               43 unit tests, no network and no emulator needed
```

Java and XML layouts, no Kotlin and no Compose. The only libraries beyond
AndroidX and Material are the Google Maps SDK.

## Building it

You need Android Studio and a Google Maps Platform key with **Maps SDK for
Android** and **Routes API** enabled. Put the key in `local.properties`, which
git ignores:

```
MAPS_API_KEY=your-key-here
```

Then run the app from Android Studio, or:

```
./gradlew testDebugUnitTest     # the unit tests
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
```

Weather comes from [Open-Meteo](https://open-meteo.com/), which needs no key
and is free for non-commercial use.

## Planned

- Pick any departure time and see the best hour to leave
- Compare a route with the same route ridden the other way
- Wind strength, gusts and rain shown along the line, not only direction
- Live position while riding
- Bikepacking mode: a multi-day route with each day forecast for when you ride it

## How this was built

I direct AI coding agents for the implementation. The code is commented to be
studied: I am starting a Master of Software Development and wanted a Java
codebase I can read line by line.

## Licence

MIT, see `LICENSE`.
