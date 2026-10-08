# What other apps do

Notes from looking at the field in October 2026. Sources at the bottom.

## Wind-specific apps

| App | What it does well | Where it falls short for James |
|---|---|---|
| myWindsock | Colour-coded wind along a route (red head, purple cross, light blue tail), a rolling forecast that follows your position, Strava segment analysis, alerts when conditions suit a segment | Needs a route file or Strava link. Paid subscription. Built for racers chasing segments |
| Epic Ride Weather | Wind arrows along the route sized by strength, temperature and rain per mile, can reverse the route or shift the start time, dark mode | Needs a route imported from Strava, Komoot, Ride with GPS |
| Headwind | Free, visual difficulty rating for Strava rides and the week ahead | Web only, Strava only |
| PedalCast, BikeWind | Segment-by-segment wind, compare directions | iOS |

**The gap WindRoute fills:** none of them is "type two places". They all assume
you already planned the route somewhere else.

**Ideas worth taking:** arrows sized by wind strength (Epic Ride Weather),
reverse-the-route comparison, forecast that follows your position in time
(already done), alerts for good conditions.

## Route planners

- **Komoot:** the strongest pure planner, routing aware of surface and sport.
  Offline maps and turn-by-turn voice are paid.
- **Ride with GPS:** the most control. Cue sheets you can edit, multi-day
  tours as linked routes, export to Garmin and Wahoo. The reference for
  bikepacking planning.
- **Strava:** heatmap-based routes and the social layer.

WindRoute should not try to out-plan these. It should import their routes
(GPX) and be the best answer to "what will the weather do to this ride".

## Climb features on bike computers

- **Garmin ClimbPro:** detects significant climbs on a course, shows remaining
  distance, ascent and gradient as you approach, with configurable alerts at
  the start of a climb or a set distance before it.
- **Wahoo Summit:** same idea, works without a planned route by scanning the
  road ahead, colours the profile in 10 m sections by steepness, 400 m minimum
  climb length.

Nobody found so far combines these with wind: "this climb is into a headwind"
is the cue WindRoute can own.

## Bikepacking (second pass, 2026-10-08)

No rider survey turned up; this is what the tools offer and what guides say.

- **Days:** Komoot's paid multi-day planner splits a route into as many days
  as you like with a slider, and can reverse it. That slider is the model for
  WindRoute's trip screen.
- **Water and resupply:** Komoot shows public water points, bike shops,
  campsites and toilets. Guides say to plan the trip around water and
  resupply first, and warn that a water point on the map may be dry.
  WindRoute should show the longest gap without water or food, and not
  promise the tap works.
- **Sleeping:** campsites and hostels at the end of each stage.
- **Offline:** every serious app saves the route for use without signal.

## Voice cues (second pass, 2026-10-08)

Nothing published was found on how Komoot or Google Maps word or time their
spoken instructions. Another app's forum mentions fixed distances before a
turn (300 m, 1 km, 2 km, 5 km). WindRoute's cue rules in
`docs/specs/M4-riding.md` are therefore our own and need trying on a ride.

## Not yet researched

- App store reviews of myWindsock and Epic Ride Weather for what annoys people.
- Ride with GPS's own multi-day and resupply tools.

## Sources

- https://www.cyclingweekly.com/news/latest-news/best-cycling-apps-143222
- https://mywindsock.com/page/help/features/
- https://road.cc/content/tech-news/221571-cycling-app-week-mywindsock
- https://www.bikeradar.com/news/mywindsock-takes-strava-nerdiness-to-the-next-level/
- https://velo.outsideonline.com/gear/tech-wearables/epic-ride-weather-review-one-of-the-handiest-cycling-apps-just-got-better/
- https://www.bikeradar.com/advice/buyers-guides/guide-to-using-komoot
- https://bikepackingreviews.com/posts/best-bikepacking-navigation-apps
- https://cyclinglabs.net/cycling-apps-compared-komoot-vs-strava-vs-ridewithgps
- https://the5krunner.com/garmin-features/navigation/climbpro/
- https://www.bikeradar.com/news/wahoo-summit-freeride/
- https://developers.google.com/maps/documentation/routes/reference/rest/v2/RouteTravelMode
- https://open-meteo.com/en/docs- https://www.singletracks.com/mtb-news/bikepack-around-the-globe-with-new-komoot-premium-multi-day-routing-features/
- https://www.adventure-journal.com/2023/04/so-you-wanna-plan-a-bikepacking-route-we-have-advice/
- https://forum.kurviger.com/t/voice-guidance-after-instruction-points/1912
- https://developer.android.com/about/versions/14/changes/fgs-types-required
- https://dev.overpass-api.de/overpass-doc/en/preface/commons.html
- https://open-meteo.com/en/docs/elevation-api
