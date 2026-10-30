# Bugs on a long route (V7, 2026-10-30)

Read `docs/specs/COMMON.md` first. Claude ran the V6 build on the emulator
with a 115 km GPX (Auckland to Hamilton). Part 1 and the alerts settings
look right; these three are broken. James also reported "I didn't see the
wind overlay" on his phone, which matches bug 1.

1. **No moving wind over a route.** With no route the particles show (start
   screen, Auckland at city zoom). With the 115 km route fitted on screen
   (roughly lat -37.9 to -36.7, lng 174.6 to 175.6) there are no particles
   at all and no loading ring or error on the Layers button, even after a
   minute. Find out why: look at `WindLayersController` (the `covered`
   check needs the field to cover both visible corners; is the grid ever
   fetched for an area this size, or capped, or fetched for the route's
   bounds instead of the camera's?). Fix it so the layer shows at any zoom
   a rider plans at (city to about 300 km across), using a coarser grid
   when zoomed out (still at most 100 points). Add a unit test with these
   bounds for whatever decides the grid area. If the area really is too
   big, say so on the Layers button rather than showing nothing.

2. **"Places lookup failed" every time on the long route** (two tries, a
   minute apart). Claude checked the public server from the laptop: a small
   bounding-box query with a User-Agent returns 200, so the service is up.
   The single `around` query over 100 points, four kinds, node and way,
   with `[timeout:25]` and a 30 s read timeout is very likely timing out
   (504 or a socket timeout). Fix it properly:
   - Split the route into pieces of at most about 25 km and run them one
     after another (never in parallel, it is a shared free service), each
     with a short `around` line.
   - Combine kinds into one regex clause per key
     (`nwr["amenity"~"^(drinking_water|cafe|restaurant|fast_food)$"]`)
     with `out center tags;` rather than one clause per tag and type.
   - `[timeout:60]` and a 70 s read timeout.
   - Show progress per piece ("Finding places… 2 of 5"), keep results from
     pieces that worked, and tell apart "the places service is busy (try
     again in a minute)" for 429/504 from a real network error.
   - Tests for the splitting and the query text.

3. **The elevation profile is clipped at the top**: on this route the
   peaks are cut flat and the line runs into the Start ride button area.
   The y scale must fit the highest point with some headroom, with a top
   axis label as well as "0 m". And the climbs list is a wall of twelve
   lines: show the three biggest (by gain) and "Show all 12 climbs".

Unit tests as above. testDebugUnitTest lintDebug assembleDebug must pass.
One Gradle build at a time, no emulator, do not commit.
