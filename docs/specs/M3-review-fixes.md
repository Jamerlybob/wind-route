# Review fixes for the Milestone 3 to 5 logic

Read `docs/specs/COMMON.md` first. Claude reviewed the code written from
`M3-M5-logic.md` and found three real problems. Fix each one and add a test
that fails on the old code. Touch only the files named here and their tests.

## 1. `GpxParser` will fail on every file on a real phone

The unit tests run on the desktop JVM, whose XML parser is Xerces. Android's
`DocumentBuilderFactory` is a different implementation: `setFeature` throws
`ParserConfigurationException` for any feature it does not know (it knows
almost none of the Apache/SAX ones used in `secureFactory`), and
`setXIncludeAware(true or false)` throws `UnsupportedOperationException`. As
written, `parse` would turn that into "Could not read this GPX file." for
every GPX on the device.

Make each hardening step best-effort: try it, and carry on if this parser
does not support it. Keep the empty `EntityResolver`, which works on both and
is the real protection. Explain in a comment that the two platforms ship
different parsers and that this is why a passing desktop test was not enough.
You cannot run Android's parser in a unit test, so structure the code so the
"feature not supported" path is exercised by a test some other way (for
example a small method that applies one feature and swallows the two
exception types, tested with a made-up feature name).

## 2. `ElevationProfile` loses all the climbing on a gentle rise

Ascent is only counted when one sample-to-sample rise is at least 1 m. With
samples about 100 m apart, a steady 0.8% drag rises 0.8 m per sample, so ten
kilometres of it (80 m of real climbing) counts as zero. `ascentBetween` has
the same flaw.

Replace the per-sample test with the usual hysteresis method: keep a
reference height; when the smoothed height has moved more than the threshold
away from the reference, count the whole move and make the new height the
reference. Small wiggles around a level never reach the threshold, and a slow
steady rise is counted in full. Pick the threshold as a named constant (2 to 3
m is typical for 90 m terrain data) and explain the method in a comment a
learner can follow. `ascentBetween` should give answers consistent with it.

Tests: a 10 km, 0.8% rise counts close to 80 m; a flat road with plus and
minus 0.5 m noise counts zero.

## 3. `ClimbDetector` throws away a real climb with a gentle approach

A climb starts at the first sample that rises at all and its average is taken
over everything from there. Two kilometres at 1% followed by one kilometre at
6% is 80 m over 3 km, 2.7%, so the whole thing is rejected and the rider gets
no warning of a 1 km, 6% climb.

Change the rule so the climb is the steep part. One clear way: a sample
counts as "climbing" only when its gradient is at least a named threshold
(about 2%); a run of climbing samples may bridge a gap of up to
`MAX_INTERRUPTION_METERS` of non-climbing road, but only if that gap does not
lose more than a named number of metres of height; then apply the existing
length, gain and average tests to each run. If you find a cleaner rule, use
it, but it must pass these tests:

- 2 km at 1% then 1 km at 6%: one climb, about 1 km long, about 6%.
- 1 km at 5%, 150 m flat, 1 km at 5%: one climb of about 2.15 km.
- 1 km at 5%, 1 km descending at 5%, 1 km at 5%: two climbs.
- 10 km at 1%: no climb.
