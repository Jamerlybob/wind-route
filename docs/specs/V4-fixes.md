# Fixes after Claude's emulator run of V1 to V3 (2026-10-30)

Read `docs/specs/COMMON.md` first. Commit 1aa2ea9 looks good: light map,
glowing route, ring endpoints, pill search, wind ring, three-column legend,
quieter particles, the "where to ride today" card, "How far today?" ("Stop
near Pōkeno · 50.0 km · about 2 h 05 with this wind · arrive 2:06 PM") and
the trip map with day colours. Fix these.

## Trip screen (`TripActivity`)

1. **The slider shows dozens of tick dots** and no value. Use a continuous
   look (no tick marks, `app:tickVisible="false"` or the Material
   equivalent) and show the value in the label: "How far a day? 60 km".
   Same for the "How far today?" slider in the sheet.
2. **A text field labelled "Days" holds 50** in distance mode. With the
   slider, drop the free text field: "Distance per day" uses the slider,
   "Number of days" shows a small stepper (– 3 +). The trip updates as the
   slider is released; remove "Update days".
3. **Every day's verdict badge is a green up arrow**, even "Mostly into the
   wind". Use the verdict colour and the relative arrow, as `WindBadgeView`
   does in the main sheet; reuse it.
4. **Day cards repeat themselves**: "57.8 km · Mostly crosswind · dry" then
   "57.8 km · 503 m up", and the verdict is in the badge and the line. One
   headline line ("Day 1 · Fri 30 Oct"), one summary line ("57.8 km · 503 m
   up · dry"), then the details. "no net push" starts lower case: make it
   "No net push" or better "Wind roughly cancels out".
5. "Forecast fetched 0 minutes ago" → "Forecast just updated" under 2 min.
6. "Best overall start: Sun 1 Nov" should look tappable (a tonal chip with
   an arrow), as the V2 spec asked.

## Main screen

7. **The start marker's label repeats the GPX name** ("Auckland to
   Hamilton") right under the search pill, and place labels run off the
   screen edge ("Mission Bay, Auck"). Label endpoints with the short place
   name (text before the first comma); for a GPX use "Start" and "Finish".
   Keep labels inside the screen, or drop them when they would collide
   with the search pill or each other.
8. **The "How far today?" block is jammed under the departure strip's time
   axis**, with no spacing or title style. Make it a section like the
   others: title "How far today?", the slider, then the stop as a small
   card with the tent icon ("Stop near Pōkeno", second line "50 km · 2 h 05
   · arrive 2:06 PM · the wind saves 41 min"). Times as "2 h 5 min".
9. "Best: … rain possible" while the weather line says "100% chance of
   rain". Use "rain likely" from 60 percent, "rain possible" from 20.
10. The tent marker covers the route's wind arrows: skip arrows within
    about 40 dp of a marker.

## Dark mode

11. Not yet seen by Claude. Check every screen you touched has night
    colours (sheet, cards, legend, layers panel, trip cards) and that the
    night map style makes the route and particles stand out as in
    `docs/design/mockup-2026-10-30.png`.

## Checking

Unit tests for 4, 5, 7 (the short-name rule) and 9. Lint clean, one Gradle
build at a time, no emulator, do not commit.
