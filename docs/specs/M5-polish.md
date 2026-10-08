# Polish for the trips screen

Read `docs/specs/COMMON.md` first. Claude ran Milestone 5 on the emulator:
GPX import, the trip screen, day cards and the overflow menu all work. Fix
these, each checked with a screenshot, before starting Milestone 4.

1. **Places were not seen working.** On a 240 km imported route, tapping "Find
   water, food and camping" showed no markers, no list and no message after 40
   seconds; only the OpenStreetMap credit appeared. Find out which it is: the
   request failing silently, an empty result shown as nothing, or results not
   drawn. Whatever the cause, the rider must always see one of: a progress
   indicator, results, "nothing found within N m of the route", or a plain
   error. Then confirm with a real lookup on a route through a town.
2. **The collapsed search line reads "Day 1 · → Finish".** The start name is
   empty for a trip day. Show "Day 1 of 3 · 79.9 km" for a trip day and the
   GPX track name (falling back to the file name) for an import.
3. **A blank gap in the sheet** where Google's cycling notice would be, on
   routes that do not show it. Collapse the space with the notice.
4. **"4% chance of rain, up to 0.0 mm an hour around 8:00 AM."** Leave the
   amount and time out when the amount rounds to 0.0. On the day cards,
   "8% rain · 0.0 mm/h" likewise becomes "8% rain".
5. **The trip screen is a wall of filled buttons.** One primary action per
   screen: keep "Update days" filled; make start date, ride time, the two
   shift buttons and the plus and minus 5 km buttons tonal or outlined; label
   the bare "40" field ("Warn when water or food is further apart than, km")
   and the days field.
6. **"Best overall start: Tue 13 Oct"** should be tappable to jump the trip to
   that date, and say why in a few words ("least headwind, dry").
7. **A day in the past of the forecast or beyond it** must say so on its card
   rather than showing numbers from the nearest forecast hour.
