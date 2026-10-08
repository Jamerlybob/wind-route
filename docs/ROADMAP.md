# Roadmap

One milestone at a time, top to bottom. Each one ends with an APK on James's
phone. Tick items as they land.

Milestones 2, 3 and 5 have their logic written and tested but nothing on
screen yet, so their boxes stay unticked. `STATE.md` lists what exists.

The test for every milestone: would James open this instead of Google Maps
before a ride?

## Milestone 0: route coloured by wind (done)

- [x] Two places in, cycling route from Google Routes API
- [x] Hourly wind along the route from Open-Meteo
- [x] Route coloured by headwind, tailwind, crosswind, calm for your arrival time
- [x] Summary card, share bar, Now / +1 h / +3 h
- [x] Unit tests, CI, verified on a real map

## Milestone 1: something you open every day

- [x] Dark mode: follows the system, with a dark Google map style to match
- [x] A real colour theme and typography (replace default Material purple)
- [x] "My location" as the start (location permission, asked properly)
- [x] Place suggestions while typing, using Android's free Geocoder (James
      chose free over Places autocomplete)
- [x] Swap start and finish with one tap, and show both directions' wind side
      by side ("ride it the other way: 70% tailwind")
- [x] Settings screen: km or miles, km/h or mph or m/s or knots, your usual
      riding speed, theme (system, light, dark), calm threshold
- [x] Remember the last route and recent places
- [x] Show Google's cycling warning as required

## Milestone 2: when should I leave?

- [ ] Departure time picker for any time in the next 7 days
- [ ] "Best time to leave" strip: the next 24 hours scored by net headwind,
      tap an hour to recolour
- [ ] Rain and temperature along the route, on the same timeline
- [ ] Gust warnings on exposed stretches
- [ ] Tap any stretch for its wind, time of arrival and gradient
- [ ] Estimated time cost of the wind, not just km/h (simple power model;
      state the assumptions in the UI)

## Milestone 3: hills

- [ ] Elevation along the route (Open-Meteo elevation API is free; compare
      with Google Elevation before choosing)
- [ ] Elevation profile under the map, coloured by wind
- [ ] Climb detection: list the climbs with length, gain, average and steepest
      gradient (Garmin ClimbPro and Wahoo Summit are the reference; Wahoo uses
      a 400 m minimum)
- [ ] The nasty combination flagged: a climb into a headwind

## Milestone 4: riding with it

- [ ] Live position on the route (fused location, started and stopped with
      the screen so the battery survives)
- [ ] Turn-by-turn steps from the Routes API (`routes.legs.steps`)
- [ ] Spoken directions with Android TextToSpeech
- [ ] Spoken heads-up, a few hundred metres ahead: "climb in 400 metres, 1.2
      kilometres at 6 percent", "headwind for the next 5 kilometres",
      "tailwind from here to the finish", "strong crosswind from the left on
      the bridge"
- [ ] Settings for cues: which ones, how far ahead, how chatty
- [ ] Keeps working with the screen off (foreground service; needs care)
- [ ] Off-route detection and a re-route

## Milestone 5: bikepacking

- [ ] A trip is several days. Split a long route into days by distance, by
      climbing, or by hand
- [ ] Each day forecast for the date and hours you will ride it
- [ ] Trip overview: every day's wind, rain, climbing and daylight on one screen
- [ ] "Shift the trip by a day" to dodge a bad one
- [ ] Waypoints: water, food, camping, bike shops (OpenStreetMap via Overpass
      is free; check usage terms first)
- [ ] Import and export GPX, so routes can come from Komoot or Ride with GPS
      and go to a Garmin or Wahoo
- [ ] Save trips on the phone for use without signal

## Later, if it earns it

- Surface type along the route
- Home screen widget: tomorrow's commute wind
- Morning notification for a saved commute
- Wear OS glance

## Decisions waiting on James

1. (Settled: stay on Google, upgrade the billing account and cap the Routes
   quota. See `STATE.md`.)
2. App name. WindRoute is a working title.
3. Whether the app stays personal (non-commercial Open-Meteo is fine) or goes
   on the Play Store (needs Open-Meteo's commercial terms and a locked-down key).