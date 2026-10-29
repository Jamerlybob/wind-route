# Small fixes (V5, 2026-10-30)

Read `docs/specs/COMMON.md` first. Three things Claude saw on the emulator
after commit c87fdb2:

1. **The my-location button is a light grey square on the dark map.** Make
   it match the Layers button: round, the same translucent surface and
   icon tint in both themes, the same size, stacked with even spacing.
2. **The "Last:" chip reads "Auckland to Hamilton → Finish" for a GPX
   import.** For an import show the track name only ("Last: Auckland to
   Hamilton"); for a searched route keep "Last: Mission Bay → Auckland
   Domain" using the short place names (text before the first comma).
   Put the wording in a small pure-Java helper with a test.
3. **The trip map has no Start or Finish labels.** Use the same ring
   endpoints and short labels as the main map (`MarkerLayout`), and number
   the overnight tents ("1", "2").

Unit tests for 2. testDebugUnitTest lintDebug assembleDebug must pass. One
Gradle build at a time, no emulator, do not commit.
