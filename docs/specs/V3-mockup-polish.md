# Spec: match the mockup (V3)

Read `docs/specs/COMMON.md` first. Do this after V1 and V2. James made a
mockup on 2026-10-30: `docs/design/mockup-2026-10-30.png`. Open it and use
it as the visual direction for the main screen. Take the look, not the
fake parts (no satellite imagery; we use Google's styled vector map).

## Take from the mockup

1. **Dark map style by default in dark theme**: deep navy land, near-black
   water, dimmed roads and labels, so the route and the wind are what you
   see. Update the JSON style; keep light mode as it is but tone the base
   map down a little so the route stands out.
2. **The route glows**: the casing from V1, plus a soft outer glow (a wider
   line of the same colour at about 25 percent alpha under it). Start and
   finish as ring markers (a coloured ring with a dark centre) with the
   place name as a label, instead of the default red pins.
3. **Search card as a pill**: rounded fully, a search icon on the left,
   "A → B · Leave 8:00 AM" on one line, overflow on the right, translucent
   surface.
4. **Sheet header**: a wind icon, the big headline ("Wind at your back"), a
   one-sentence summary under it ("A favourable ride with tailwinds for 62%
   of the route."), and on the right the V1 wind badge drawn as a ring in
   the verdict colour with an arrow, the speed and the unit inside.
5. **Share legend as three columns**: dot and name, the percentage in bold,
   and the distance under it ("14.2 km"). Thin dividers between columns.
   Drop "Calm" when it is 0 percent.
6. **Elevation profile as a line, coloured by wind** (gradient along the
   line, light fill under it), with total up and down at the top right
   ("↑ 186 m  ↓ 192 m") and a title "Elevation & wind".
7. **Layers button**: round, translucent, icon over the word "Layers".

## The moving wind: quieter than the mockup

The mockup's streamlines are too loud to ride with. James agreed: subtle,
and only noticeable where the wind is strong.

- Particle opacity scales with speed: almost invisible below the rider's
  calm threshold, fully visible from about 30 km/h. Thin lines (1 dp),
  short trails.
- Fewer particles (about 300 on a phone screen) and slower drift.
- Under the route and the sheet they fade out, so they never compete with
  the route line.
- Default: on, at this quiet level. The heatmap stays off by default.
- Planning only. James, 2026-10-30: the moving wind is for planning; nobody
  looks at it while riding. Both wind layers are off in ride mode (no
  animation, no grid requests, saves battery) and come back when the ride
  ends.

## Checking

Screenshots of the main screen with a route in dark and light mode,
compared side by side with the mockup. Lint clean, tests pass, one Gradle
build at a time, do not commit.

## Fixes from Claude's emulator run of V1 (2026-10-30)

Do these first, then the mockup work above.

1. **The wind badge is green with "Mostly into the wind".** The badge must
   use the verdict colour (headwind red here). Check how the arrow is
   rotated too: it should point the way the wind blows relative to the
   direction of travel, same convention as the ride screen.
2. **The heatmap is all one blue** on a 33 km/h day and blue on the blue sea.
   Use a ramp with real contrast (e.g. pale teal → green → amber → red →
   violet across 0 to 60 km/h), scaled so a typical day uses most of it.
   The legend is cramped ("0 km/h 25 km/h 50 km/h" in one run): ramp on
   top, three labels spaced under their positions, rounded translucent card.
3. **"Where to ride today" card**: "changing to 34 km/h by 2:38 PM" is
   odd. Use whole hours ("by 3 PM"), and say "steady" when the change is
   under about 5 km/h. Make it look like a card from the mockup: a wind
   ring on the left, headline and one line of advice; "Weather:
   Open-Meteo" as small attribution text.
4. **Layers panel**: the time chips read "11:40 AM, 12:40 PM, 2:40 PM".
   Use "Now", then whole hours. A title "Map layers" and a short
   description under each switch.
5. **Big blank gap in the expanded sheet** between "Start ride" and the
   departure strip title (about 150 dp). Remove it.
6. **"Best: leave at 7:00 AM"** but the "best" marker on the strip sits
   over a different bar (near 8 AM the next day) and the selected bar is
   the second one. Make the label, the marker and the bar agree, with a
   test on the index maths.
7. **Saved trips** from the overflow menu showed nothing at all when there
   are none. Say "No saved trips yet" with a line on how to make one.
8. The crosswind stretches of the route look dashed/broken at this zoom.
   Draw them as solid segments like the others.
